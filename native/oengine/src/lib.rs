//! O Engine native core. This crate deliberately exposes a narrow C ABI.
//! JNI registration and renderer interception are added only after ABI/device validation.

use std::ffi::{c_char, c_void, CStr};
use std::ptr;

const ABI_VERSION: u32 = 1;

/// Returns the version of the O Engine native C ABI.
#[no_mangle]
pub extern "C" fn oengine_native_abi_version() -> u32 {
    ABI_VERSION
}

/// Classifies whether an LWJGL native-load request exactly matches the selected
/// renderer alias. This is policy only: it never loads libraries or changes handles.
///
/// Return values: 1 = exact redirect candidate, 0 = ordinary library.
#[no_mangle]
pub unsafe extern "C" fn oengine_lwjgl_is_redirect_candidate(
    requested_name: *const c_char,
    selected_alias: *const c_char,
) -> i32 {
    if requested_name.is_null() || selected_alias.is_null() {
        return 0;
    }
    let requested = CStr::from_ptr(requested_name);
    let alias = CStr::from_ptr(selected_alias);
    if requested.to_bytes().is_empty() || alias.to_bytes().is_empty() {
        return 0;
    }
    if requested.to_bytes() == alias.to_bytes() { 1 } else { 0 }
}

/// Returns a renderer handle only for an exact alias match; otherwise null.
/// The caller owns handle lifetime and normal dlopen fallback behavior.
#[no_mangle]
pub unsafe extern "C" fn oengine_lwjgl_redirect_handle(
    requested_name: *const c_char,
    selected_alias: *const c_char,
    renderer_handle: *mut c_void,
) -> *mut c_void {
    if oengine_lwjgl_is_redirect_candidate(requested_name, selected_alias) == 1 {
        renderer_handle
    } else {
        ptr::null_mut()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::CString;

    #[test]
    fn exact_alias_is_redirect_candidate() {
        let request = CString::new("libGL.so").unwrap();
        let alias = CString::new("libGL.so").unwrap();
        assert_eq!(unsafe { oengine_lwjgl_is_redirect_candidate(request.as_ptr(), alias.as_ptr()) }, 1);
    }

    #[test]
    fn non_matching_library_is_not_redirected() {
        let request = CString::new("libvulkan.so").unwrap();
        let alias = CString::new("libGL.so").unwrap();
        assert_eq!(unsafe { oengine_lwjgl_is_redirect_candidate(request.as_ptr(), alias.as_ptr()) }, 0);
    }

    #[test]
    fn null_or_empty_names_are_rejected() {
        let alias = CString::new("libGL.so").unwrap();
        let empty = CString::new("").unwrap();
        assert_eq!(unsafe { oengine_lwjgl_is_redirect_candidate(ptr::null(), alias.as_ptr()) }, 0);
        assert_eq!(unsafe { oengine_lwjgl_is_redirect_candidate(empty.as_ptr(), alias.as_ptr()) }, 0);
    }

    #[test]
    fn handle_is_returned_only_for_exact_alias() {
        let request = CString::new("libGL.so").unwrap();
        let alias = CString::new("libGL.so").unwrap();
        let handle = 0x1234usize as *mut c_void;
        assert_eq!(unsafe { oengine_lwjgl_redirect_handle(request.as_ptr(), alias.as_ptr(), handle) }, handle);
    }
}
