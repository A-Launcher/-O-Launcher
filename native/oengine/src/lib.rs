//! O Engine native core. Rust owns the LWJGL native-loader hook.
//! Android UI and lifecycle remain in Kotlin.

use jni::objects::{JClass, JObjectArray, JString};
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
static JVM_INSTANCE: OnceLock<Mutex<Option<usize>>> = OnceLock::new();

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
    let mut hot_spot_env = match JNIEnv::from_raw(vm_env as *mut jni::sys::JNIEnv) {
        Ok(value) => value,
        Err(error) => return response!("created_env_error", &format!("VM created but JNIEnv could not be wrapped: {error}")),
    };
    match hot_spot_env.find_class("java/lang/String") {
        Ok(_) => response!("vm_created", "HotSpot started and java.lang.String resolved successfully"),
        Err(error) => {
            let _ = hot_spot_env.exception_check().and_then(|pending| {
                if pending { hot_spot_env.exception_clear() } else { Ok(()) }
            });
            response!("core_class_error", &format!("VM started but java.lang.String lookup failed: {error}"))
        }
    }
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
