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
static RENDERER_HOOK_ERROR: OnceLock<Mutex<String>> = OnceLock::new();
static ANDROID_LIBRARY: OnceLock<usize> = OnceLock::new();

type OEngineNativeWindowFromSurface = unsafe extern "system" fn(*mut RawJNIEnv, jobject) -> *mut c_void;
type OEngineNativeWindowSetBuffersGeometry = unsafe extern "C" fn(*mut c_void, i32, i32, i32) -> i32;
type OEngineNativeWindowRelease = unsafe extern "C" fn(*mut c_void);

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
    let library = *ANDROID_LIBRARY.get_or_init(|| {
        libc::dlopen(b"libandroid.so\\0".as_ptr() as *const c_char, libc::RTLD_NOW | libc::RTLD_LOCAL) as usize
    }) as *mut c_void;
    if library.is_null() {
        return 0;
    }
    let from_surface = libc::dlsym(library, b"ANativeWindow_fromSurface\\0".as_ptr() as *const c_char);
    let set_geometry = libc::dlsym(library, b"ANativeWindow_setBuffersGeometry\\0".as_ptr() as *const c_char);
    let library = *ANDROID_LIBRARY.get_or_init(|| {
        libc::dlopen(b"libandroid.so\\0".as_ptr() as *const c_char, libc::RTLD_NOW | libc::RTLD_LOCAL) as usize
    }) as *mut c_void;
    let release = if library.is_null() { ptr::null_mut() } else {
        libc::dlsym(library, b"ANativeWindow_release\\0".as_ptr() as *const c_char)
    };
    if !release.is_null() {
        let release: OEngineNativeWindowRelease = std::mem::transmute(release);
        release(handle as *mut c_void);
    }
}

