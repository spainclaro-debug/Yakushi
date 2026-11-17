//! In-memory plugin loading for Android.
//!
//! Android 10+ enforces W^X for app-writable storage: `dlopen()` rejects a
//! shared object that lives in the app's data directory (SELinux neverallow for
//! `app_data_file:file execute`). Tanoshi extensions are downloaded into the
//! app's writable plugin directory, so the extension worker copies the library
//! bytes into an anonymous memfd, maps the memfd executable and asks the dynamic
//! linker to load it from that file descriptor with
//! `android_dlopen_ext(ANDROID_DLEXT_USE_LIBRARY_FD)`.
//!
//! This is the same class of technique used by other on-device runtimes to load
//! code from outside `nativeLibraryDir`; it keeps the app targeting a modern SDK
//! (no targetSdk downgrade). Bundled plugins are still loaded normally from
//! `nativeLibraryDir`, this path is only used when the normal loader fails.
//!
//! The loader is best-effort: if the memfd path fails for any reason the worker
//! falls back to a regular `dlopen()` and reports both errors.

use std::ffi::{c_char, c_int, c_long, c_void, CStr};
use std::io;
use std::path::Path;
use std::ptr;

use anyhow::{bail, Context, Result};
use log::{info, warn};

/// `ANDROID_DLEXT_USE_LIBRARY_FD` from `<android/dlext.h>`.
const ANDROID_DLEXT_USE_LIBRARY_FD: u64 = 0x10;
const RTLD_NOW: c_int = 2;
const RTLD_LOCAL: c_int = 0;
const MFD_CLOEXEC: c_long = 0x0001;
const PROT_READ: c_int = 1;
const PROT_WRITE: c_int = 2;
const PROT_EXEC: c_int = 4;
const MAP_SHARED: c_int = 0x01;
/// Smallest plausible extension; matches the download sanity check in the VM.
const MIN_PLUGIN_BYTES: usize = 256 * 1024;

#[cfg(target_arch = "aarch64")]
const SYS_MEMFD_CREATE: c_long = 279;
#[cfg(target_arch = "arm")]
const SYS_MEMFD_CREATE: c_long = 385;
#[cfg(target_arch = "x86_64")]
const SYS_MEMFD_CREATE: c_long = 319;
#[cfg(target_arch = "x86")]
const SYS_MEMFD_CREATE: c_long = 356;

/// `struct android_dlextinfo` from `<android/dlext.h>` (64-bit layout).
#[repr(C)]
struct AndroidDlextInfo {
    flags: u64,
    reserved_addr: *const c_void,
    reserved_size: usize,
    relro_fd: c_int,
    library_fd: c_int,
    library_fd_offset: i64,
    library_namespace: *const c_void,
}

#[link(name = "dl")]
unsafe extern "C" {
    fn android_dlopen_ext(
        filename: *const c_char,
        flags: c_int,
        ext_info: *const AndroidDlextInfo,
    ) -> *mut c_void;
    fn dlerror() -> *mut c_char;
}

unsafe extern "C" {
    fn syscall(number: c_long, ...) -> c_long;
    fn mmap(
        addr: *mut c_void,
        length: usize,
        prot: c_int,
        flags: c_int,
        fd: c_int,
        offset: i64,
    ) -> *mut c_void;
    fn mprotect(addr: *mut c_void, length: usize, prot: c_int) -> c_int;
    fn ftruncate(fd: c_int, length: i64) -> c_int;
    fn munmap(addr: *mut c_void, length: usize) -> c_int;
    fn close(fd: c_int) -> c_int;
}

/// Load a plugin shared object into the current process.
///
/// On Android this first tries the memfd + `android_dlopen_ext` path so a
/// library can be loaded from the app's writable plugin directory; if that
/// fails it falls back to the regular `dlopen()` (which works for bundled
/// plugins in `nativeLibraryDir`).
pub unsafe fn open_plugin_library(path: &Path) -> Result<libloading::Library> {
    let bytes = std::fs::read(path)
        .with_context(|| format!("failed to read extension {}", path.display()))?;
    if bytes.len() < MIN_PLUGIN_BYTES {
        bail!(
            "extension {} is only {} bytes; refusing to load it",
            path.display(),
            bytes.len()
        );
    }

    match open_from_memfd(&bytes) {
        Ok(library) => {
            info!(
                "loaded extension {} from memory (memfd, {} bytes)",
                path.display(),
                bytes.len()
            );
            Ok(library)
        }
        Err(loader_error) => {
            warn!(
                "in-memory extension loader failed for {}: {loader_error:#}; \
                 falling back to the regular dynamic linker",
                path.display()
            );
            unsafe { libloading::Library::new(path) }.with_context(|| {
                format!(
                    "could not load extension {} (in-memory loader: {loader_error:#})",
                    path.display()
                )
            })
        }
    }
}

unsafe fn open_from_memfd(bytes: &[u8]) -> Result<libloading::Library> {
    #[cfg(not(any(
        target_arch = "aarch64",
        target_arch = "arm",
        target_arch = "x86_64",
        target_arch = "x86"
    )))]
    {
        let _ = bytes;
        bail!("in-memory extension loading is not implemented for this architecture");
    }

    #[cfg(any(
        target_arch = "aarch64",
        target_arch = "arm",
        target_arch = "x86_64",
        target_arch = "x86"
    ))]
    {
        let fd = unsafe { syscall(SYS_MEMFD_CREATE, c"tanoshi-plugin".as_ptr(), MFD_CLOEXEC) }
            as c_int;
        if fd < 0 {
            bail!("memfd_create failed: {}", io::Error::last_os_error());
        }

        let length = bytes.len();
        // The memfd must be sized to the payload before it is mapped, otherwise
        // the first page beyond EOF raises SIGBUS.
        if unsafe { ftruncate(fd, length as i64) } != 0 {
            let error = io::Error::last_os_error();
            unsafe { close(fd) };
            bail!("ftruncate of the extension memfd failed: {error}");
        }
        let mapping = unsafe {
            mmap(
                ptr::null_mut(),
                length,
                PROT_READ | PROT_WRITE,
                MAP_SHARED,
                fd,
                0,
            )
        };
        if mapping as isize == -1 {
            let error = io::Error::last_os_error();
            unsafe { close(fd) };
            bail!("mmap of the extension memfd failed: {error}");
        }

        unsafe { ptr::copy_nonoverlapping(bytes.as_ptr(), mapping.cast::<u8>(), length) };
        // The linker maps the fd itself, but making the mapping executable first
        // surfaces SELinux/policy failures early and mirrors the approach used by
        // other on-device loaders.
        if unsafe { mprotect(mapping, length, PROT_READ | PROT_EXEC) } != 0 {
            warn!(
                "mprotect(PROT_EXEC) on the extension memfd failed: {}; continuing anyway",
                io::Error::last_os_error()
            );
        }

        let info = AndroidDlextInfo {
            flags: ANDROID_DLEXT_USE_LIBRARY_FD,
            reserved_addr: ptr::null(),
            reserved_size: 0,
            relro_fd: 0,
            library_fd: fd,
            library_fd_offset: 0,
            library_namespace: ptr::null(),
        };
        // The name is only used by the linker for diagnostics; the descriptor is
        // the actual source of the library.
        let handle = unsafe {
            android_dlopen_ext(c"/tanoshi-plugin.so".as_ptr(), RTLD_NOW | RTLD_LOCAL, &info)
        };
        if handle.is_null() {
            let message = unsafe { dlerror() };
            let message = if message.is_null() {
                "unknown dynamic linker error".to_string()
            } else {
                CStr::from_ptr(message).to_string_lossy().into_owned()
            };
            unsafe {
                unsafe { munmap(mapping, length) };
                close(fd);
            }
            bail!("android_dlopen_ext failed: {message}");
        }

        unsafe { munmap(mapping, length) };
        // Deliberately leak the descriptor: the loaded library stays mapped for
        // the rest of this (short-lived worker) process.
        Ok(unsafe { libloading::os::unix::Library::from_raw(handle) }.into())
    }
}
