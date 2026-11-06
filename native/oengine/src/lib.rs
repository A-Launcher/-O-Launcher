//! O Engine native core. Rust owns the LWJGL native-loader hook.
//! Android UI and lifecycle remain in Kotlin.

use jni::objects::{JClass, JObject, JObjectArray, JString, JValue};
use jni::sys::{jboolean, jint, jlong, jobject, jstring, JNIEnv as RawJNIEnv};
use jni::{JNIEnv, NativeMethod};
use libc::{c_char, c_void};
use std::ffi::{CStr, CString};
use std::ptr;
use std::sync::{Mutex, OnceLock, RwLock};
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

fn hook_config() -> &'static RwLock<Option<HookConfig>> {
    HOOK_CONFIG.get_or_init(|| RwLock::new(None))
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
    if raw_env.is_null() || target_class_name.is_null() || selected_alias.is_null() || renderer_handle == 0 {
        return 0;
    }

    let mut android_env = match JNIEnv::from_raw(raw_env) {
        Ok(value) => value,
        Err(_) => return 0,
    };
    let class_name: String = match android_env.get_string(&JString::from_raw(target_class_name)) {
        Ok(value) => value.into(),
        Err(_) => return 0,
    };
    let alias: String = match android_env.get_string(&JString::from_raw(selected_alias)) {
        Ok(value) => value.into(),
        Err(_) => return 0,
    };
    if class_name.len() > 512
        || !class_name.split('.').all(|part| !part.is_empty() && part.chars().all(|c| c.is_ascii_alphanumeric() || c == '_'))
        || alias.is_empty()
        || alias.len() > 128
        || !alias.starts_with("lib")
        || !alias.ends_with(".so")
        || alias.contains('/')
        || alias.as_bytes().contains(&0)
    {
        return 0;
    }
    let alias = match CString::new(alias) {
        Ok(value) => value,
        Err(_) => return 0,
    };

    let creator = HOTSPOT_CREATOR_THREAD.get()
        .and_then(|state| state.lock().ok())
        .and_then(|guard| guard.clone());
    if creator.as_ref() != Some(&std::thread::current().id()) {
        return 0;
    }
    let hotspot_env = HOTSPOT_ENV.get()
        .and_then(|state| state.lock().ok())
        .and_then(|guard| *guard);
    let Some(hotspot_env) = hotspot_env else {
        return 0;
    };
    let mut env = match JNIEnv::from_raw(hotspot_env as *mut RawJNIEnv) {
        Ok(value) => value,
        Err(_) => return 0,
    };

    let loader = match env.call_static_method(
        "java/lang/ClassLoader",
        "getSystemClassLoader",
        "()Ljava/lang/ClassLoader;",
        &[],
    ) {
        Ok(value) => match value.l() {
            Ok(value) if !value.is_null() => value,
            _ => return 0,
        },
        Err(_) => {
            if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
            return 0;
        }
    };
    let java_name = match env.new_string(&class_name) {
        Ok(value) => value,
        Err(_) => {
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
            _ => return 0,
        },
        Err(_) => {
            if env.exception_check().unwrap_or(false) { let _ = env.exception_clear(); }
            return 0;
        }
    };
    let class = JClass::from(class_object);

    // Hold the write lock through registration so the redirect becomes visible
    // atomically with the native method. The hook itself only takes a read lock.
    let mut guard = match hook_config().write() {
        Ok(value) => value,
        Err(_) => return 0,
    };
    if let Some(existing) = guard.as_ref() {
        if existing.selected_alias != alias || existing.renderer_handle != renderer_handle as usize {
            return 0;
        }
    }

    let methods = [NativeMethod {
        name: "ndlopen".into(),
        sig: "(JI)J".into(),
        fn_ptr: lwjgl_ndlopen_hook as *mut c_void,
    }];
    if env.register_native_methods(&class, &methods).is_err() {
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
    1
}}

#[cfg(test)]
mod tests {
    use super::*;

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
