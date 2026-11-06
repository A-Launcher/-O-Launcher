//! Bounded Android ELF dependency preloader, adapted from the dependency-walk
//! strategy used by MojoLauncher. This is O Engine Rust code, not copied C source.
//! Handles are retained only for the duration of the intercepted dlopen call.

use goblin::elf::Elf;
use std::collections::HashSet;
use std::ffi::{CStr, CString};
use std::fs;
use std::path::{Path, PathBuf};

const MAX_ELF_BYTES: u64 = 256 * 1024 * 1024;
const MAX_DEPENDENCIES: usize = 512;
const MAX_DEPTH: usize = 64;

pub(crate) struct PreloadedDependencies {
    handles: Vec<usize>,
}

impl Drop for PreloadedDependencies {
    fn drop(&mut self) {
        for handle in self.handles.drain(..).rev() {
            unsafe { libc::dlclose(handle as *mut libc::c_void); }
        }
    }
}

fn library_directories() -> Vec<PathBuf> {
    std::env::var_os("LD_LIBRARY_PATH")
        .into_iter()
        .flat_map(|value| std::env::split_paths(&value).collect::<Vec<_>>())
        .filter(|path| path.is_dir())
        .collect()
}

fn locate_library(name: &str, parent: &Path, directories: &[PathBuf]) -> Option<PathBuf> {
    let requested = Path::new(name);
    if requested.is_absolute() && requested.is_file() {
        return requested.canonicalize().ok();
    }
    if requested.components().count() > 1 {
        let relative = parent.join(requested);
        if relative.is_file() {
            return relative.canonicalize().ok();
        }
    }
    directories.iter().map(|dir| dir.join(name))
        .find(|candidate| candidate.is_file())
        .and_then(|candidate| candidate.canonicalize().ok())
}

fn already_loaded(name: &str) -> bool {
    let Ok(name) = CString::new(name) else { return false; };
    unsafe {
        let handle = libc::dlopen(name.as_ptr(), libc::RTLD_NOW | libc::RTLD_NOLOAD);
        if handle.is_null() { false } else {
            libc::dlclose(handle);
            true
        }
    }
}

fn preload_recursive(
    path: &Path,
    depth: usize,
    directories: &[PathBuf],
    visited: &mut HashSet<PathBuf>,
    handles: &mut Vec<usize>,
) -> Result<(), String> {
    if depth > MAX_DEPTH {
        return Err(format!("ELF dependency depth exceeds {MAX_DEPTH}"));
    }
    let canonical = path.canonicalize().map_err(|e| format!("Cannot canonicalize {}: {e}", path.display()))?;
    if visited.contains(&canonical) { return Ok(()); }
    if visited.len() >= MAX_DEPENDENCIES {
        return Err(format!("ELF dependency count exceeds {MAX_DEPENDENCIES}"));
    }
    visited.insert(canonical.clone());

    let metadata = fs::metadata(&canonical).map_err(|e| format!("Cannot stat {}: {e}", canonical.display()))?;
    if metadata.len() == 0 || metadata.len() > MAX_ELF_BYTES {
        return Err(format!("ELF file size is outside the accepted range: {}", canonical.display()));
    }
    let bytes = fs::read(&canonical).map_err(|e| format!("Cannot read {}: {e}", canonical.display()))?;
    let elf = Elf::parse(&bytes).map_err(|e| format!("Invalid ELF {}: {e}", canonical.display()))?;

    for dependency in elf.libraries {
        if already_loaded(dependency) { continue; }
        let dependency_path = locate_library(dependency, canonical.parent().unwrap_or(Path::new("/")), directories)
            .ok_or_else(|| format!("Cannot locate ELF dependency {dependency} required by {}", canonical.display()))?;
        preload_recursive(&dependency_path, depth + 1, directories, visited, handles)?;
    }

    let path_string = CString::new(canonical.to_string_lossy().as_bytes())
        .map_err(|_| "ELF path contains a NUL byte".to_owned())?;
    unsafe {
        let handle = libc::dlopen(path_string.as_ptr(), libc::RTLD_NOW | libc::RTLD_GLOBAL);
        if handle.is_null() {
            let error = libc::dlerror();
            let detail = if error.is_null() { "dlopen failed".to_owned() }
                else { CStr::from_ptr(error).to_string_lossy().into_owned() };
            return Err(format!("Cannot preload {}: {detail}", canonical.display()));
        }
        handles.push(handle as usize);
    }
    Ok(())
}


/// Resolve a soname against LD_LIBRARY_PATH and preload its DT_NEEDED closure.
pub(crate) fn preload_named_library(name: &str) -> Result<PreloadedDependencies, String> {
    let directories = library_directories();
    let target = locate_library(name, Path::new("/"), &directories)
        .ok_or_else(|| format!("Cannot resolve native library {name} through LD_LIBRARY_PATH"))?;
    let mut visited = HashSet::new();
    let mut handles = Vec::new();
    if let Err(error) = preload_recursive(&target, 0, &directories, &mut visited, &mut handles) {
        for handle in handles.drain(..).rev() {
            unsafe { libc::dlclose(handle as *mut libc::c_void); }
        }
        return Err(error);
    }
    Ok(PreloadedDependencies { handles })
}

/// Preload the target's DT_NEEDED closure with global visibility. The returned guard
/// must stay alive until the intercepted original native-library load returns.
pub(crate) fn preload_dependencies(target: &Path) -> Result<PreloadedDependencies, String> {
    let canonical = target.canonicalize().map_err(|e| format!("Cannot resolve {}: {e}", target.display()))?;
    let directories = library_directories();
    let mut visited = HashSet::new();
    let mut handles = Vec::new();
    if let Err(error) = preload_recursive(&canonical, 0, &directories, &mut visited, &mut handles) {
        for handle in handles.drain(..).rev() {
            unsafe { libc::dlclose(handle as *mut libc::c_void); }
        }
        return Err(error);
    }
    Ok(PreloadedDependencies { handles })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn library_lookup_supports_absolute_paths() {
        let dir = std::env::temp_dir().join(format!("oengine-elf-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let file = dir.join("libsample.so");
        std::fs::write(&file, b"fixture").unwrap();
        let found = locate_library(file.to_str().unwrap(), Path::new("/"), &[]);
        assert_eq!(found, file.canonicalize().ok());
        let _ = std::fs::remove_file(file);
        let _ = std::fs::remove_dir(dir);
    }

    #[test]
    fn library_lookup_searches_ld_path_directories() {
        let dir = std::env::temp_dir().join(format!("oengine-elf-path-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let file = dir.join("libsample.so");
        std::fs::write(&file, b"fixture").unwrap();
        let found = locate_library("libsample.so", Path::new("/"), &[dir.clone()]);
        assert_eq!(found, file.canonicalize().ok());
        let _ = std::fs::remove_file(file);
        let _ = std::fs::remove_dir(dir);
    }

    #[test]
    fn reject_nonexistent_dependencies() {
        assert!(locate_library("lib-not-present-oengine.so", Path::new("/"), &[]).is_none());
    }
}
