//! O Engine native core. Rust owns the LWJGL native-loader hook.
//! Android UI and lifecycle remain in Kotlin.

use jni::objects::{JClass, JFloatArray, JIntArray, JLongArray, JObject, JObjectArray, JString, JValue};
use jni::sys::{jboolean, jfloatArray, jint, jintArray, jlong, jlongArray, jobject, jstring, JNIEnv as RawJNIEnv};
use jni::{JNIEnv, NativeMethod};
use libc::{c_char, c_void};
use std::collections::VecDeque;
use std::ffi::{CStr, CString};
use std::ptr;
use std::sync::{atomic::{AtomicUsize, Ordering}, Mutex, OnceLock, RwLock};
use std::thread::ThreadId;

mod elf_hinter;

const ABI_VERSION: u32 = 1;

#[derive(Clone)]
struct HookConfig {
    selected_alias: CString,
    renderer_handle: usize,
}

static HOOK_CONFIG: OnceLock<RwLock<Option<HookConfig>>> = OnceLock::new();
static JVM_LIBRARY: OnceLock<Mutex<Option<(CString, usize)>>> = OnceLock::new();
static JVM_INSTANCE: OnceLock<Mutex<Option<usize>>> = OnceLock::new();
static NATIVE_LOAD_HOOK: OnceLock<Mutex<Option<(i32, usize, usize)>>> = OnceLock::new();
static HOTSPOT_ENV: OnceLock<Mutex<Option<usize>>> = OnceLock::new();
static HOTSPOT_CREATOR_THREAD: OnceLock<Mutex<Option<ThreadId>>> = OnceLock::new();
static RENDERER_LIBRARIES: OnceLock<Mutex<Vec<(CString, usize)>>> = OnceLock::new();
static RENDERER_LOAD_ERROR: OnceLock<Mutex<String>> = OnceLock::new();
static RENDERER_HOOK_ERROR: OnceLock<Mutex<String>> = OnceLock::new();
static ANDROID_LIBRARY: OnceLock<usize> = OnceLock::new();

const INPUT_QUEUE_CAPACITY: usize = 512;
const INPUT_MAX_TRACKED_CONTROLS: usize = 1024;

/// Stable C ABI consumed by the Android GLFW event-poll backend.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct OEngineNativeInputEvent {
    pub device: i32,
    pub action: i32,
    pub code: i32,
    pub x: f32,
    pub y: f32,
    pub value: f32,
    pub timestamp_nanos: i64,
    pub modifiers: i32,
    pub codepoint: u32,
    pub scan_code: i32,
    pub reserved: i32,
}

#[derive(Clone, Default)]
struct OEngineNativeInputQueue {
    events: VecDeque<OEngineNativeInputEvent>,
    pressed: Vec<OEngineNativeInputEvent>,
    reset_required: bool,
    dropped: u64,
}

impl OEngineNativeInputQueue {
    fn same_control(a: &OEngineNativeInputEvent, b: &OEngineNativeInputEvent) -> bool {
        a.device == b.device && a.code == b.code
    }

    fn offer_event(&mut self, event: OEngineNativeInputEvent) -> bool {
        let disposable = event.action == 3 || event.action == 4; // MOVE / SCROLL
        if event.action == 3 {
            if let Some(previous) = self.events.iter_mut().rev().find(|queued|
                queued.action == 3 && Self::same_control(queued, &event)) {
                *previous = event;
                self.dropped += 1;
                return true;
            }
        }
        if self.events.len() < INPUT_QUEUE_CAPACITY {
            self.events.push_back(event);
            return true;
        }
        if let Some(index) = self.events.iter().position(|queued| queued.action == 3 || queued.action == 4) {
            self.events.remove(index);
            self.dropped += 1;
            self.events.push_back(event);
            return true;
        }
        self.dropped += 1;
        if !disposable {
            self.reset_required = true;
        }
        false
    }

    fn enqueue(&mut self, event: OEngineNativeInputEvent) -> bool {
        let control = self.pressed.iter().position(|pressed| Self::same_control(pressed, &event));
        match event.action {
            0 => { // DOWN
                if control.is_some() { return true; }
                if self.pressed.len() >= INPUT_MAX_TRACKED_CONTROLS {
                    self.reset_required = true;
                    return false;
                }
                self.pressed.push(event);
            }
            1 => { // UP
                let Some(index) = control else { return true; };
                let previous = self.pressed.remove(index);
                if !self.offer_event(event) {
                    self.pressed.push(previous);
                    return false;
                }
                return true;
            }
            2 => { // REPEAT
                if control.is_none() { return true; }
            }
            _ => {}
        }
        self.offer_event(event)
    }

    fn enqueue_batch(&mut self, events: &[OEngineNativeInputEvent]) -> bool {
        let mut staged = self.clone();
        for event in events.iter().copied() {
            if !staged.enqueue(event) {
                self.reset_required = true;
                self.dropped = self.dropped.saturating_add(events.len() as u64);
                return false;
            }
        }
        *self = staged;
        true
    }

    fn drain(&mut self, output: &mut [OEngineNativeInputEvent]) -> usize {
        if self.reset_required {
            if output.len() < self.pressed.len() {
                return 0;
            }
            self.events.clear();
            let count = self.pressed.len();
            for (index, pressed) in self.pressed.iter().rev().enumerate() {
                output[index] = OEngineNativeInputEvent {
                    device: pressed.device,
                    action: 1,
                    code: pressed.code,
                    x: 0.0,
                    y: 0.0,
                    value: 0.0,
                    timestamp_nanos: 0,
                    modifiers: pressed.modifiers,
                    codepoint: 0,
                    scan_code: pressed.scan_code,
                    reserved: 0,
                };
            }
            self.pressed.clear();
            self.reset_required = false;
            return count;
        }
        let count = output.len().min(self.events.len());
        for slot in output.iter_mut().take(count) {
            if let Some(event) = self.events.pop_front() {
                *slot = event;
            }
        }
        count
    }
}

static NATIVE_INPUT_QUEUE: OnceLock<Mutex<OEngineNativeInputQueue>> = OnceLock::new();

fn native_input_queue() -> &'static Mutex<OEngineNativeInputQueue> {
    NATIVE_INPUT_QUEUE.get_or_init(|| Mutex::new(OEngineNativeInputQueue::default()))
}

/// Native GLFW callback table. Function pointers must belong to the active HotSpot
/// GLFW implementation and are invoked only by the thread calling oengine_input_dispatch.
#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct OEngineNativeInputCallbacks {
    pub key: Option<unsafe extern "C" fn(*mut c_void, i32, i32, i32, i32)>,
    pub character: Option<unsafe extern "C" fn(*mut c_void, u32)>,
    pub character_mods: Option<unsafe extern "C" fn(*mut c_void, u32, i32)>,
    pub cursor_position: Option<unsafe extern "C" fn(*mut c_void, f64, f64)>,
    pub mouse_button: Option<unsafe extern "C" fn(*mut c_void, i32, i32, i32)>,
    pub scroll: Option<unsafe extern "C" fn(*mut c_void, f64, f64)>,
    pub platform_event: Option<unsafe extern "C" fn(
        *mut c_void, i32, i32, i32, i32, f32, f32, f32, i64, i32, u32
    )>,
}

fn glfw_action_from_oengine(action: i32) -> Option<i32> {
    match action {
        0 => Some(1), // O Engine DOWN -> GLFW_PRESS
        1 => Some(0), // O Engine UP -> GLFW_RELEASE
        2 => Some(2), // O Engine REPEAT -> GLFW_REPEAT
        _ => None,
    }
}

unsafe fn dispatch_native_input_event(
    window: *mut c_void,
    callbacks: &OEngineNativeInputCallbacks,
    event: OEngineNativeInputEvent,
) {
    match event.device {
        1 => { // KEYBOARD: translate O Engine transition values to GLFW action values.
            if let Some(action) = glfw_action_from_oengine(event.action) {
                if let Some(callback) = callbacks.key {
                    callback(window, event.code, event.scan_code, action, event.modifiers);
                }
                if action != 0 && event.codepoint != 0 {
                    if let Some(callback) = callbacks.character {
                        callback(window, event.codepoint);
                    }
                    if let Some(callback) = callbacks.character_mods {
                        callback(window, event.codepoint, event.modifiers);
                    }
                }
            }
        }
        2 => match event.action {
            0 | 1 => if let Some(callback) = callbacks.mouse_button {
                callback(window, event.code, glfw_action_from_oengine(event.action).unwrap(), event.modifiers);
            },
            3 => if let Some(callback) = callbacks.cursor_position {
                callback(window, event.x as f64, event.y as f64);
            },
            4 => if let Some(callback) = callbacks.scroll {
                callback(window, event.x as f64, event.y as f64);
            },
            _ => {}
        },
        // Touch and gamepad remain backend-specific. Preserve their complete payload rather
        // than incorrectly pretending Android pointer IDs are GLFW mouse buttons.
        0 | 3 => if let Some(callback) = callbacks.platform_event {
            callback(window, event.device, event.action, event.code, event.scan_code,
                event.x, event.y, event.value, event.timestamp_nanos, event.modifiers, event.codepoint);
        },
        _ => {}
    }
}

/// Drains and dispatches queued events to GLFW callbacks on the caller's game/event thread.
/// The queue lock is released before callbacks run, so callback re-entry cannot deadlock ingress.
#[no_mangle]
pub unsafe extern "C" fn oengine_input_dispatch(
    window: *mut c_void,
    callbacks: *const OEngineNativeInputCallbacks,
    capacity: usize,
) -> usize {
    if callbacks.is_null() || capacity == 0 || capacity > 4096 {
        return 0;
    }
    let callbacks = *callbacks;
    let mut events = vec![OEngineNativeInputEvent::default(); capacity];
    let count = {
        let Ok(mut queue) = native_input_queue().lock() else { return 0; };
        queue.drain(&mut events)
    };
    for event in events.into_iter().take(count) {
        dispatch_native_input_event(window, &callbacks, event);
    }
    count
}

/// Drains pending platform events on the actual GLFW/game thread. On transition overflow,
/// the caller must provide enough room for every tracked release (at most 1024).
#[no_mangle]
pub unsafe extern "C" fn oengine_input_drain(
    output: *mut OEngineNativeInputEvent,
    capacity: usize,
) -> usize {
    if output.is_null() || capacity == 0 || capacity > 4096 {
        return 0;
    }
    let Ok(mut queue) = native_input_queue().lock() else { return 0; };
    queue.drain(std::slice::from_raw_parts_mut(output, capacity))
}

#[no_mangle]
pub extern "C" fn oengine_input_reset_pending() -> bool {
    native_input_queue().lock().map(|queue| queue.reset_required).unwrap_or(true)
}

#[no_mangle]
pub extern "C" fn oengine_input_clear_session() {
    if let Ok(mut queue) = native_input_queue().lock() {
        queue.events.clear();
        queue.pressed.clear();
        queue.reset_required = false;
    }
}

/// Notify the Android GLFW backend so glfwWaitEvents cannot sleep through newly queued input.
fn wake_android_glfw_event_loop() {
    unsafe {
        let handle = libc::dlopen(
            b"libglfw.so\0".as_ptr() as *const c_char,
            libc::RTLD_NOW | libc::RTLD_LOCAL,
        );
        if handle.is_null() { return; }
        let symbol = libc::dlsym(handle, b"oengine_wake_input\0".as_ptr() as *const c_char);
        if !symbol.is_null() {
            let wake: unsafe extern "C" fn() = std::mem::transmute(symbol);
            wake();
        }
        libc::dlclose(handle);
    }
}

/// Copies a bounded Android input batch into the native queue shared with the GLFW backend.
/// The native queue is independent of HotSpot's JNI environment; GLFW drains it through C ABI.
#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeEnqueueInputBatch(
    raw_env: *mut RawJNIEnv,
    _receiver: jobject,
    devices_raw: jintArray,
    actions_raw: jintArray,
    codes_raw: jintArray,
    scan_codes_raw: jintArray,
    xs_raw: jfloatArray,
    ys_raw: jfloatArray,
    values_raw: jfloatArray,
    times_raw: jlongArray,
    modifiers_raw: jintArray,
    codepoints_raw: jintArray,
) -> jint {
    if raw_env.is_null() || devices_raw.is_null() || actions_raw.is_null() ||
        codes_raw.is_null() || xs_raw.is_null() || ys_raw.is_null() || values_raw.is_null() ||
        times_raw.is_null() || modifiers_raw.is_null() || codepoints_raw.is_null() || scan_codes_raw.is_null() {
        return -1;
    }
    let mut env = match JNIEnv::from_raw(raw_env) { Ok(value) => value, Err(_) => return -1 };
    let devices = JIntArray::from_raw(devices_raw);
    let actions = JIntArray::from_raw(actions_raw);
    let codes = JIntArray::from_raw(codes_raw);
    let xs = JFloatArray::from_raw(xs_raw);
    let ys = JFloatArray::from_raw(ys_raw);
    let values = JFloatArray::from_raw(values_raw);
    let times = JLongArray::from_raw(times_raw);
    let modifiers = JIntArray::from_raw(modifiers_raw);
    let codepoints = JIntArray::from_raw(codepoints_raw);
    let scan_codes = JIntArray::from_raw(scan_codes_raw);
    let count = match env.get_array_length(&devices) { Ok(value) if (1..=128).contains(&value) => value as usize, _ => return -1 };
    let arrays_match = [
        env.get_array_length(&actions), env.get_array_length(&codes),
        env.get_array_length(&xs), env.get_array_length(&ys), env.get_array_length(&values),
        env.get_array_length(&times), env.get_array_length(&modifiers), env.get_array_length(&codepoints),
        env.get_array_length(&scan_codes),
    ].into_iter().all(|length| length.map(|value| value as usize == count).unwrap_or(false));
    if !arrays_match { return -1; }
    let mut d = vec![0; count];
    let mut a = vec![0; count];
    let mut c = vec![0; count];
    let mut x = vec![0.0; count];
    let mut y = vec![0.0; count];
    let mut v = vec![0.0; count];
    let mut t = vec![0; count];
    let mut m = vec![0; count];
    let mut cp = vec![0; count];
    let mut sc = vec![0; count];
    if env.get_int_array_region(&devices, 0, &mut d).is_err() ||
        env.get_int_array_region(&actions, 0, &mut a).is_err() ||
        env.get_int_array_region(&codes, 0, &mut c).is_err() ||
        env.get_float_array_region(&xs, 0, &mut x).is_err() ||
        env.get_float_array_region(&ys, 0, &mut y).is_err() ||
        env.get_float_array_region(&values, 0, &mut v).is_err() ||
        env.get_long_array_region(&times, 0, &mut t).is_err() ||
        env.get_int_array_region(&modifiers, 0, &mut m).is_err() ||
        env.get_int_array_region(&codepoints, 0, &mut cp).is_err() ||
        env.get_int_array_region(&scan_codes, 0, &mut sc).is_err() {
        return -1;
    }
    let mut parsed = Vec::with_capacity(count);
    for index in 0..count {
        if !(0..=3).contains(&d[index]) || !(0..=4).contains(&a[index]) ||
            !x[index].is_finite() || !y[index].is_finite() || !v[index].is_finite() ||
            m[index] < 0 || !(0..=0x10FFFF).contains(&cp[index]) ||
            (0xD800..=0xDFFF).contains(&cp[index]) {
            return -1;
        }
        parsed.push(OEngineNativeInputEvent {
            device: d[index], action: a[index], code: c[index],
            x: x[index], y: y[index], value: v[index],
            timestamp_nanos: t[index], modifiers: m[index], codepoint: cp[index] as u32,
            scan_code: sc[index], reserved: 0,
        });
    }
    let Ok(mut queue) = native_input_queue().lock() else { return -1; };
    if !queue.enqueue_batch(&parsed) { return -1; }
    let accepted = parsed.len() as jint;
    drop(queue);
    wake_android_glfw_event_loop();
    accepted
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeInputResetPending(
    _raw_env: *mut RawJNIEnv,
    _receiver: jobject,
) -> jboolean {
    if oengine_input_reset_pending() { 1 } else { 0 }
}


type OEngineNativeWindowFromSurface = unsafe extern "system" fn(*mut RawJNIEnv, jobject) -> *mut c_void;
type OEngineNativeWindowSetBuffersGeometry = unsafe extern "C" fn(*mut c_void, i32, i32, i32) -> i32;
type OEngineNativeWindowRelease = unsafe extern "C" fn(*mut c_void);

fn android_library() -> *mut c_void {
    *ANDROID_LIBRARY.get_or_init(|| {
        unsafe { libc::dlopen(b"libandroid.so\0".as_ptr() as *const c_char, libc::RTLD_NOW | libc::RTLD_LOCAL) as usize }
    }) as *mut c_void
}

/// Acquires an ANativeWindow reference from Android's Surface. The returned pointer
/// is owned by the caller and must be passed to nativeReleaseWindow exactly once.
#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeAcquireWindow(
    raw_env: *mut RawJNIEnv,
    _receiver: jobject,
    surface: jobject,
    width: jint,
    height: jint,
) -> jlong {
    if raw_env.is_null() || surface.is_null() || width <= 0 || height <= 0 {
        return 0;
    }
    let library = android_library();
    if library.is_null() {
        return 0;
    }
    let from_surface = libc::dlsym(library, b"ANativeWindow_fromSurface\0".as_ptr() as *const c_char);
    let set_geometry = libc::dlsym(library, b"ANativeWindow_setBuffersGeometry\0".as_ptr() as *const c_char);
    let release = libc::dlsym(library, b"ANativeWindow_release\0".as_ptr() as *const c_char);
    if from_surface.is_null() || set_geometry.is_null() || release.is_null() {
        return 0;
    }
    let from_surface: OEngineNativeWindowFromSurface = std::mem::transmute(from_surface);
    let set_geometry: OEngineNativeWindowSetBuffersGeometry = std::mem::transmute(set_geometry);
    let release: OEngineNativeWindowRelease = std::mem::transmute(release);
    let window = from_surface(raw_env, surface);
    if window.is_null() {
        return 0;
    }
    // Format 0 preserves the Surface's configured native buffer format.
    if set_geometry(window, width, height, 0) != 0 {
        release(window);
        return 0;
    }
    window as jlong
}

/// Releases the exact ANativeWindow reference returned by nativeAcquireWindow.
#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeReleaseWindow(
    _raw_env: *mut RawJNIEnv,
    _receiver: jobject,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let library = android_library();
    if library.is_null() {
        return;
    }
    let release = libc::dlsym(library, b"ANativeWindow_release\0".as_ptr() as *const c_char);
    if !release.is_null() {
        let release: OEngineNativeWindowRelease = std::mem::transmute(release);
        release(handle as *mut c_void);
    }
}

fn hook_config() -> &'static RwLock<Option<HookConfig>> {
    HOOK_CONFIG.get_or_init(|| RwLock::new(None))
}

fn is_valid_lwjgl_class_name(value: &str) -> bool {
    value.len() <= 512
        && value.split('.').all(|part| {
            !part.is_empty() && part.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
        })
}

fn is_valid_renderer_alias(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 128
        && value.starts_with("lib")
        && value.ends_with(".so")
        && !value.contains('/')
        && !value.as_bytes().contains(&0)
}

#[no_mangle]
pub extern "C" fn oengine_native_abi_version() -> u32 {
    ABI_VERSION
}

/// JNI diagnostic equivalent to the temporary C++ probe. It retains one validated
/// Android OpenJDK libjvm handle for the process lifetime but never creates a VM.
#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeProbeJvmLibrary(
    raw_env: *mut RawJNIEnv,
    _receiver: jobject,
    library_path: jstring,
) -> jstring {
    if raw_env.is_null() || library_path.is_null() {
        return ptr::null_mut();
    }
    let mut env = match JNIEnv::from_raw(raw_env) {
        Ok(value) => value,
        Err(_) => return ptr::null_mut(),
    };
    let java_path = JString::from_raw(library_path);
    let path: String = match env.get_string(&java_path) {
        Ok(value) => value.into(),
        Err(_) => return ptr::null_mut(),
    };
    let response = |status: &str, detail: &str| {
        env.new_string(format!("{status}:{detail}"))
            .map(|value| value.into_raw())
            .unwrap_or(ptr::null_mut())
    };
    if !path.starts_with('/') {
        return response("invalid_path", "libjvm.so path must be absolute");
    }
    let requested = match CString::new(path) {
        Ok(value) => value,
        Err(_) => return response("invalid_path", "libjvm.so path contains a NUL byte"),
    };
    let state = JVM_LIBRARY.get_or_init(|| Mutex::new(None));
    let mut guard = match state.lock() {
        Ok(value) => value,
        Err(_) => return response("lock_error", "JVM library state lock is poisoned"),
    };
    if let Some((loaded_path, _)) = guard.as_ref() {
        if loaded_path != &requested {
            return response("runtime_already_loaded", "A different libjvm.so is already retained in this process");
        }
    } else {
        let handle = libc::dlopen(requested.as_ptr(), libc::RTLD_NOW | libc::RTLD_GLOBAL);
        if handle.is_null() {
            let error = libc::dlerror();
            let detail = if error.is_null() {
                "dlopen failed without detail".to_owned()
            } else {
                CStr::from_ptr(error).to_string_lossy().into_owned()
            };
            return response("load_error", &detail);
        }
        libc::dlerror();
        let create_vm = libc::dlsym(handle, b"JNI_CreateJavaVM\0".as_ptr() as *const c_char);
        let create_error = libc::dlerror();
        let get_vms = libc::dlsym(handle, b"JNI_GetCreatedJavaVMs\0".as_ptr() as *const c_char);
        let get_error = libc::dlerror();
        if create_vm.is_null() || !create_error.is_null() || get_vms.is_null() || !get_error.is_null() {
            let detail = if create_vm.is_null() || !create_error.is_null() {
                "JNI_CreateJavaVM is not exported"
            } else {
                "JNI_GetCreatedJavaVMs is not exported"
            };
            libc::dlclose(handle);
            return response("missing_symbol", detail);
        }
        *guard = Some((requested, handle as usize));
    }
    response("symbols_ok", "JNI Invocation API exports validated; libjvm remains mapped for this process")
}


/// JNI Invocation API ABI declarations. These mirror jni.h and are intentionally local
/// so the bootstrap can resolve JNI_CreateJavaVM from the selected Android JRE at runtime.
#[repr(C)]
struct OEngineJavaVmOption {
    option_string: *mut c_char,
    extra_info: *mut c_void,
}

#[repr(C)]
struct OEngineJavaVmInitArgs {
    version: i32,
    n_options: i32,
    options: *mut OEngineJavaVmOption,
    ignore_unrecognized: u8,
}

type OEngineCreateJavaVm = unsafe extern "system" fn(
    *mut *mut c_void,
    *mut *mut c_void,
    *mut c_void,
) -> i32;

fn java_vm_state() -> &'static Mutex<Option<usize>> {
    JVM_INSTANCE.get_or_init(|| Mutex::new(None))
}

/// Creates one HotSpot VM in this isolated Android process and verifies core Java classes.
/// The calling HandlerThread remains attached for the lifetime of the service.
#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeCreateJvm(
    raw_env: *mut RawJNIEnv,
    _receiver: jobject,
    vm_options: jni::sys::jobjectArray,
    java_major: jint,
) -> jstring {
    if raw_env.is_null() || vm_options.is_null() {
        return ptr::null_mut();
    }
    let mut env = match JNIEnv::from_raw(raw_env) {
        Ok(value) => value,
        Err(_) => return ptr::null_mut(),
    };
    macro_rules! response {
        ($status:expr, $detail:expr) => {
            env.new_string(format!("{}:{}", $status, $detail))
                .map(|value| value.into_raw())
                .unwrap_or(ptr::null_mut())
        };
    }

    if !matches!(java_major, 8 | 17 | 21) {
        return response!("unsupported_java_major", &format!("Java {java_major} has no verified native-load hook profile"));
    }

    let library_state = JVM_LIBRARY.get_or_init(|| Mutex::new(None));
    let library_handle = match library_state.lock() {
        Ok(guard) => match guard.as_ref() {
            Some((_, handle)) => *handle,
            None => return response!("library_not_loaded", "Probe and retain libjvm.so before creating the VM"),
        },
        Err(_) => return response!("lock_error", "JVM library state lock is poisoned"),
    };

    let instance_state = java_vm_state();
    let mut instance_guard = match instance_state.lock() {
        Ok(guard) => guard,
        Err(_) => return response!("lock_error", "JVM instance state lock is poisoned"),
    };
    if instance_guard.is_some() {
        return response!("already_created", "A HotSpot VM already exists in this game process");
    }

    let options_array = JObjectArray::from_raw(vm_options);
    let option_count = match env.get_array_length(&options_array) {
        Ok(value) => value,
        Err(error) => return response!("invalid_options", &format!("Cannot read JVM option array: {error}")),
    };
    if option_count > 512 {
        return response!("invalid_options", "JVM option count exceeds the 512-option safety limit");
    }

    let mut option_strings: Vec<CString> = Vec::with_capacity(option_count as usize);
    for index in 0..option_count {
        let option_object = match env.get_object_array_element(&options_array, index) {
            Ok(value) => value,
            Err(error) => return response!("invalid_options", &format!("Cannot read JVM option {index}: {error}")),
        };
        let option_string = JString::from(option_object);
        let option: String = match env.get_string(&option_string) {
            Ok(value) => value.into(),
            Err(error) => return response!("invalid_options", &format!("Cannot decode JVM option {index}: {error}")),
        };
        if option.is_empty() || option.len() > 16_384 {
            return response!("invalid_options", &format!("JVM option {index} is empty or too long"));
        }
        let encoded = match CString::new(option) {
            Ok(value) => value,
            Err(_) => return response!("invalid_options", &format!("JVM option {index} contains a NUL byte")),
        };
        option_strings.push(encoded);
    }

    let mut native_options: Vec<OEngineJavaVmOption> = option_strings
        .iter_mut()
        .map(|option| OEngineJavaVmOption {
            option_string: option.as_ptr() as *mut c_char,
            extra_info: ptr::null_mut(),
        })
        .collect();
    let mut init_args = OEngineJavaVmInitArgs {
        version: 0x0001_0008, // JNI_VERSION_1_8; supported by the selected OpenJDK builds.
        n_options: native_options.len() as i32,
        options: native_options.as_mut_ptr(),
        ignore_unrecognized: 0,
    };

    let create_symbol = libc::dlsym(library_handle as *mut c_void, b"JNI_CreateJavaVM\0".as_ptr() as *const c_char);
    if create_symbol.is_null() {
        return response!("missing_symbol", "JNI_CreateJavaVM disappeared after the library probe");
    }
    let create_vm: OEngineCreateJavaVm = std::mem::transmute(create_symbol);
    let mut vm: *mut c_void = ptr::null_mut();
    let mut vm_env: *mut c_void = ptr::null_mut();
    let result = create_vm(&mut vm, &mut vm_env, &mut init_args as *mut _ as *mut c_void);
    if result != 0 {
        return response!("create_failed", &format!("JNI_CreateJavaVM returned {result}"));
    }
    if vm.is_null() || vm_env.is_null() {
        return response!("create_failed", "JNI_CreateJavaVM returned success without a VM or JNIEnv");
    }

    *instance_guard = Some(vm as usize);
    *HOTSPOT_ENV.get_or_init(|| Mutex::new(None)).lock().map_err(|_| ()).unwrap() = Some(vm_env as usize);
    *HOTSPOT_CREATOR_THREAD.get_or_init(|| Mutex::new(None)).lock().map_err(|_| ()).unwrap() = Some(std::thread::current().id());
    let mut hot_spot_env = match JNIEnv::from_raw(&mut vm_env as *mut *mut c_void as *mut RawJNIEnv) {
        Ok(value) => value,
        Err(error) => return response!("created_env_error", &format!("VM created but JNIEnv could not be wrapped: {error}")),
    };
    if let Err(error) = install_native_library_hook(&mut hot_spot_env, java_major) {
        return response!("vm_created_hooks_missing", &format!("HotSpot VM exists, but Java {java_major} native-library hook installation failed: {error}"));
    }

    if let Err(error) = hot_spot_env.find_class("java/lang/String") {
        if hot_spot_env.exception_check().unwrap_or(false) {
            let _ = hot_spot_env.exception_clear();
        }
        return response!("core_class_error", &format!("VM started but java.lang.String lookup failed: {error}"));
    }

    // A tiny Java 8-compatible class file with public static void main(String[]) { }.
    // Defining and invoking this class verifies that the VM can load and execute Java code.
    const SMOKE_CLASS: [u8; 136] = [
        202,254,186,190,0,0,0,52,0,8,1,0,18,79,69,110,103,105,110,101,86,109,83,109,111,107,101,84,101,115,116,
        7,0,1,1,0,16,106,97,118,97,47,108,97,110,103,47,79,98,106,101,99,116,7,0,3,1,0,4,109,97,105,110,
        1,0,22,40,91,76,106,97,118,97,47,108,97,110,103,47,83,116,114,105,110,103,59,41,86,1,0,4,67,111,100,
        101,0,33,0,2,0,4,0,0,0,0,0,1,0,9,0,5,0,6,0,1,0,7,0,0,0,13,0,0,0,1,0,0,0,1,
        177,0,0,0,0,0,0
    ];
    let loader_value = match hot_spot_env.call_static_method(
        "java/lang/ClassLoader",
        "getSystemClassLoader",
        "()Ljava/lang/ClassLoader;",
        &[],
    ) {
        Ok(value) => value,
        Err(error) => {
            if hot_spot_env.exception_check().unwrap_or(false) { let _ = hot_spot_env.exception_clear(); }
            return response!("java_main_loader_error", &format!("Cannot get system class loader: {error}"));
        }
    };
    let loader = match loader_value.l() {
        Ok(value) => value,
        Err(error) => return response!("java_main_loader_error", &format!("Invalid system class loader result: {error}")),
    };
    let smoke_class = match hot_spot_env.define_class("OEngineVmSmokeTest", &loader, &SMOKE_CLASS) {
        Ok(value) => value,
        Err(error) => {
            if hot_spot_env.exception_check().unwrap_or(false) { let _ = hot_spot_env.exception_clear(); }
            return response!("java_main_define_error", &format!("Cannot define smoke-test class: {error}"));
        }
    };
    let null_args = JObject::null();
    match hot_spot_env.call_static_method(
        &smoke_class,
        "main",
        "([Ljava/lang/String;)V",
        &[JValue::Object(&null_args)],
    ) {
        Ok(_) => response!("vm_created", "HotSpot created; java.lang.String resolved; OEngineVmSmokeTest.main executed"),
        Err(error) => {
            if hot_spot_env.exception_check().unwrap_or(false) { let _ = hot_spot_env.exception_clear(); }
            response!("java_main_invoke_error", &format!("VM created but smoke-test main failed: {error}"))
        }
    }
}


/// Invokes a Java main class on the same HandlerThread that created HotSpot.
/// This deliberately rejects calls from other threads: the saved JNIEnv is thread-confined.
#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeInvokeMain(
    raw_env: *mut RawJNIEnv,
    _receiver: jobject,
    main_class: jstring,
    arguments: jni::sys::jobjectArray,
) -> jstring {
    if raw_env.is_null() || main_class.is_null() || arguments.is_null() {
        return ptr::null_mut();
    }
    let mut android_env = match JNIEnv::from_raw(raw_env) {
        Ok(value) => value,
        Err(_) => return ptr::null_mut(),
    };
    let respond = |env: &mut JNIEnv, status: &str, detail: &str| {
        env.new_string(format!("{status}:{detail}"))
            .map(|value| value.into_raw())
            .unwrap_or(ptr::null_mut())
    };

    let class_name: String = match android_env.get_string(&JString::from_raw(main_class)) {
        Ok(value) => value.into(),
        Err(error) => return respond(&mut android_env, "invalid_main_class", &error.to_string()),
    };

    if class_name.len() > 512
        || class_name.matches('.').count() == 0
        || !class_name.split('.').all(|part| !part.is_empty() && part.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')) {
        return respond(&mut android_env, "invalid_main_class", "Main class must be a dotted Java class name");
    }

    let argument_array = JObjectArray::from_raw(arguments);
    let count = match android_env.get_array_length(&argument_array) {
        Ok(value) => value,
        Err(error) => return respond(&mut android_env, "invalid_arguments", &error.to_string()),
    };
    if count > 16384 {
        return respond(&mut android_env, "invalid_arguments", "Minecraft argument count exceeds 16384");
    }
    let mut game_args = Vec::with_capacity(count as usize);
    for index in 0..count {
        let value = match android_env.get_object_array_element(&argument_array, index) {
            Ok(value) => value,
            Err(error) => return respond(&mut android_env, "invalid_arguments", &error.to_string()),
        };
        let value: String = match android_env.get_string(&JString::from(value)) {
            Ok(value) => value.into(),
            Err(error) => return respond(&mut android_env, "invalid_arguments", &error.to_string()),
        };
        if value.len() > 65536 || value.contains('\0') {
            return respond(&mut android_env, "invalid_arguments", "A Minecraft argument is too long or contains NUL");
        }
        game_args.push(value);
    }

    let creator = HOTSPOT_CREATOR_THREAD.get()
        .and_then(|state| state.lock().ok())
        .and_then(|guard| guard.clone());
    if creator.as_ref() != Some(&std::thread::current().id()) {
        return respond(&mut android_env, "wrong_thread", "Minecraft main must run on the HotSpot-creating game-process thread");
    }
    let hotspot_env = HOTSPOT_ENV.get()
        .and_then(|state| state.lock().ok())
        .and_then(|guard| *guard);
    let Some(hotspot_env) = hotspot_env else {
        return respond(&mut android_env, "vm_not_created", "No retained HotSpot JNIEnv is available");
    };
    let mut env = match JNIEnv::from_raw(hotspot_env as *mut RawJNIEnv) {
        Ok(value) => value,
        Err(error) => return respond(&mut android_env, "hotspot_env_error", &error.to_string()),
    };

    let internal_name = class_name.replace('.', "/");
    let class = match env.find_class(&internal_name) {
        Ok(value) => value,
        Err(error) => {
            if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
            return respond(&mut android_env, "main_class_not_found", &format!("Cannot load {class_name}: {error}"));
        }
    };
    let string_class = match env.find_class("java/lang/String") {
        Ok(value) => value,
        Err(error) => {
            if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
            return respond(&mut android_env, "string_class_error", &error.to_string());
        }
    };
    let java_args = match env.new_object_array(count, string_class, JObject::null()) {
        Ok(value) => value,
        Err(error) => {
            if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
            return respond(&mut android_env, "argument_array_error", &error.to_string());
        }
    };
    for (index, value) in game_args.iter().enumerate() {
        let java_value = match env.new_string(value) {
            Ok(value) => value,
            Err(error) => {
                if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
                return respond(&mut android_env, "argument_encode_error", &error.to_string());
            }
        };
        if let Err(error) = env.set_object_array_element(&java_args, index as i32, java_value) {
            if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
            return respond(&mut android_env, "argument_array_error", &error.to_string());
        }
    }
    let java_args_object: JObject = java_args.into();
    match env.call_static_method(&class, "main", "([Ljava/lang/String;)V", &[JValue::Object(&java_args_object)]) {
        Ok(_) => respond(&mut android_env, "main_returned", &format!("{class_name}.main returned")),
        Err(error) => {
            if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
            respond(&mut android_env, "main_threw", &format!("{class_name}.main failed: {error}"))
        }
    }
}

type J8NativeLoad = unsafe extern "system" fn(*mut RawJNIEnv, jobject, jstring, jboolean);
type J17NativeLoad = unsafe extern "system" fn(*mut RawJNIEnv, jni::sys::jclass, jobject, jstring, jboolean, jboolean, jboolean) -> jboolean;
type J21NativeLoad = unsafe extern "system" fn(*mut RawJNIEnv, jni::sys::jclass, jobject, jstring, jboolean, jboolean) -> jboolean;

fn preload_for_native_load(raw_env: *mut RawJNIEnv, name: jstring) -> Option<elf_hinter::PreloadedDependencies> {
    if raw_env.is_null() || name.is_null() { return None; }
    let mut env = unsafe { JNIEnv::from_raw(raw_env).ok()? };
    let java_name = unsafe { JString::from_raw(name) };
    let name: String = env.get_string(&java_name).ok()?.into();
    if name.is_empty() || name.len() > 4096 { return None; }
    elf_hinter::preload_named_library(&name).ok()
}

unsafe extern "system" fn native_load_java8(
    raw_env: *mut RawJNIEnv, receiver: jobject, name: jstring, builtin: jboolean
) {
    let _preloaded = preload_for_native_load(raw_env, name);
    let original = NATIVE_LOAD_HOOK.get()
        .and_then(|state| state.lock().ok())
        .and_then(|guard| guard.as_ref().filter(|(major, _, _)| *major == 8).map(|(_, ptr, _)| *ptr));
    if let Some(original) = original {
        let function: J8NativeLoad = std::mem::transmute(original);
        function(raw_env, receiver, name, builtin);
    }
}

unsafe extern "system" fn native_load_java17(
    raw_env: *mut RawJNIEnv, class: jni::sys::jclass, library: jobject, name: jstring,
    builtin: jboolean, is_jni: jboolean, throw_on_fail: jboolean
) -> jboolean {
    let _preloaded = preload_for_native_load(raw_env, name);
    let original = NATIVE_LOAD_HOOK.get()
        .and_then(|state| state.lock().ok())
        .and_then(|guard| guard.as_ref().filter(|(major, _, _)| *major == 17).map(|(_, ptr, _)| *ptr));
    original.map(|ptr| {
        let function: J17NativeLoad = std::mem::transmute(ptr);
        function(raw_env, class, library, name, builtin, is_jni, throw_on_fail)
    }).unwrap_or(0)
}

unsafe extern "system" fn native_load_java21(
    raw_env: *mut RawJNIEnv, class: jni::sys::jclass, library: jobject, name: jstring,
    builtin: jboolean, throw_on_fail: jboolean
) -> jboolean {
    let _preloaded = preload_for_native_load(raw_env, name);
    let original = NATIVE_LOAD_HOOK.get()
        .and_then(|state| state.lock().ok())
        .and_then(|guard| guard.as_ref().filter(|(major, _, _)| *major == 21 || *major == 171).map(|(_, ptr, _)| *ptr));
    original.map(|ptr| {
        let function: J21NativeLoad = std::mem::transmute(ptr);
        function(raw_env, class, library, name, builtin, throw_on_fail)
    }).unwrap_or(0)
}

unsafe fn install_native_library_hook(env: &mut JNIEnv, java_major: i32) -> Result<(), String> {
    if !matches!(java_major, 8 | 17 | 21) {
        return Err(format!("Java {java_major} native-library hook is not verified"));
    }
    let state = NATIVE_LOAD_HOOK.get_or_init(|| Mutex::new(None));
    if state.lock().map_err(|_| "Native-load hook state lock is poisoned")?.is_some() {
        return Err("A native-library hook is already installed in this process".to_owned());
    }

    let symbol_name = if java_major == 8 {
        "Java_java_lang_ClassLoader_00024NativeLibrary_load"
    } else {
        "Java_jdk_internal_loader_NativeLibraries_load"
    };
    let symbol = CString::new(symbol_name).unwrap();
    let libjava_name = CString::new("libjava.so").unwrap();
    let libjava = libc::dlopen(libjava_name.as_ptr(), libc::RTLD_NOW | libc::RTLD_NOLOAD);
    let original = if libjava.is_null() {
        libc::dlsym(libc::RTLD_DEFAULT, symbol.as_ptr())
    } else {
        libc::dlsym(libjava, symbol.as_ptr())
    };
    if original.is_null() {
        if !libjava.is_null() { libc::dlclose(libjava); }
        return Err(format!("Cannot resolve original {symbol_name} in libjava.so"));
    }

    let (class_name, descriptor, function): (&str, &str, *mut c_void) = match java_major {
        8 => (
            "java/lang/ClassLoader$NativeLibrary",
            "(Ljava/lang/String;Z)V",
            native_load_java8 as *mut c_void,
        ),
        17 => {
            let class = env.find_class("jdk/internal/loader/NativeLibraries")
                .map_err(|e| format!("Cannot find JDK 17 NativeLibraries class: {e}"))?;
            let primary = "(Ljdk/internal/loader/NativeLibraries$NativeLibraryImpl;Ljava/lang/String;ZZZ)Z";
            let fallback = "(Ljdk/internal/loader/NativeLibraries$NativeLibraryImpl;Ljava/lang/String;ZZ)Z";
            let primary_found = env.get_static_method_id(&class, "load", primary).is_ok();
            if !primary_found && env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
            let selected = if primary_found { primary } else {
                if env.get_static_method_id(&class, "load", fallback).is_err() {
                    if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
                    return Err("Neither verified Java 17 NativeLibraries.load signature exists".to_owned());
                }
                fallback
            };
            let hook = if selected == primary { native_load_java17 as *mut c_void } else { native_load_java21 as *mut c_void };
            let method = [NativeMethod { name: "load".into(), sig: selected.into(), fn_ptr: hook }];
            env.register_native_methods(&class, &method)
                .map_err(|e| format!("Cannot register Java 17 native-load hook: {e}"))?;
            *state.lock().map_err(|_| "Native-load hook state lock is poisoned")? =
                Some((if selected == primary { 17 } else { 171 }, original as usize, if libjava.is_null() { 0 } else { libjava as usize }));
            return Ok(());
        },
        21 => (
            "jdk/internal/loader/NativeLibraries",
            "(Ljdk/internal/loader/NativeLibraries$NativeLibraryImpl;Ljava/lang/String;ZZ)Z",
            native_load_java21 as *mut c_void,
        ),
        _ => unreachable!(),
    };

    let class = env.find_class(class_name)
        .map_err(|e| format!("Cannot find {class_name}: {e}"))?;
    let methods = [NativeMethod { name: "load".into(), sig: descriptor.into(), fn_ptr: function }];
    env.register_native_methods(&class, &methods)
        .map_err(|e| format!("Cannot register Java {java_major} native-load hook: {e}"))?;
    *state.lock().map_err(|_| "Native-load hook state lock is poisoned")? =
        Some((java_major, original as usize, if libjava.is_null() { 0 } else { libjava as usize }));
    Ok(())
}

/// Pure policy used by the JNI hook: only the exact renderer alias is redirected.
fn is_redirect_candidate(requested: &[u8], selected_alias: &[u8]) -> bool {
    !requested.is_empty() && !selected_alias.is_empty() && requested == selected_alias
}

/// LWJGL's upstream DynamicLinkLoader.ndlopen(long,int) replacement.
/// The renderer handle is process-owned and must remain open for the whole game session.
unsafe extern "system" fn lwjgl_ndlopen_hook(
    _env: *mut RawJNIEnv,
    _class: jni::sys::jclass,
    filename_ptr: jlong,
    mode: jint,
) -> jlong {
    if filename_ptr == 0 {
        return 0;
    }
    let filename = CStr::from_ptr(filename_ptr as *const c_char);
    if let Ok(guard) = hook_config().read() {
        if let Some(config) = guard.as_ref() {
            if is_redirect_candidate(filename.to_bytes(), config.selected_alias.as_bytes()) {
                return config.renderer_handle as jlong;
            }
        }
    }
    libc::dlopen(filename.as_ptr(), mode) as jlong
}

/// Loads one validated renderer shared object into the process linker namespace.
/// Handles are retained for the lifetime of the game process because LWJGL may call
/// the replacement long after installation.
#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeLoadRenderer(
    raw_env: *mut RawJNIEnv,
    _receiver: jobject,
    renderer_path: jstring,
) -> jlong {
    let set_error = |detail: String| {
        if let Ok(mut value) = RENDERER_LOAD_ERROR.get_or_init(|| Mutex::new(String::new())).lock() {
            *value = detail;
        }
    };
    if raw_env.is_null() || renderer_path.is_null() {
        set_error("JNI environment or renderer path is null".to_owned());
        return 0;
    }
    let mut env = match JNIEnv::from_raw(raw_env) {
        Ok(value) => value,
        Err(error) => { set_error(format!("Cannot access JNI environment: {error}")); return 0; },
    };
    let path: String = match env.get_string(&JString::from_raw(renderer_path)) {
        Ok(value) => value.into(),
        Err(error) => { set_error(format!("Cannot read renderer path: {error}")); return 0; },
    };
    let canonical = match std::fs::canonicalize(&path) {
        Ok(value) if value.is_absolute() && value.is_file() => value,
        _ => { set_error(format!("Renderer file does not exist or cannot be canonicalized: {path}")); return 0; },
    };
    let path = match CString::new(canonical.to_string_lossy().as_bytes()) {
        Ok(value) => value,
        Err(_) => { set_error("Renderer path contains a NUL byte".to_owned()); return 0; },
    };
    let libraries = RENDERER_LIBRARIES.get_or_init(|| Mutex::new(Vec::new()));
    let mut guard = match libraries.lock() {
        Ok(value) => value,
        Err(_) => { set_error("Renderer library state lock is poisoned".to_owned()); return 0; },
    };
    if let Some((_, handle)) = guard.iter().find(|(loaded, _)| loaded == &path) {
        set_error(String::new());
        return *handle as jlong;
    }
    libc::dlerror();
    // Renderer wrappers are loaded as local EGL providers, matching MojoExec's
    // RTLD_LOCAL | RTLD_NOW contract. LWJGL receives the retained handle explicitly
    // through the verified loader hook, so exporting every renderer symbol globally
    // is unnecessary and risks collisions with Android's system EGL/GLES libraries.
    let handle = libc::dlopen(path.as_ptr(), libc::RTLD_NOW | libc::RTLD_LOCAL);
    if handle.is_null() {
        let error = libc::dlerror();
        let detail = if error.is_null() {
            "dlopen failed without linker detail".to_owned()
        } else {
            CStr::from_ptr(error).to_string_lossy().into_owned()
        };
        set_error(format!("Android linker rejected {}: {detail}", path.to_string_lossy()));
        return 0;
    }
    set_error(String::new());
    let raw = handle as usize;
    guard.push((path, raw));
    raw as jlong
}

/// Returns the most recent renderer dlopen diagnostic for the Kotlin bridge.
#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeRendererLoadError(
    raw_env: *mut RawJNIEnv,
    _receiver: jobject,
) -> jstring {
    if raw_env.is_null() { return ptr::null_mut(); }
    let mut env = match JNIEnv::from_raw(raw_env) {
        Ok(value) => value,
        Err(_) => return ptr::null_mut(),
    };
    let detail = RENDERER_LOAD_ERROR.get_or_init(|| Mutex::new(String::new()))
        .lock().map(|value| value.clone()).unwrap_or_else(|_| "Renderer diagnostic lock is poisoned".to_owned());
    env.new_string(detail).map(|value| value.into_raw()).unwrap_or(ptr::null_mut())
}

/// Installs the LWJGL 3 JNI method replacement using the embedded HotSpot VM's
/// own system class loader. Android ART Class objects must never be passed to HotSpot.
#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeInstallLwjglHook(
    raw_env: *mut RawJNIEnv,
    _receiver: jobject,
    target_class_name: jstring,
    selected_alias: jstring,
    renderer_handle: jlong,
) -> jboolean {
    let set_error = |detail: &str| {
        if let Ok(mut value) = RENDERER_HOOK_ERROR.get_or_init(|| Mutex::new(String::new())).lock() {
            *value = detail.to_owned();
        }
    };
    set_error("Hook installation did not complete.");
    if raw_env.is_null() || target_class_name.is_null() || selected_alias.is_null() || renderer_handle == 0 {
        set_error("JNI input or renderer handle is null.");
        return 0;
    }

    let mut android_env = match JNIEnv::from_raw(raw_env) {
        Ok(value) => value,
        Err(error) => { set_error(&format!("Cannot access Android JNI environment: {error}")); return 0; },
    };
    let class_name: String = match android_env.get_string(&JString::from_raw(target_class_name)) {
        Ok(value) => value.into(),
        Err(error) => { set_error(&format!("Cannot read LWJGL target class name: {error}")); return 0; },
    };
    let alias: String = match android_env.get_string(&JString::from_raw(selected_alias)) {
        Ok(value) => value.into(),
        Err(error) => { set_error(&format!("Cannot read renderer alias: {error}")); return 0; },
    };
    if !is_valid_lwjgl_class_name(&class_name) || !is_valid_renderer_alias(&alias) {
        set_error("Invalid target class name or renderer alias format.");
        return 0;
    }
    let alias = match CString::new(alias) {
        Ok(value) => value,
        Err(_) => { set_error("Renderer alias contains an invalid NUL byte."); return 0; },
    };

    let creator = HOTSPOT_CREATOR_THREAD.get()
        .and_then(|state| state.lock().ok())
        .and_then(|guard| guard.clone());
    if creator.as_ref() != Some(&std::thread::current().id()) {
        set_error("Hook installation must run on the HotSpot creator thread.");
        return 0;
    }
    let hotspot_env = HOTSPOT_ENV.get()
        .and_then(|state| state.lock().ok())
        .and_then(|guard| *guard);
    let Some(hotspot_env) = hotspot_env else {
        set_error("HotSpot JNIEnv is unavailable; create the JVM before installing the hook.");
        return 0;
    };
    let mut env = match JNIEnv::from_raw(hotspot_env as *mut RawJNIEnv) {
        Ok(value) => value,
        Err(error) => { set_error(&format!("Cannot access embedded HotSpot JNIEnv: {error}")); return 0; },
    };

    let loader = match env.call_static_method(
        "java/lang/ClassLoader",
        "getSystemClassLoader",
        "()Ljava/lang/ClassLoader;",
        &[],
    ) {
        Ok(value) => match value.l() {
            Ok(value) if !value.is_null() => value,
            _ => { set_error("HotSpot returned a null system class loader."); return 0; },
        },
        Err(error) => {
            set_error(&format!("Cannot obtain HotSpot system class loader: {error}"));
            if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
            return 0;
        }
    };
    let java_name = match env.new_string(&class_name) {
        Ok(value) => value,
        Err(error) => {
            set_error(&format!("Cannot create target class name: {error}"));
            if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
            return 0;
        }
    };
    let class_object = match env.call_method(
        &loader,
        "loadClass",
        "(Ljava/lang/String;)Ljava/lang/Class;",
        &[JValue::Object(&JObject::from(java_name))],
    ) {
        Ok(value) => match value.l() {
            Ok(value) if !value.is_null() => value,
            _ => { set_error("HotSpot returned a null LWJGL target class."); return 0; },
        },
        Err(error) => {
            set_error(&format!("HotSpot could not load {class_name}: {error}"));
            if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
            return 0;
        }
    };
    let class = JClass::from(class_object);

    // Hold the write lock through registration so the redirect becomes visible
    // atomically with the native method. The hook itself only takes a read lock.
    let mut guard = match hook_config().write() {
        Ok(value) => value,
        Err(_) => { set_error("Renderer hook state lock is poisoned."); return 0; },
    };
    if let Some(existing) = guard.as_ref() {
        if existing.selected_alias != alias || existing.renderer_handle != renderer_handle as usize {
            set_error("A different renderer hook is already installed in this process.");
            return 0;
        }
    }

    let methods = [NativeMethod {
        name: "ndlopen".into(),
        sig: "(JI)J".into(),
        fn_ptr: lwjgl_ndlopen_hook as *mut c_void,
    }];
    if let Err(error) = env.register_native_methods(&class, &methods) {
        set_error(&format!("HotSpot rejected DynamicLinkLoader.ndlopen(JI)J: {error}"));
        if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
        return 0;
    }

    // A failed RegisterNatives must not leave a half-installed process-wide hook.
    if guard.is_none() {
        *guard = Some(HookConfig {
            selected_alias: alias,
            renderer_handle: renderer_handle as usize,
        });
    }
    set_error("");
    1
}

/// Returns the most recent LWJGL native-hook installation diagnostic.
#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeRendererHookError(
    raw_env: *mut RawJNIEnv,
    _receiver: jobject,
) -> jstring {
    if raw_env.is_null() { return ptr::null_mut(); }
    let mut env = match JNIEnv::from_raw(raw_env) { Ok(value) => value, Err(_) => return ptr::null_mut() };
    let detail = RENDERER_HOOK_ERROR.get_or_init(|| Mutex::new(String::new()))
        .lock().map(|value| value.clone()).unwrap_or_else(|_| "Renderer hook diagnostic lock is poisoned".to_owned());
    env.new_string(detail).map(|value| value.into_raw()).unwrap_or(ptr::null_mut())
}

#[cfg(test)]
mod native_input_dispatch_tests {
    use super::*;

    static TRACE: OnceLock<Mutex<Vec<(i32, i64, i64)>>> = OnceLock::new();
    static DISPATCH_TEST_LOCK: Mutex<()> = Mutex::new(());

    fn record(kind: i32, first: i64, second: i64) {
        TRACE.get_or_init(|| Mutex::new(Vec::new())).lock().unwrap().push((kind, first, second));
    }

    unsafe extern "C" fn key(_window: *mut c_void, code: i32, scan: i32, action: i32, mods: i32) {
        record(1, code as i64, scan as i64);
        record(7, action as i64, mods as i64);
    }
    unsafe extern "C" fn character(_window: *mut c_void, codepoint: u32) {
        record(2, codepoint as i64, 0);
    }
    unsafe extern "C" fn character_mods(_window: *mut c_void, codepoint: u32, mods: i32) {
        record(7, codepoint as i64, mods as i64);
    }
    unsafe extern "C" fn cursor(_window: *mut c_void, x: f64, y: f64) {
        record(3, x as i64, y as i64);
    }
    unsafe extern "C" fn mouse(_window: *mut c_void, button: i32, action: i32, _mods: i32) {
        record(4, button as i64, action as i64);
    }
    unsafe extern "C" fn scroll(_window: *mut c_void, x: f64, y: f64) {
        record(5, x as i64, y as i64);
    }
    unsafe extern "C" fn platform(
        _window: *mut c_void, device: i32, action: i32, _code: i32, _scan_code: i32,
        _x: f32, _y: f32, _value: f32, _time: i64, _mods: i32, _codepoint: u32
    ) {
        record(6, device as i64, action as i64);
    }

    #[test]
    fn dispatch_maps_keyboard_mouse_and_platform_events_to_callbacks() {
        let _serial = DISPATCH_TEST_LOCK.lock().unwrap();
        let trace = TRACE.get_or_init(|| Mutex::new(Vec::new()));
        trace.lock().unwrap().clear();
        let callbacks = OEngineNativeInputCallbacks {
            key: Some(key), character: Some(character), character_mods: Some(character_mods), cursor_position: Some(cursor),
            mouse_button: Some(mouse), scroll: Some(scroll), platform_event: Some(platform),
        };
        let window = ptr::null_mut();
        unsafe {
            dispatch_native_input_event(window, &callbacks, OEngineNativeInputEvent {
                device: 1, action: 0, code: 87, scan_code: 42, codepoint: 119, modifiers: 1, ..Default::default()
            });
            dispatch_native_input_event(window, &callbacks, OEngineNativeInputEvent {
                device: 2, action: 0, code: 0, modifiers: 2, ..Default::default()
            });
            dispatch_native_input_event(window, &callbacks, OEngineNativeInputEvent {
                device: 2, action: 3, x: 12.0, y: 34.0, ..Default::default()
            });
            dispatch_native_input_event(window, &callbacks, OEngineNativeInputEvent {
                device: 2, action: 4, x: 1.0, y: -2.0, ..Default::default()
            });
            dispatch_native_input_event(window, &callbacks, OEngineNativeInputEvent {
                device: 0, action: 0, code: 7, ..Default::default()
            });
        }
        assert_eq!(*trace.lock().unwrap(), vec![
            (1, 87, 42), (7, 1, 1), (2, 119, 0), (7, 119, 1), (4, 0, 1), (3, 12, 34), (5, 1, -2), (6, 0, 0)
        ]);
    }

    #[test]
    fn key_release_does_not_emit_text_and_repeat_uses_glfw_repeat() {
        let _serial = DISPATCH_TEST_LOCK.lock().unwrap();
        let trace = TRACE.get_or_init(|| Mutex::new(Vec::new()));
        trace.lock().unwrap().clear();
        let callbacks = OEngineNativeInputCallbacks {
            key: Some(key), character: Some(character), character_mods: Some(character_mods),
            ..Default::default()
        };
        unsafe {
            dispatch_native_input_event(ptr::null_mut(), &callbacks, OEngineNativeInputEvent {
                device: 1, action: 1, code: 87, scan_code: 42, codepoint: 119, ..Default::default()
            });
            dispatch_native_input_event(ptr::null_mut(), &callbacks, OEngineNativeInputEvent {
                device: 1, action: 2, code: 87, scan_code: 42, codepoint: 119, ..Default::default()
            });
        }
        assert_eq!(*trace.lock().unwrap(), vec![
            (1, 87, 42), (7, 0, 0),
            (1, 87, 42), (7, 2, 0), (2, 119, 0), (7, 119, 0)
        ]);
    }

    #[test]
    fn native_batch_enqueue_is_atomic_when_a_transition_cannot_fit() {
        let mut queue = OEngineNativeInputQueue::default();
        for code in 0..INPUT_QUEUE_CAPACITY {
            assert!(queue.enqueue(OEngineNativeInputEvent {
                device: 1, action: 0, code: code as i32, ..Default::default()
            }));
        }
        let before = queue.events.len();
        let batch = [
            OEngineNativeInputEvent { device: 1, action: 0, code: 9001, ..Default::default() },
            OEngineNativeInputEvent { device: 1, action: 1, code: 9001, ..Default::default() },
        ];
        assert!(!queue.enqueue_batch(&batch));
        assert_eq!(queue.events.len(), before);
        assert_eq!(queue.pressed.len(), INPUT_QUEUE_CAPACITY);
        assert!(queue.reset_required);
        assert!(!queue.pressed.iter().any(|event| event.code == 9001));
    }

    #[test]
    fn oengine_transition_values_are_translated_to_glfw_actions() {
        assert_eq!(glfw_action_from_oengine(0), Some(1)); // PRESS
        assert_eq!(glfw_action_from_oengine(1), Some(0)); // RELEASE
        assert_eq!(glfw_action_from_oengine(2), Some(2)); // REPEAT
        assert_eq!(glfw_action_from_oengine(3), None);
        assert_eq!(glfw_action_from_oengine(4), None);
    }

    #[test]
    fn exported_c_dispatch_drains_the_native_queue() {
        let _serial = DISPATCH_TEST_LOCK.lock().unwrap();
        oengine_input_clear_session();
        TRACE.get_or_init(|| Mutex::new(Vec::new())).lock().unwrap().clear();
        native_input_queue().lock().unwrap().enqueue(OEngineNativeInputEvent {
            device: 1, action: 0, code: 65, codepoint: 97, ..Default::default()
        });
        let callbacks = OEngineNativeInputCallbacks {
            key: Some(key), character: Some(character), character_mods: Some(character_mods), ..Default::default()
        };
        let count = unsafe { oengine_input_dispatch(ptr::null_mut(), &callbacks, 8) };
        assert_eq!(count, 1);
        assert_eq!(*TRACE.get().unwrap().lock().unwrap(), vec![(1, 65, 0), (7, 1, 0), (2, 97, 0), (7, 97, 0)]);
        oengine_input_clear_session();
    }

    #[test]
    fn native_input_event_matches_the_published_c_abi_layout() {
        use std::mem::{align_of, offset_of, size_of};
        assert_eq!(align_of::<OEngineNativeInputEvent>(), 8);
        assert_eq!(offset_of!(OEngineNativeInputEvent, device), 0);
        assert_eq!(offset_of!(OEngineNativeInputEvent, action), 4);
        assert_eq!(offset_of!(OEngineNativeInputEvent, code), 8);
        assert_eq!(offset_of!(OEngineNativeInputEvent, x), 12);
        assert_eq!(offset_of!(OEngineNativeInputEvent, y), 16);
        assert_eq!(offset_of!(OEngineNativeInputEvent, value), 20);
        assert_eq!(offset_of!(OEngineNativeInputEvent, timestamp_nanos), 24);
        assert_eq!(offset_of!(OEngineNativeInputEvent, modifiers), 32);
        assert_eq!(offset_of!(OEngineNativeInputEvent, codepoint), 36);
        assert_eq!(offset_of!(OEngineNativeInputEvent, scan_code), 40);
        assert_eq!(offset_of!(OEngineNativeInputEvent, reserved), 44);
        assert_eq!(size_of::<OEngineNativeInputEvent>(), 48);
    }

    #[test]
    fn ltw_egl_share_info_has_stable_c_pointer_layout() {
        use std::mem::{align_of, offset_of, size_of};
        assert_eq!(align_of::<OEngineLtwEglShareInfo>(), align_of::<usize>());
        assert_eq!(offset_of!(OEngineLtwEglShareInfo, display), 0);
        assert_eq!(offset_of!(OEngineLtwEglShareInfo, config), size_of::<usize>());
        assert_eq!(offset_of!(OEngineLtwEglShareInfo, context), 2 * size_of::<usize>());
        assert_eq!(offset_of!(OEngineLtwEglShareInfo, pbuffer), 3 * size_of::<usize>());
        assert_eq!(offset_of!(OEngineLtwEglShareInfo, window_surface), 4 * size_of::<usize>());
        assert_eq!(offset_of!(OEngineLtwEglShareInfo, create_context), 5 * size_of::<usize>());
        assert_eq!(offset_of!(OEngineLtwEglShareInfo, make_current), 6 * size_of::<usize>());
        assert_eq!(offset_of!(OEngineLtwEglShareInfo, destroy_context), 7 * size_of::<usize>());
        assert_eq!(offset_of!(OEngineLtwEglShareInfo, destroy_surface), 8 * size_of::<usize>());
        assert_eq!(offset_of!(OEngineLtwEglShareInfo, swap_buffers), 9 * size_of::<usize>());
        assert_eq!(size_of::<OEngineLtwEglShareInfo>(), 10 * size_of::<usize>());
    }

    #[test]
    fn callback_table_is_a_c_compatible_pointer_table() {
        assert_eq!(
            std::mem::size_of::<OEngineNativeInputCallbacks>(),
            7 * std::mem::size_of::<usize>()
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lwjgl_class_names_must_be_valid_dotted_names() {
        assert!(is_valid_lwjgl_class_name("org.lwjgl.system.Library"));
        assert!(!is_valid_lwjgl_class_name(""));
        assert!(!is_valid_lwjgl_class_name("org..Library"));
        assert!(!is_valid_lwjgl_class_name("org/lwjgl/Library"));
        assert!(!is_valid_lwjgl_class_name("org.lwjgl.Library;"));
        assert!(!is_valid_lwjgl_class_name(&"a".repeat(513)));
    }

    #[test]
    fn renderer_alias_must_be_a_bare_shared_library_name() {
        assert!(is_valid_renderer_alias("libGLMojo.so"));
        assert!(!is_valid_renderer_alias(""));
        assert!(!is_valid_renderer_alias("libGLMojo"));
        assert!(!is_valid_renderer_alias("/system/lib/libGLMojo.so"));
        assert!(!is_valid_renderer_alias("libGLMojo.so\0evil"));
        assert!(!is_valid_renderer_alias(&format!("lib{}.so", "x".repeat(126))));
    }

    #[test]
    fn exact_renderer_alias_is_redirected() {
        assert!(is_redirect_candidate(b"libGLMojo.so", b"libGLMojo.so"));
        assert!(!is_redirect_candidate(b"libGL.so", b"libGLMojo.so"));
    }

    #[test]
    fn unrelated_libraries_keep_normal_loader_path() {
        assert!(!is_redirect_candidate(b"libvulkan.so", b"libGLMojo.so"));
        assert!(!is_redirect_candidate(b"/system/lib/libGLMojo.so", b"libGLMojo.so"));
    }

    #[test]
    fn empty_names_are_never_redirected() {
        assert!(!is_redirect_candidate(b"", b"libGLMojo.so"));
        assert!(!is_redirect_candidate(b"libGLMojo.so", b""));
    }

    #[test]
    fn abi_version_is_stable() {
        assert_eq!(oengine_native_abi_version(), ABI_VERSION);
    }
}


// ---- LTW-owned EGL context/session -------------------------------------------------
//
// LTW tracks desktop-GL compatibility state per EGLContext. Creating an EGLContext
// through Android's EGL14 API bypasses LTW's eglCreateContext wrapper and leaves
// LTW's context_map empty; the first wrapped GL call can then abort. O Engine must
// create, bind, and destroy the context through the retained LTW wrapper symbols.
type OEngineEglGetDisplay = unsafe extern "C" fn(*mut c_void) -> *mut c_void;
type OEngineEglInitialize = unsafe extern "C" fn(*mut c_void, *mut i32, *mut i32) -> u32;
type OEngineEglBindApi = unsafe extern "C" fn(u32) -> u32;
type OEngineEglChooseConfig = unsafe extern "C" fn(*mut c_void, *const i32, *mut *mut c_void, i32, *mut i32) -> u32;
type OEngineEglCreatePbuffer = unsafe extern "C" fn(*mut c_void, *mut c_void, *const i32) -> *mut c_void;
type OEngineEglCreateWindow = unsafe extern "C" fn(*mut c_void, *mut c_void, *mut c_void, *const i32) -> *mut c_void;
type OEngineEglDestroySurface = unsafe extern "C" fn(*mut c_void, *mut c_void) -> u32;
type OEngineEglTerminate = unsafe extern "C" fn(*mut c_void) -> u32;
type OEngineEglGetError = unsafe extern "C" fn() -> i32;
type OEngineEglSwapBuffers = unsafe extern "C" fn(*mut c_void, *mut c_void) -> u32;
type OEngineLtwCreateContext = unsafe extern "C" fn(*mut c_void, *mut c_void, *mut c_void, *const i32) -> *mut c_void;
type OEngineLtwDestroyContext = unsafe extern "C" fn(*mut c_void, *mut c_void) -> u32;
type OEngineLtwMakeCurrent = unsafe extern "C" fn(*mut c_void, *mut c_void, *mut c_void, *mut c_void) -> u32;
type OEngineEglGetProcAddress = unsafe extern "C" fn(*const c_char) -> *mut c_void;
type OEngineGlGetString = unsafe extern "C" fn(u32) -> *const u8;
type OEngineGlViewport = unsafe extern "C" fn(i32, i32, i32, i32);

#[repr(C)]
pub struct OEngineLtwEglShareInfo {
    pub display: *mut c_void,
    pub config: *mut c_void,
    pub context: *mut c_void,
    pub pbuffer: *mut c_void,
    pub window_surface: *mut c_void,
    pub create_context: *mut c_void,
    pub make_current: *mut c_void,
    pub destroy_context: *mut c_void,
    pub destroy_surface: *mut c_void,
    pub swap_buffers: *mut c_void,
}

struct OEngineLtwEglSession {
    egl_library: *mut c_void,
    gles_library: *mut c_void,
    display: *mut c_void,
    config: *mut c_void,
    context: *mut c_void,
    pbuffer: *mut c_void,
    window_surface: *mut c_void,
    native_window: *mut c_void,
    egl_destroy_surface: OEngineEglDestroySurface,
    egl_terminate: OEngineEglTerminate,
    egl_get_error: OEngineEglGetError,
    egl_swap_buffers: OEngineEglSwapBuffers,
    ltw_create_context: OEngineLtwCreateContext,
    ltw_destroy_context: OEngineLtwDestroyContext,
    ltw_make_current: OEngineLtwMakeCurrent,
    gl_viewport: Option<OEngineGlViewport>,
    owner_thread: ThreadId,
    version: String,
    renderer: String,
}

static LTW_EGL_ERROR: OnceLock<Mutex<String>> = OnceLock::new();
static ACTIVE_LTW_EGL_SESSION: AtomicUsize = AtomicUsize::new(0);

#[derive(Clone, Copy)]
struct PendingLtwWindow { window: usize, width: i32, height: i32 }
static PENDING_LTW_WINDOW: OnceLock<Mutex<Option<PendingLtwWindow>>> = OnceLock::new();

fn set_ltw_egl_error(message: String) {
    if let Ok(mut value) = LTW_EGL_ERROR.get_or_init(|| Mutex::new(String::new())).lock() {
        *value = message;
    }
}

unsafe fn oengine_symbol<T: Copy>(handle: *mut c_void, name: &'static [u8]) -> Result<T, String> {
    let symbol = libc::dlsym(handle, name.as_ptr() as *const c_char);
    if symbol.is_null() {
        return Err(format!("Missing native symbol {}", String::from_utf8_lossy(&name[..name.len().saturating_sub(1)])));
    }
    Ok(std::mem::transmute_copy(&symbol))
}

unsafe fn oengine_egl_error(get_error: OEngineEglGetError, operation: &str) -> String {
    format!("{operation} failed with EGL error 0x{:04x}", get_error())
}

unsafe fn release_anative_window(window: *mut c_void) {
    if window.is_null() { return; }
    let library = android_library();
    if library.is_null() { return; }
    let release = libc::dlsym(library, b"ANativeWindow_release\0".as_ptr() as *const c_char);
    if !release.is_null() {
        let release: OEngineNativeWindowRelease = std::mem::transmute(release);
        release(window);
    }
}

unsafe fn attach_ltw_window_inner(
    session: &mut OEngineLtwEglSession, window: *mut c_void, width: i32, height: i32,
) -> Result<(), String> {
    if window.is_null() || width <= 0 || height <= 0 {
        return Err("Queued LTW window attachment has an invalid window or size.".to_owned());
    }
    if !session.window_surface.is_null() {
        if (session.ltw_make_current)(session.display, session.pbuffer, session.pbuffer, session.context) == 0 {
            return Err(oengine_egl_error(session.egl_get_error, "LTW eglMakeCurrent(pbuffer before window replacement)"));
        }
        if (session.egl_destroy_surface)(session.display, session.window_surface) == 0 {
            return Err(oengine_egl_error(session.egl_get_error, "eglDestroySurface(previous window)"));
        }
        session.window_surface = ptr::null_mut();
    }
    let create_window: OEngineEglCreateWindow = oengine_symbol(session.egl_library, b"eglCreateWindowSurface\0")?;
    let surface = create_window(session.display, session.config, window, [0x3038].as_ptr());
    if surface.is_null() {
        return Err(oengine_egl_error(session.egl_get_error, "eglCreateWindowSurface(queued window)"));
    }
    if (session.ltw_make_current)(session.display, surface, surface, session.context) == 0 {
        let detail = oengine_egl_error(session.egl_get_error, "LTW eglMakeCurrent(queued window)");
        let _ = (session.egl_destroy_surface)(session.display, surface);
        return Err(detail);
    }
    if let Some(viewport) = session.gl_viewport { viewport(0, 0, width, height); }
    let previous_window = session.native_window;
    session.window_surface = surface;
    session.native_window = window;
    if !previous_window.is_null() && previous_window != window {
        release_anative_window(previous_window);
    }
    Ok(())
}

unsafe fn detach_ltw_window_inner(session: &mut OEngineLtwEglSession) -> Result<(), String> {
    if !session.window_surface.is_null() {
        if (session.ltw_make_current)(session.display, session.pbuffer, session.pbuffer, session.context) == 0 {
            return Err(oengine_egl_error(session.egl_get_error, "LTW eglMakeCurrent(pbuffer for detach)"));
        }
        if (session.egl_destroy_surface)(session.display, session.window_surface) == 0 {
            return Err(oengine_egl_error(session.egl_get_error, "eglDestroySurface(window detach)"));
        }
        session.window_surface = ptr::null_mut();
    }
    if let Some(viewport) = session.gl_viewport { viewport(0, 0, 1, 1); }
    let previous_window = std::mem::replace(&mut session.native_window, ptr::null_mut());
    release_anative_window(previous_window);
    Ok(())
}

/// Called from GLFW's event-poll thread, which is also the LTW session owner.
#[no_mangle]
pub unsafe extern "C" fn oengine_ltw_egl_process_pending_window() -> u32 {
    let pending = match PENDING_LTW_WINDOW.get_or_init(|| Mutex::new(None)).lock() {
        Ok(mut slot) => slot.take(),
        Err(_) => return 0,
    };
    let Some(change) = pending else { return 1; };
    let handle = ACTIVE_LTW_EGL_SESSION.load(Ordering::Acquire);
    if handle == 0 {
        if change.window != 0 { release_anative_window(change.window as *mut c_void); }
        set_ltw_egl_error("Cannot apply queued Android Surface change: no active LTW session.".to_owned());
        return 0;
    }
    let session = match with_ltw_egl_session(handle as jlong) {
        Ok(value) => value,
        Err(error) => {
            if change.window != 0 { release_anative_window(change.window as *mut c_void); }
            set_ltw_egl_error(error);
            return 0;
        }
    };
    let result = if change.window == 0 {
        detach_ltw_window_inner(session)
    } else {
        attach_ltw_window_inner(session, change.window as *mut c_void, change.width, change.height)
    };
    match result {
        Ok(()) => 1,
        Err(error) => {
            if change.window != 0 && session.native_window != change.window as *mut c_void {
                release_anative_window(change.window as *mut c_void);
            }
            set_ltw_egl_error(error);
            0
        }
    }
}

/// Queue a Surface transition from Binder/ART; EGL is only touched later by GLFW's game thread.
#[no_mangle]
pub unsafe extern "C" fn oengine_ltw_egl_queue_window(
    window: *mut c_void, width: i32, height: i32,
) -> u32 {
    if !window.is_null() && (width <= 0 || height <= 0) {
        set_ltw_egl_error("Queued LTW window dimensions must be positive.".to_owned());
        return 0;
    }
    if ACTIVE_LTW_EGL_SESSION.load(Ordering::Acquire) == 0 {
        set_ltw_egl_error("Cannot queue Android Surface change before LTW EGL session creation.".to_owned());
        return 0;
    }
    let slot = PENDING_LTW_WINDOW.get_or_init(|| Mutex::new(None));
    let mut pending = match slot.lock() {
        Ok(value) => value,
        Err(_) => {
            set_ltw_egl_error("LTW Surface event queue lock is poisoned.".to_owned());
            return 0;
        }
    };
    if let Some(previous) = pending.take() {
        if previous.window != 0 && previous.window != window as usize {
            release_anative_window(previous.window as *mut c_void);
        }
    }
    *pending = Some(PendingLtwWindow { window: window as usize, width, height });
    1
}

unsafe fn destroy_ltw_egl_session(session: &mut OEngineLtwEglSession) {
    if !session.window_surface.is_null() {
        let _ = (session.ltw_make_current)(session.display, session.pbuffer, session.pbuffer, session.context);
        let _ = (session.egl_destroy_surface)(session.display, session.window_surface);
        session.window_surface = ptr::null_mut();
    }
    release_anative_window(std::mem::replace(&mut session.native_window, ptr::null_mut()));
    if !session.display.is_null() {
        let _ = (session.ltw_make_current)(session.display, ptr::null_mut(), ptr::null_mut(), ptr::null_mut());
    }
    if !session.context.is_null() {
        let _ = (session.ltw_destroy_context)(session.display, session.context);
        session.context = ptr::null_mut();
    }
    if !session.pbuffer.is_null() {
        let _ = (session.egl_destroy_surface)(session.display, session.pbuffer);
        session.pbuffer = ptr::null_mut();
    }
    if !session.display.is_null() {
        let _ = (session.egl_terminate)(session.display);
        session.display = ptr::null_mut();
    }
    if !session.gles_library.is_null() {
        libc::dlclose(session.gles_library);
        session.gles_library = ptr::null_mut();
    }
    if !session.egl_library.is_null() {
        libc::dlclose(session.egl_library);
        session.egl_library = ptr::null_mut();
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeCreateLtwEglSession(
    raw_env: *mut RawJNIEnv, _receiver: jobject, renderer_handle: jlong, minimum_gles: jint,
) -> jlong {
    if raw_env.is_null() || renderer_handle == 0 || !(3..=3).contains(&minimum_gles) {
        set_ltw_egl_error("LTW EGL session requires a loaded renderer and GLES 3.".to_owned());
        return 0;
    }
    let ltw = renderer_handle as *mut c_void;
    let egl_library = libc::dlopen(b"libEGL.so\0".as_ptr() as *const c_char, libc::RTLD_NOW | libc::RTLD_LOCAL);
    if egl_library.is_null() {
        set_ltw_egl_error("Could not load Android libEGL.so for the LTW session.".to_owned());
        return 0;
    }
    let gles_library = {
        let first = libc::dlopen(b"libGLESv3.so\0".as_ptr() as *const c_char, libc::RTLD_NOW | libc::RTLD_LOCAL);
        if first.is_null() {
            libc::dlopen(b"libGLESv2.so\0".as_ptr() as *const c_char, libc::RTLD_NOW | libc::RTLD_LOCAL)
        } else { first }
    };
    if gles_library.is_null() {
        libc::dlclose(egl_library);
        set_ltw_egl_error("Could not load Android GLES entry points.".to_owned());
        return 0;
    }

    let setup = (|| -> Result<OEngineLtwEglSession, String> {
        let get_display: OEngineEglGetDisplay = oengine_symbol(egl_library, b"eglGetDisplay\0")?;
        let initialize: OEngineEglInitialize = oengine_symbol(egl_library, b"eglInitialize\0")?;
        let bind_api: OEngineEglBindApi = oengine_symbol(egl_library, b"eglBindAPI\0")?;
        let choose_config: OEngineEglChooseConfig = oengine_symbol(egl_library, b"eglChooseConfig\0")?;
        let create_pbuffer: OEngineEglCreatePbuffer = oengine_symbol(egl_library, b"eglCreatePbufferSurface\0")?;
        let destroy_surface: OEngineEglDestroySurface = oengine_symbol(egl_library, b"eglDestroySurface\0")?;
        let terminate: OEngineEglTerminate = oengine_symbol(egl_library, b"eglTerminate\0")?;
        let get_error: OEngineEglGetError = oengine_symbol(egl_library, b"eglGetError\0")?;
        let swap_buffers: OEngineEglSwapBuffers = oengine_symbol(egl_library, b"eglSwapBuffers\0")?;
        let ltw_create: OEngineLtwCreateContext = oengine_symbol(ltw, b"eglCreateContext\0")?;
        let ltw_destroy: OEngineLtwDestroyContext = oengine_symbol(ltw, b"eglDestroyContext\0")?;
        let ltw_make_current: OEngineLtwMakeCurrent = oengine_symbol(ltw, b"eglMakeCurrent\0")?;
        let display = get_display(ptr::null_mut());
        if display.is_null() { return Err(oengine_egl_error(get_error, "eglGetDisplay")); }
        let mut major = 0;
        let mut minor = 0;
        if initialize(display, &mut major, &mut minor) == 0 {
            return Err(oengine_egl_error(get_error, "eglInitialize"));
        }
        if bind_api(0x30A0) == 0 {
            let detail = oengine_egl_error(get_error, "eglBindAPI(OpenGL ES)");
            let _ = terminate(display);
            return Err(detail);
        }
        let attributes = [
            0x3033, 0x0001 | 0x0004, // EGL_SURFACE_TYPE: pbuffer + window
            0x3040, 0x0040,          // EGL_RENDERABLE_TYPE: OpenGL ES 3
            0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8,
            0x3038,
        ];
        let mut config = ptr::null_mut();
        let mut count = 0;
        if choose_config(display, attributes.as_ptr(), &mut config, 1, &mut count) == 0 || count < 1 || config.is_null() {
            let detail = oengine_egl_error(get_error, "eglChooseConfig(GLES3 window+pbuffer)");
            let _ = terminate(display);
            return Err(detail);
        }
        let pbuffer_attributes = [0x3057, 1, 0x3056, 1, 0x3038];
        let pbuffer = create_pbuffer(display, config, pbuffer_attributes.as_ptr());
        if pbuffer.is_null() {
            let detail = oengine_egl_error(get_error, "eglCreatePbufferSurface");
            let _ = terminate(display);
            return Err(detail);
        }
        let context_attributes = [0x3098, minimum_gles, 0x3038];
        let context = ltw_create(display, config, ptr::null_mut(), context_attributes.as_ptr());
        if context.is_null() {
            let detail = oengine_egl_error(get_error, "LTW eglCreateContext");
            let _ = destroy_surface(display, pbuffer);
            let _ = terminate(display);
            return Err(detail);
        }
        if ltw_make_current(display, pbuffer, pbuffer, context) == 0 {
            let detail = oengine_egl_error(get_error, "LTW eglMakeCurrent(pbuffer)");
            let _ = ltw_destroy(display, context);
            let _ = destroy_surface(display, pbuffer);
            let _ = terminate(display);
            return Err(detail);
        }
        let get_string: OEngineGlGetString = match oengine_symbol(gles_library, b"glGetString\0") {
            Ok(value) => value,
            Err(error) => {
                let _ = ltw_make_current(display, ptr::null_mut(), ptr::null_mut(), ptr::null_mut());
                let _ = ltw_destroy(display, context);
                let _ = destroy_surface(display, pbuffer);
                let _ = terminate(display);
                return Err(error);
            }
        };
        let version_ptr = get_string(0x1F02);
        let renderer_ptr = get_string(0x1F01);
        if version_ptr.is_null() || renderer_ptr.is_null() {
            let _ = ltw_make_current(display, ptr::null_mut(), ptr::null_mut(), ptr::null_mut());
            let _ = ltw_destroy(display, context);
            let _ = destroy_surface(display, pbuffer);
            let _ = terminate(display);
            return Err("GLES driver returned no version or renderer for the LTW context.".to_owned());
        }
        let version = CStr::from_ptr(version_ptr as *const c_char).to_string_lossy().into_owned();
        let renderer = CStr::from_ptr(renderer_ptr as *const c_char).to_string_lossy().into_owned();
        if !version.contains("OpenGL ES 3") {
            let _ = ltw_make_current(display, ptr::null_mut(), ptr::null_mut(), ptr::null_mut());
            let _ = ltw_destroy(display, context);
            let _ = destroy_surface(display, pbuffer);
            let _ = terminate(display);
            return Err(format!("LTW requires GLES 3; the active driver reported {version}."));
        }
        let viewport = libc::dlsym(gles_library, b"glViewport\0".as_ptr() as *const c_char);
        Ok(OEngineLtwEglSession {
            egl_library, gles_library, display, config, context, pbuffer,
            window_surface: ptr::null_mut(), native_window: ptr::null_mut(), egl_destroy_surface: destroy_surface,
            egl_terminate: terminate, egl_get_error: get_error, egl_swap_buffers: swap_buffers,
            ltw_create_context: ltw_create, ltw_destroy_context: ltw_destroy, ltw_make_current,
            gl_viewport: if viewport.is_null() { None } else { Some(std::mem::transmute_copy(&viewport)) },
            owner_thread: std::thread::current().id(), version, renderer,
        })
    })();

    match setup {
        Ok(session) => {
            set_ltw_egl_error(String::new());
            let handle = Box::into_raw(Box::new(session)) as jlong;
            ACTIVE_LTW_EGL_SESSION.store(handle as usize, Ordering::Release);
            handle
        }
        Err(error) => {
            libc::dlclose(gles_library);
            libc::dlclose(egl_library);
            set_ltw_egl_error(error);
            0
        }
    }
}

unsafe fn with_ltw_egl_session<'a>(handle: jlong) -> Result<&'a mut OEngineLtwEglSession, String> {
    if handle == 0 { return Err("LTW EGL session handle is null.".to_owned()); }
    let session = &mut *(handle as *mut OEngineLtwEglSession);
    if session.owner_thread != std::thread::current().id() {
        return Err("LTW EGL operation called from a thread other than its creating game thread.".to_owned());
    }
    Ok(session)
}

/// Return the active LTW EGL session's share handles to the Android GLFW backend.
/// This must be called on the game thread that created the session.
#[no_mangle]
pub unsafe extern "C" fn oengine_ltw_egl_active_share_info(
    output: *mut OEngineLtwEglShareInfo,
) -> u32 {
    let handle = ACTIVE_LTW_EGL_SESSION.load(Ordering::Acquire);
    if handle == 0 {
        set_ltw_egl_error("No active LTW EGL session is registered.".to_owned());
        return 0;
    }
    oengine_ltw_egl_share_info(handle as u64, output)
}

/// Expose the active LTW-tracked EGL context to a native GLFW adapter for context sharing.
#[no_mangle]
pub unsafe extern "C" fn oengine_ltw_egl_share_info(
    handle: u64,
    output: *mut OEngineLtwEglShareInfo,
) -> u32 {
    if output.is_null() {
        set_ltw_egl_error("LTW EGL share-info output pointer is null.".to_owned());
        return 0;
    }
    let session = match with_ltw_egl_session(handle as jlong) {
        Ok(session) => session,
        Err(error) => {
            set_ltw_egl_error(error);
            return 0;
        }
    };
    *output = OEngineLtwEglShareInfo {
        display: session.display,
        config: session.config,
        context: session.context,
        pbuffer: session.pbuffer,
        window_surface: session.window_surface,
        create_context: session.ltw_create_context as *const () as *mut c_void,
        make_current: session.ltw_make_current as *const () as *mut c_void,
        destroy_context: session.ltw_destroy_context as *const () as *mut c_void,
        destroy_surface: session.egl_destroy_surface as *const () as *mut c_void,
        swap_buffers: session.egl_swap_buffers as *const () as *mut c_void,
    };
    1
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeAttachLtwWindow(
    _raw_env: *mut RawJNIEnv, _receiver: jobject, handle: jlong, window: jlong, width: jint, height: jint,
) -> jboolean {
    let result = (|| -> Result<(), String> {
        if window == 0 || width <= 0 || height <= 0 { return Err("Invalid ANativeWindow or dimensions.".to_owned()); }
        let session = with_ltw_egl_session(handle)?;
        attach_ltw_window_inner(session, window as *mut c_void, width, height)
    })();
    match result {
        Ok(()) => 1,
        Err(error) => { set_ltw_egl_error(error); 0 }
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeDetachLtwWindow(
    _raw_env: *mut RawJNIEnv, _receiver: jobject, handle: jlong,
) -> jboolean {
    let result = (|| -> Result<(), String> {
        let session = with_ltw_egl_session(handle)?;
        detach_ltw_window_inner(session)
    })();
    match result { Ok(()) => 1, Err(error) => { set_ltw_egl_error(error); 0 } }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeQueueLtwWindow(
    _raw_env: *mut RawJNIEnv, _receiver: jobject, handle: jlong, window: jlong, width: jint, height: jint,
) -> jboolean {
    if handle == 0 || ACTIVE_LTW_EGL_SESSION.load(Ordering::Acquire) != handle as usize {
        set_ltw_egl_error("Queued Surface event references a stale LTW session.".to_owned());
        return 0;
    }
    if window != 0 && (width <= 0 || height <= 0) {
        set_ltw_egl_error("Queued Surface event has invalid dimensions.".to_owned());
        return 0;
    }
    if oengine_ltw_egl_queue_window(window as *mut c_void, width, height) == 1 { 1 } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeProcessPendingLtwWindow(
    _raw_env: *mut RawJNIEnv, _receiver: jobject,
) -> jboolean {
    if oengine_ltw_egl_process_pending_window() == 1 { 1 } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeSwapLtwBuffers(
    _raw_env: *mut RawJNIEnv, _receiver: jobject, handle: jlong,
) -> jboolean {
    match with_ltw_egl_session(handle) {
        Ok(session) if !session.window_surface.is_null() => {
            if (session.egl_swap_buffers)(session.display, session.window_surface) != 0 { 1 }
            else { set_ltw_egl_error(oengine_egl_error(session.egl_get_error, "eglSwapBuffers")); 0 }
        }
        Ok(_) => { set_ltw_egl_error("No LTW window surface is attached.".to_owned()); 0 }
        Err(error) => { set_ltw_egl_error(error); 0 }
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeLtwEglInfo(
    raw_env: *mut RawJNIEnv, _receiver: jobject, handle: jlong,
) -> jstring {
    if raw_env.is_null() { return ptr::null_mut(); }
    let mut env = match JNIEnv::from_raw(raw_env) { Ok(value) => value, Err(_) => return ptr::null_mut() };
    let detail = match with_ltw_egl_session(handle) {
        Ok(session) => format!("{} • {}", session.version, session.renderer),
        Err(error) => error,
    };
    env.new_string(detail).map(|value| value.into_raw()).unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeLtwEglError(
    raw_env: *mut RawJNIEnv, _receiver: jobject,
) -> jstring {
    if raw_env.is_null() { return ptr::null_mut(); }
    let mut env = match JNIEnv::from_raw(raw_env) { Ok(value) => value, Err(_) => return ptr::null_mut() };
    let detail = LTW_EGL_ERROR.get_or_init(|| Mutex::new(String::new()))
        .lock().map(|value| value.clone()).unwrap_or_else(|_| "LTW EGL diagnostic lock is poisoned".to_owned());
    env.new_string(detail).map(|value| value.into_raw()).unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeDestroyLtwEglSession(
    _raw_env: *mut RawJNIEnv, _receiver: jobject, handle: jlong,
) {
    if handle == 0 { return; }
    let mut session = Box::from_raw(handle as *mut OEngineLtwEglSession);
    if session.owner_thread != std::thread::current().id() {
        // Do not destroy EGL objects from the wrong thread. The process owns this
        // session and will reclaim it on process exit if the owner thread is gone.
        std::mem::forget(session);
        set_ltw_egl_error("Refused to destroy the LTW EGL session from a non-owner thread.".to_owned());
        return;
    }
    if ACTIVE_LTW_EGL_SESSION.load(Ordering::Acquire) == handle as usize {
        ACTIVE_LTW_EGL_SESSION.store(0, Ordering::Release);
    }
    destroy_ltw_egl_session(&mut session);
}


#[cfg(test)]
mod native_input_tests {
    use super::*;

    fn event(device: i32, action: i32, code: i32, x: f32) -> OEngineNativeInputEvent {
        OEngineNativeInputEvent {
            device, action, code, x, y: 0.0, value: 1.0,
            timestamp_nanos: 1, modifiers: 0, codepoint: 0, scan_code: 0, reserved: 0,
        }
    }

    #[test]
    fn native_input_queue_preserves_order_when_coalescing_motion() {
        let mut queue = OEngineNativeInputQueue::default();
        assert!(queue.enqueue(event(0, 3, 7, 1.0)));
        assert!(queue.enqueue(event(1, 0, 87, 0.0)));
        assert!(queue.enqueue(event(0, 3, 7, 2.0)));
        let mut output = [OEngineNativeInputEvent::default(); 8];
        let count = queue.drain(&mut output);
        assert_eq!(count, 2);
        assert_eq!(output[0].x, 2.0);
        assert_eq!(output[0].action, 3);
        assert_eq!(output[1].action, 0);
    }

    #[test]
    fn native_input_queue_releases_every_held_control_after_overflow() {
        let mut queue = OEngineNativeInputQueue::default();
        for code in 0..INPUT_QUEUE_CAPACITY as i32 {
            assert!(queue.enqueue(event(1, 0, code, 0.0)));
        }
        assert!(!queue.enqueue(event(1, 0, 900, 0.0)));
        assert!(queue.reset_required);
        let mut output = [OEngineNativeInputEvent::default(); INPUT_MAX_TRACKED_CONTROLS];
        let count = queue.drain(&mut output);
        assert_eq!(count, INPUT_QUEUE_CAPACITY + 1);
        assert!(output[..count].iter().all(|item| item.action == 1));
        assert!(!queue.reset_required);
        assert!(queue.events.is_empty());
        assert!(queue.pressed.is_empty());
    }

    #[test]
    fn failed_up_transition_keeps_control_in_reset_set() {
        let mut queue = OEngineNativeInputQueue::default();
        for code in 0..INPUT_QUEUE_CAPACITY as i32 {
            assert!(queue.enqueue(event(1, 0, code, 0.0)));
        }
        assert!(!queue.enqueue(event(1, 1, 0, 0.0)));
        let mut output = [OEngineNativeInputEvent::default(); INPUT_MAX_TRACKED_CONTROLS];
        let count = queue.drain(&mut output);
        assert_eq!(count, INPUT_QUEUE_CAPACITY);
        assert!(output[..count].iter().any(|item| item.code == 0 && item.action == 1));
    }

    #[test]
    fn duplicate_down_and_unmatched_up_are_ignored() {
        let mut queue = OEngineNativeInputQueue::default();
        assert!(queue.enqueue(event(1, 0, 87, 0.0)));
        assert!(queue.enqueue(event(1, 0, 87, 0.0)));
        assert!(queue.enqueue(event(1, 1, 99, 0.0)));
        assert_eq!(queue.events.len(), 1);
        assert_eq!(queue.pressed.len(), 1);
    }

    #[test]
    fn reset_waits_until_output_can_hold_every_release() {
        let mut queue = OEngineNativeInputQueue::default();
        for code in 0..INPUT_QUEUE_CAPACITY as i32 {
            assert!(queue.enqueue(event(1, 0, code, 0.0)));
        }
        assert!(!queue.enqueue(event(1, 0, 901, 0.0)));
        let mut small = [OEngineNativeInputEvent::default(); 4];
        assert_eq!(queue.drain(&mut small), 0);
        assert!(queue.reset_required);
        assert_eq!(queue.pressed.len(), INPUT_QUEUE_CAPACITY + 1);
    }
}
