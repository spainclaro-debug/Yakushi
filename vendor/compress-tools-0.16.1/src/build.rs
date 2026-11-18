// Copyright (C) 2019-2021 O.S. Systems Software LTDA
//
// SPDX-License-Identifier: MIT OR Apache-2.0

fn main() {
    find_libarchive();
}

#[cfg(not(target_env = "msvc"))]
fn find_libarchive() {
    const MACOS_HOMEBREW_LIBARCHIVE_PATH: &str = "/opt/homebrew/opt/libarchive/lib/pkgconfig/";

    if cfg!(target_os = "macos")
        && pkg_config::Config::new()
            .atleast_version("3.2.0")
            .probe("libarchive")
            .is_err()
        && std::path::Path::new(MACOS_HOMEBREW_LIBARCHIVE_PATH).exists()
    {
        // on OSX brew doesn't install libarchive in the default path...
        // try that workaround as it's a pain providing this in the env e.g.
        // for vs code usage.
        // todo should add to current one and set afterwards to current value!
        std::env::set_var("PKG_CONFIG_PATH", MACOS_HOMEBREW_LIBARCHIVE_PATH);
    }

    // Termux ships the compression libs as shared objects; probe them without
    // requiring a full static-only archive of every codec. On Android the
    // bundled libarchive is compiled without lz4/zstd/xz support and the host
    // pkg-config would otherwise inject x86_64 libraries into the aarch64
    // link, so those probes are skipped there entirely.
    // Build scripts run for the host, so the target OS comes from cargo's env.
    let target_android = std::env::var("CARGO_CFG_TARGET_OS")
        .map(|os| os == "android")
        .unwrap_or(false);
    let probe = |pc_name: &str| {
        if target_android {
            return;
        }
        if let Err(error) = pkg_config::Config::new().probe(pc_name) {
            println!(
                "cargo:warning=optional compression codec {pc_name} not found; skipping ({error})"
            );
        }
    };
    let probe_static = &probe;

    if cfg!(feature = "static_b2") {
        probe_static("libb2");
    }
    if cfg!(feature = "static_lz4") {
        probe_static("liblz4");
    }
    if cfg!(feature = "static_zstd") {
        probe_static("libzstd");
    }
    if cfg!(feature = "static_lzma") {
        probe_static("liblzma");
    }
    if cfg!(feature = "static_bz2") && !target_android {
        // bzip2 upstream (and Ubuntu's libbz2-dev) ships no .pc file, but
        // Homebrew's bzip2 does. Try pkg-config so we pick up search paths on
        // macOS; fall back to a bare link directive when pkg-config has
        // nothing, relying on the system library search path.
        if pkg_config::Config::new().statik(true).probe("bzip2").is_err() {
            println!("cargo:rustc-link-lib=static=bz2");
        }
    }
    if cfg!(feature = "static_z") {
        probe_static("zlib");
    }
    if cfg!(feature = "static_xml2") {
        probe_static("libxml-2.0");
    }

    pkg_config::Config::new()
        .atleast_version("3.2.0")
        .probe("libarchive")
        .expect("Unable to find libarchive");
}

#[cfg(target_env = "msvc")]
fn find_libarchive() {
    vcpkg::Config::new()
        .find_package("libarchive")
        .expect("Unable to find libarchive");

    println!("cargo:rustc-link-lib=static=archive");
    if cfg!(feature = "win_user32") {
        println!("cargo:rustc-link-lib=User32");
    }
    if cfg!(feature = "win_crypt32") {
        println!("cargo:rustc-link-lib=Crypt32");
    }
    if cfg!(feature = "win_advapi32") {
        println!("cargo:rustc-link-lib=advapi32");
    }
    if cfg!(feature = "win_xmllite") {
        println!("cargo:rustc-link-lib=xmllite");
    }
}
