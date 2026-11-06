//! O Engine native core. Rust owns the LWJGL native-loader hook.
//! Android UI and lifecycle remain in Kotlin.

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jobject, jstring, JNIEnv as RawJNIEnv};
use jni::{JNIEnv, NativeMethod};
use libc::{c_char, c_void};
use std::ffi::{CStr, CString};
use std::ptr;
use std::sync::{Mutex, OnceLock, RwLock};

const ABI_VERSION: u32 = 1;

#[derive(Clone)]
struct HookConfig {
    selected_alias: CString,
    renderer_handle: usize,
}

static HOOK_CONFIG: OnceLock<RwLock<Option<HookConfig>>> = OnceLock::new();
static JVM_LIBRARY: OnceLock<Mutex<Option<(CString, usize)>>> = OnceLock::new();

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
    let mut response = |status: &str, detail: &str| {
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
        let handle = libc::dlopen(requested.as_ptr(), libc::RTLD_NOW | libc::RTLD_LOCAL);
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

/// Installs the upstream LWJGL 3 JNI method replacement on the exact class
/// resolved by the caller's game class loader. It does not find/load game classes itself.
#[no_mangle]
pub unsafe extern "system" fn Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeInstallLwjglHook(
    raw_env: *mut RawJNIEnv,
    _receiver: jobject,
    target_class: jni::sys::jclass,
    selected_alias: jstring,
    renderer_handle: jlong,
) -> jboolean {
    if raw_env.is_null() || target_class.is_null() || selected_alias.is_null() || renderer_handle == 0 {
        return 0;
    }

    let mut env = match JNIEnv::from_raw(raw_env) {
        Ok(value) => value,
        Err(_) => return 0,
    };
    let alias_string = JString::from_raw(selected_alias);
    let alias: String = match env.get_string(&alias_string) {
        Ok(value) => value.into(),
        Err(_) => return 0,
    };
    if alias.is_empty() || alias.as_bytes().contains(&0) {
        return 0;
    }
    let alias = match CString::new(alias) {
        Ok(value) => value,
        Err(_) => return 0,
    };

    // A process can host only one active renderer provider. Never silently replace it.
    {
        let mut guard = match hook_config().write() {
            Ok(value) => value,
            Err(_) => return 0,
        };
        if let Some(existing) = guard.as_ref() {
            if existing.selected_alias != alias || existing.renderer_handle != renderer_handle as usize {
                return 0;
            }
        } else {
            *guard = Some(HookConfig {
                selected_alias: alias,
                renderer_handle: renderer_handle as usize,
            });
        }
    }

    let class = JClass::from_raw(target_class);
    let methods = [NativeMethod {
        name: "ndlopen".into(),
        sig: "(JI)J".into(),
        fn_ptr: lwjgl_ndlopen_hook as *mut c_void,
    }];
    match env.register_native_methods(&class, &methods) {
        Ok(()) => 1,
        Err(_) => 0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn exact_renderer_alias_is_redirected() {
        assert!(is_redirect_candidate(b"libGL.so", b"libGL.so"));
    }

    #[test]
    fn unrelated_libraries_keep_normal_loader_path() {
        assert!(!is_redirect_candidate(b"libvulkan.so", b"libGL.so"));
        assert!(!is_redirect_candidate(b"/system/lib/libGL.so", b"libGL.so"));
    }

    #[test]
    fn empty_names_are_never_redirected() {
        assert!(!is_redirect_candidate(b"", b"libGL.so"));
        assert!(!is_redirect_candidate(b"libGL.so", b""));
    }

    #[test]
    fn abi_version_is_stable() {
        assert_eq!(oengine_native_abi_version(), ABI_VERSION);
    }
}
