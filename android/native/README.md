# Native binaries for the Tanoshi Android app

The Android app in `android/` does not compile any Rust code. It ships the
Tanoshi server, its `libarchive` dependency and the extension plugins as
prebuilt `arm64-v8a` shared objects inside the APK, and only runs Gradle.

This directory contains the pipeline that produces those binaries. The
checked-in files under `android/app/src/main/jniLibs/arm64-v8a/` were built
with exactly these scripts; re-run them to update Tanoshi or a plugin.

The same upstream Tanoshi source and the same Termux patch set used by the
root package (`patches/`, `vendor/compress-tools-0.16.1/`, `lib/build.sh`)
are reused here, with one extra patch that adapts the server to the Android
app runtime.

---

## What gets built

| Output in `jniLibs/arm64-v8a/` | Built from | Notes |
| --- | --- | --- |
| `libtanoshi.so` | `luigi311/tanoshi` v0.40.0, patched with this repo's Termux patch set + `native/patches/0001-android-app.patch` + `native/patches/0002-android-extension-loader.patch` (+ `android_loader.rs`) | The server binary, **renamed** from `tanoshi` so the package installer extracts it to `nativeLibraryDir`. Since 0.5.0 it also loads user-installed plugins from the writable plugin directory through an in-memory loader. ~35 MB. |
| `libarchive.so` | [libarchive](https://github.com/libarchive/libarchive) 3.8.11 | Minimal shared build: zlib linked in **statically** (no `libz.so` runtime dependency), no OpenSSL, libxml2 or iconv; only libc/libdl remain as `NEEDED` entries; SONAME patched to plain `libarchive.so`. Loaded by `libtanoshi.so` through `LD_LIBRARY_PATH`. |
| `lib<plugin>.so` | [luigi311/tanoshi-extensions](https://github.com/luigi311/tanoshi-extensions) + this repo's ported extensions | Nine plugins: `libmangadex.so`, `libguya.so`, `libmanhwa18cc.so`, `libweebcentral.so`, `libatsumaru.so`, `libqiscans.so`, `libmangaball.so`, `libmadaradex.so`, `libhentainexus.so`. Renamed with the `lib` prefix for the same reason as the server. |

All of these files must be ELF aarch64 shared objects. Android only *executes*
files from `nativeLibraryDir`; the bundled plugins are loaded from there
normally. User-installed plugins live in `files/tanoshi/plugins/` and are
loaded by the server through the in-memory loader described in
[Extension loader](#5-extension-loader-and-publishing-to-the-catalog) (the app
does not execute them itself).

`libtanoshi.so` also embeds the Tanoshi web UI: the wasm bundle from
`android/native/web-dist/` is compiled into the server binary, so the
catalogue/infinite-scroll behaviour and UI changes ship with the server rather
than with the Android app. The dist is rebuilt from source with
`patches/tanoshi-infinite-scroll.patch`,
`patches/tanoshi-web-ui.patch` + `patches/tanoshi-web-ui-settings.patch`
(Delete History, catalogue filter sheet, layout presets, reader tap area),
`patches/tanoshi-web-ui-backup.patch` + `patches/tanoshi-web-ui-backup-settings.patch`
(Backup and Restore page),
`native/patches/0003-web-ui-hide-source-management.patch` (extension
management is app-only) and
`native/patches/0004-web-ui-themes.patch` + `native/patches/theme_settings.rs`
(per-device theme palettes; since 0.9.1 the palette is applied across banners,
icons, the unread badge, page background, headers and settings panels with
contrast-adaptive text computed per light/dark mode, the 0.9.2 Settings list
cells get their own tinted surface + adaptive cell text, 0.13.5 adds
per-chapter download buttons/progress in the chapter list (patch 0007; the old
reader image-save patch 0006 was deleted) and 0.13.6 adds server-shared layout
presets (patch 0008 + copied `layout_presets.rs`), the gapless double-page
reader (patch 0009), 0.13.7 adds manga-page Migrate/Change Library + tabbed
Library (patch 0010), 0.13.8 adds browser-style/desktop-safe Library tabs,
per-device tab display options and inline category rename (patch 0011) and
0.13.9 adds seamless continuous chapters + automatic webtoon layout (patch
0012). Offline chapters are extracted as page images into public
`<external storage>/yakushi/.downloaded/<manga>/<chapter>/` by the server
worker (patch 0005 + app public `download_path`; see step 1).

---

## Prerequisites

A Linux x86_64 build host (the checked-in binaries were produced on Linux
with the Android NDK). The phone needs no Rust toolchain and does not build
these binaries.

- **Android NDK r26 or newer**, with `ANDROID_NDK_HOME` pointing at it.
  r26d / r27 are known-good.
- **Rust 1.99.1** (the version the Termux package pins), installed with
  `rustup`, plus the cross targets:

  ```bash
  rustup target add aarch64-linux-android wasm32-unknown-unknown
  ```

- **Web UI tooling** (only needed when rebuilding the embedded web UI):
  `trunk` **0.22.1**, `wasm-bindgen-cli` **0.2.129**, `sass` **1.106.1** and
  `wasm-opt` (binaryen) **133**, plus the `wasm32-unknown-unknown` target —
  these versions are pinned by the upstream Tanoshi web workspace and are the
  same ones the Termux scripts use (`lib/common.sh`).
- **CMake 3.22+**, `make`, `pkg-config`, `git`, `curl`, `python3`.
- About **10 GB** of free disk space for source checkouts and Cargo targets.

`build_tanoshi.sh` checks the toolchain and prints what is missing.

---

## Environment for cross-compilation

Export this in every shell that builds native code. `API` is the app's
`minSdk` (26).

```bash
export ANDROID_NDK_HOME="$HOME/android-ndk-r26d"          # adjust
export TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
export API=26
export PATH="$TOOLCHAIN/bin:$PATH"

export CC_aarch64_linux_android="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang"
export AR_aarch64_linux_android="$TOOLCHAIN/bin/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang"

# The Termux build needs --allow-multiple-definition; 16 KB page alignment is
# required for Android 15+ devices with 16 KB pages (see "Verifying" below).
export RUSTFLAGS="-Clink-arg=-Wl,--allow-multiple-definition -Clink-arg=-Wl,-z,max-page-size=16384"
```

The `CC_*` and `AR_*` names are written with underscores because a shell
cannot export a variable whose name contains `-`; the `cc` crate accepts
either form.

---

## Pipeline

The scripts to run are:

| Script | Purpose |
| --- | --- |
| `native/build_tanoshi.sh` | Fetch upstream Tanoshi at the pinned version, apply this repo's Termux patch set, the web-UI patches (`patches/tanoshi-web-ui*.patch` + 0003 + copied `theme_settings.rs` + 0004 + 0007 + 0009 + 0010 + 0011 + 0012), the Android app/extension-loader patches (0001 + 0002), the downloads patch (0005) and the server layout-presets patch (0008 + copied `layout_presets.rs`), embed the vendored `native/web-dist/` web UI when the source tree has no `dist/`, and build the `aarch64-linux-android` server binary. |
| `native/build_libarchive.sh` | Cross-build the minimal `libarchive.so` (zlib only) with the NDK CMake toolchain and stage its `pkg-config` file for `compress-tools`. |
| `native/build_extension.sh` | Build one or all exported extension crates (`android/extensions/sources/`) for `aarch64-linux-android` inside the upstream extension workspace, strip them into `android/extensions/plugins/`, and print SHA-256/size + a catalog entry. |
| `native/sync_prebuilt.sh` | Copy the built `tanoshi`, `libarchive.so` and the nine plugins into `android/app/src/main/jniLibs/arm64-v8a/` with the `lib` prefix and validate the ELF files. |

The scripts are self-checking. Typical order: run `native/build_libarchive.sh`
first (its staging prefix is what `compress-tools` links against), then
`native/build_tanoshi.sh`, then `native/sync_prebuilt.sh` from the `android/`
directory. Each script documents its own options (`--help`);
`sync_prebuilt.sh` does not rebuild anything. The subsections below describe
the equivalent manual steps, which is what the scripts automate.

### 1. Tanoshi source and patches

`build_tanoshi.sh` clones upstream and applies the following patch series (see the script for the exact order):

1. The **Termux patch set** from this repository's `patches/` directory
   (`tanoshi-android.patch` plus the two new files `http.rs.new` and
   `extension-build.rs.new` — see `patches/README-patches.txt`). This makes
   the server compile and run with Bionic and private CA roots.
2. **`native/patches/0001-android-app.patch`**, the Android-app-only layer:
   - the listen address is read from the `TANOSHI_BIND` environment variable
     instead of the hardcoded `0.0.0.0` (defaults to `127.0.0.1`);
   - the bundled plugin load directory is taken from
     `TANOSHI_PLUGIN_LOAD_DIR` (the app sets it to `nativeLibraryDir`) while
     the writable plugin directory stays the preferences directory;
   - the extension downloader is disabled (the app's Sources tab manages user
     extensions).
3. **`native/patches/0002-android-extension-loader.patch`** plus
   `native/patches/android_loader.rs`: dual-directory plugin loading (writable
   `files/tanoshi/plugins` first, then `nativeLibraryDir`), the in-memory
   `memfd` + `android_dlopen_ext` loader for plugins outside
   `nativeLibraryDir`, the `.tanoshi-disabled` marker (persistent
   uninstall/disable) and host-library filtering (`libtanoshi.so`,
   `libarchive.so`, AndroidX/datastore libraries and files under 256 KB are
   not treated as extensions) — see
   [Extension loader](#5-extension-loader-and-publishing-to-the-catalog).
4. Optionally `patches/tanoshi-infinite-scroll.patch` (the Termux package's
   auto-loading catalogue), then the web-UI patch series:
   - `patches/tanoshi-web-ui.patch` + `patches/tanoshi-web-ui-settings.patch`
     (the 0.8.0 web features: Delete History button, working catalogue filter
     sheet, device-local Layout Preference presets, reader Tap Area);
   - **`native/patches/0003-web-ui-hide-source-management.patch`**: the
     embedded web UI becomes **backend-only** for extensions: Settings →
     Sources hides the **Available** sources list, the source-list
     **Install/Update** buttons and the per-source **Uninstall** button (they
     never worked on Android). The installed sources list and per-source
     preferences remain; extension management is done in the app's Sources
     tab.
   - **`native/patches/0004-web-ui-themes.patch`** plus the new source file
     `native/patches/theme_settings.rs` (copied to
     `crates/tanoshi-web/src/common/theme_settings.rs`): per-device theme
     palettes (12 presets + custom Primary/Secondary/Accent picker) in
     Settings → Appearance → Themes, stored in `localStorage` only.
   - **`native/patches/0007-web-ui-download-progress.patch`** (0.13.1, final
     in 0.13.5): refuses an empty chapter-download selection, shows
     **"Download queued — chapters are saved on the server"**, adds a
     per-chapter download icon in every chapter row, renders per-chapter
     `downloaded/total` progress, refreshes the manga detail every 4 s (up to
     30 ticks) while any `download_status` is pending, and gives the downloaded
     marker a `Downloaded` title/label. Patch 0006 (reader Save image) was
     deleted in 0.13.5 along with the bridge method and WebView listener.
   - **`native/patches/0009-web-ui-layout-presets-double-gap.patch`** (0.13.6):
     Layout Preference presets are fetched from the server
     (`GET /api/layout-presets`), uploaded once from this device's legacy local
     list when the server has none, and saved via `PUT`; only the selected
     preset name stays in localStorage. Also fixes the double-page layout
     (`justify-content:center`, images with zero horizontal auto margins).
   - **`native/patches/0010-web-ui-manga-actions-library-tabs.patch`** (0.13.7,
     applied after 0009): manga-page **Migrate** icon + source-picker modal
     (writes `migration::set` and routes to the target catalogue pre-filled with
     the title), **Change Library** action
     (`deleteFromLibrary` + `addToLibrary` category replacement), and the
     tabbed Library page (All + per-category tabs, All merges every category by
     manga id; `/library/categories` redirects; topbar Categories hidden).
     Touches `manga.rs`, `library.rs`, `library_list.rs`, `styles.scss`.
   - **`native/patches/0011-web-ui-library-tabs-options-sidebar.patch`**
     (0.13.8, applied after 0010): browser-style Library tabs with a 5rem
     desktop-sidebar offset at ≥1024px; Settings → Categories **Library
     tabs** panel with per-device localStorage options (`settings:library`)
     `show_all_tab`/`show_category_counts` (All hidden falls back to
     uncategorized/Default; counts only in labels when enabled); inline
     per-category **Rename** using the existing `updateCategory` mutation.
     Touches `library_settings.rs`, `settings_categories.rs`, `library.rs`,
     `styles.scss`.
   - **`native/patches/0012-web-ui-continuous-autochapter-webtoon.patch`**
     (0.13.9, applied after 0011): seamless Continuous reading (append next
     chapter's pages to the same scroll via `maybe_append_next_chapter`, auto
     previous chapter when scrolling back to the top with a `user_scrolled`
     guard, `page_chapters` mapping for per-chapter progress/completion, small
     "No previous chapter"/"No more chapters" labels instead of Prev/Next
     buttons) and automatic webtoon detection (loaded page height ≥ 2.5×
     width → Continuous + Fit Width + padding off + saved settings + one-shot
     snackbar; fit-only settings in webtoon mode). Touches `reader.rs` and
     `reader_settings.rs`.
   - These web patches only matter when regenerating the wasm UI: the vendored
     `native/web-dist/` already contains the patched output, so a normal server
     rebuild uses it as-is. `build_tanoshi.sh` applies them automatically
     (infinite scroll → web features → 0003 → `theme_settings.rs` +
     0004 → 0007 → 0009 → 0010 → 0011 → 0012).
5. **`native/patches/0005-downloads-tempchapter.patch`** (final in 0.13.5),
   applied after the app/loader patches: a new `AuthGuard` lets **any
   signed-in user** download/remove/reorder chapters and read the queue/
   downloaded list (the admin-only queue-repro endpoints stay `AdminGuard`);
   the worker now stores downloads as **extracted page-image folders** under
   the configured `download_path` (no source-name level): it downloads a
   temporary CBZ, `spawn_blocking` extracts every ZIP entry into
   `<download_path>/<manga title>/<chapter title>/`, deletes the archive and
   stores the folder path; the reader reads directories
   (`get_pages_from_dir`, image files, natural sort); removal/re-download
   handles folders (`remove_dir_all`) and legacy archives. The server creates
   the configured directory in `default_download_path()`/`bootstrap()`; the
   app writes the public `<external storage>/yakushi/.downloaded` path and
   migrates old configs (`ConfigManager.ensurePublicDownloadPath`; manifest
   `MANAGE_EXTERNAL_STORAGE` + legacy `WRITE_EXTERNAL_STORAGE`).
   *Note:* the 0.13.5 release also fixes the worker's Local-source guard to
   reject only `10000..1_000_000`; that hunk is present in the built source
   tree but **not in this checked-in 0005 patch file** — regenerate the
   patch from the build tree before rebuilding from the patch set.
6. **`native/patches/0008-server-layout-presets.patch`** plus the new file
   `native/patches/layout_presets.rs` (copied to
   `crates/tanoshi/src/presentation/rest/layout_presets.rs`), applied after
   0005: registers **`GET/PUT /api/layout-presets`** in the REST router; reads/
   writes `<TANOSHI_HOME>/layout_presets.json` (next to `config.yml`) with a
   tmp+rename atomic write, returns `{"presets":[]}` when absent/invalid and
   validates `{"presets":[...]}` on PUT. The endpoint is unauthenticated.

Manually:

```bash
git clone https://github.com/luigi311/tanoshi.git
cd tanoshi
git checkout v0.40.0

# Termux patch set from the repository root
git apply ../patches/tanoshi-android.patch
cp ../patches/http.rs.new      crates/tanoshi/src/infrastructure/http.rs
cp ../patches/extension-build.rs.new crates/tanoshi-vm/src/extension/build.rs
git apply ../patches/tanoshi-infinite-scroll.patch   # optional
# 0.8.0 web features (Delete History, filters, layout presets, tap area)
git apply ../patches/tanoshi-web-ui.patch
git apply ../patches/tanoshi-web-ui-settings.patch
# Embedded web UI is backend-only for extensions (on top of infinite scroll)
git apply ../android/native/patches/0003-web-ui-hide-source-management.patch
# Per-device web-UI themes: copy the new module, then apply the patch
cp ../android/native/patches/theme_settings.rs \
   crates/tanoshi-web/src/common/theme_settings.rs
git apply ../android/native/patches/0004-web-ui-themes.patch
# Chapter download per-chapter button/progress/Downloaded marker (0.13.5)
git apply ../android/native/patches/0007-web-ui-download-progress.patch
# Server-backed layout presets + gapless double-page reader (0.13.6)
git apply ../android/native/patches/0009-web-ui-layout-presets-double-gap.patch
# Manga Migrate/Change Library + tabbed Library (0.13.7)
git apply ../android/native/patches/0010-web-ui-manga-actions-library-tabs.patch
# Library tab styling/options + inline category rename (0.13.8)
git apply ../android/native/patches/0011-web-ui-library-tabs-options-sidebar.patch
# Seamless continuous chapters + automatic webtoon layout (0.13.9)
git apply ../android/native/patches/0012-web-ui-continuous-autochapter-webtoon.patch

# Android app layer
git apply ../android/native/patches/0001-android-app.patch
# Extension loader (new source file + patch)
cp ../android/native/patches/android_loader.rs crates/tanoshi-vm/src/extension/android_loader.rs
git apply ../android/native/patches/0002-android-extension-loader.patch
# Server: signed-in users can download; offline cache in <home>/.tempchapter (0.13.0)
git apply ../android/native/patches/0005-downloads-tempchapter.patch
# Server: shared layout presets API/file (0.13.6)
cp ../android/native/patches/layout_presets.rs \
   crates/tanoshi/src/presentation/rest/layout_presets.rs
git apply ../android/native/patches/0008-server-layout-presets.patch

# Use the vendored, Android-patched compress-tools crate
export CARGO_HOME="$PWD/cargo-home"
mkdir -p "$CARGO_HOME"
cat > "$CARGO_HOME/config.toml" <<EOF
[patch.crates-io]
compress-tools = { path = "$(cd .. && pwd)/vendor/compress-tools-0.16.1" }
EOF
```

### 2. Minimal `libarchive.so`

The vendored `compress-tools` probes `libarchive` through `pkg-config`, so
libarchive must be installed into a staging prefix first. The build is
deliberately minimal — Tanoshi only needs ZIP/gzip handling for chapter
archives — and zlib is linked into the library statically so the APK does not
need a separate `libz.so`.

```bash
export PREFIX="$PWD/android-deps/arm64-v8a"
cmake -S libarchive -B build-libarchive \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM="android-$API" \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" \
  -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
  -DBUILD_SHARED_LIBS=ON \
  -DENABLE_ZLIB=ON \
  -DENABLE_OPENSSL=OFF -DENABLE_LIBXML2=OFF -DENABLE_EXPAT=OFF -DENABLE_ICONV=OFF \
  -DENABLE_BZip2=OFF -DENABLE_LZMA=OFF -DENABLE_LZ4=OFF -DENABLE_ZSTD=OFF \
  -DENABLE_TEST=OFF -DENABLE_TAR=OFF -DENABLE_CPIO=OFF -DENABLE_CAT=OFF
cmake --build build-libarchive -j"$(nproc)"
cmake --install build-libarchive
```

The block above is the baseline configure; `build_libarchive.sh` is the
authoritative invocation. It pins **libarchive 3.8.11**, makes zlib a static
input of `libarchive.so`, and patches the SONAME to plain `libarchive.so` (a
stock build would name it `libarchive.so.<major>`, which the runtime lookup
does not expect). Any libarchive release **≥ 3.2.0** satisfies
`compress-tools`, but keep the pinned version for reproducible output. Then point `pkg-config` at the staged files
and tell Cargo where to link:

```bash
export PKG_CONFIG_ALLOW_CROSS=1
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"
# The .pc file contains absolute paths, so no PKG_CONFIG_SYSROOT_DIR is needed.
```

### 3. Web UI and server binary

The server binary embeds the Tanoshi web UI (`crates/tanoshi-web/dist`) with
`rust-embed`, so a web UI dist must exist before the server is compiled. The
default source is the **vendored dist** in this directory:

- `android/native/web-dist/` is the **rebuilt web UI** used by app 0.13.9+:
  upstream Tanoshi 0.40.0's wasm UI built with
  `patches/tanoshi-infinite-scroll.patch` (auto-loading catalogue),
  `patches/tanoshi-web-ui.patch` + `patches/tanoshi-web-ui-settings.patch`
  (Delete History, catalogue filter sheet, Layout Preference presets, Tap
  Area), `native/patches/0003-web-ui-hide-source-management.patch`
  (extension Available list and Install/Update/Uninstall hidden — app-only
  management) and `native/patches/0004-web-ui-themes.patch` + copied
  `theme_settings.rs` (per-device theme palettes, Settings → Appearance →
  Themes; 0.9.1 maps primary → top/bottom banners, secondary → icons and the
  unread badge, accent → tinted page background/headers/settings panels, with
  per-mode adaptive text in a `body.themed` class; 0.9.2 adds
  `--theme-cell-light`/`--theme-cell-dark` + `--theme-cell-text-light/dark` for
  the Settings-page list cells) and
  `native/patches/0007-web-ui-download-progress.patch` (0.13.5: per-chapter
  download button, queued/progress/auto-refresh and **Downloaded** marker) and
  `native/patches/0009-web-ui-layout-presets-double-gap.patch` (0.13.6:
  server-backed layout presets + gapless double-page reader) and
  `native/patches/0010-web-ui-manga-actions-library-tabs.patch` (0.13.7:
  manga Migrate/Change Library + tabbed Library) and
  `native/patches/0011-web-ui-library-tabs-options-sidebar.patch` (0.13.8:
  browser-style/desktop-safe tabs, per-device tab options, inline category
  rename) and
  `native/patches/0012-web-ui-continuous-autochapter-webtoon.patch` (0.13.9:
  seamless continuous chapters + automatic webtoon layout; patch 0006/reader
  Save image was deleted). Its wasm bundle still contains the
  `catalogue-scroll-sentinel`,
  which auto-loads the next catalogue page about 600 px before the viewport and
  turns the **Load More** button into *No more results* at the end.
  Current dist asset names (0.13.9):
  `tanoshi-web-8ff577ec017e66ca.js`, `tanoshi-web-1f6ae6e5b2416741_bg.wasm`,
  `styles-771514d76def24f9.css`.
- `build_tanoshi.sh` picks the web UI in this order: an existing
  `crates/tanoshi-web/dist/`, then the vendored `android/native/web-dist/`
  (copied into place), then `trunk build --release`. A plain
  `./build_tanoshi.sh` therefore uses the vendored rebuilt UI with no web
  toolchain installed.
- The checked-in `libtanoshi.so` was rebuilt this way for app **v0.13.9**
  (0.13.8/0.13.7/0.13.6/0.13.5/0.13.1/0.13.0/0.9.x/0.7.0/0.8.0 used earlier
  rebuilt dists; 0.2.0/0.5.0/0.6.0 used older dists). The 0.13.9 rebuild
  carries patch 0012 (seamless continuous chapters + webtoon auto-layout) on
  top of 0011/0010/0009/0008; patch 0006 stays deleted.

The theme palette is stored by the browser in
`localStorage["settings:theme-palette"]`, so **it is not embedded or served by
the server**; the server only ships the frontend code. Regenerating the dist
changes the hashed asset names, so the binary must be rebuilt (and
`native/web-dist/` refreshed) after any web-UI change.

To change the web UI itself, rebuild the wasm dist from a patched checkout
using the pinned toolchain versions from the prerequisites (trunk **0.22.1**,
wasm-bindgen **0.2.129**, sass **1.106.1**, wasm-opt **133**,
`wasm32-unknown-unknown`):

```bash
# in the Tanoshi source tree, after applying the root Termux patches:
git apply ../patches/tanoshi-infinite-scroll.patch
git apply ../patches/tanoshi-web-ui.patch
git apply ../patches/tanoshi-web-ui-settings.patch
git apply ../android/native/patches/0003-web-ui-hide-source-management.patch
cp ../android/native/patches/theme_settings.rs \
   crates/tanoshi-web/src/common/theme_settings.rs
git apply ../android/native/patches/0004-web-ui-themes.patch

cd crates/tanoshi-web
CARGO_TARGET_DIR="$PWD/../../target-wasm" trunk build --release
cd ../..

# refresh the vendored copy used by future native builds
rm -rf android/native/web-dist && cp -R crates/tanoshi-web/dist android/native/web-dist
```

Then build the server. Make sure the `pkg-config` environment from step 2
(`PKG_CONFIG_ALLOW_CROSS`, `PKG_CONFIG_PATH`) is still exported, because the
vendored `compress-tools` build script probes libarchive at compile time:

```bash
CARGO_TARGET_DIR="$PWD/target-android" \
  cargo build -p tanoshi --release --target aarch64-linux-android
```

Result: `target-android/aarch64-linux-android/release/tanoshi`. The same
patched checkout is what the Termux package builds, except the Android build
adds patches 0001–0004; keep `patches/` (web features) and
`native/patches/0003/0004` in sync with `android/native/web-dist/`.

### 4. Extension plugins (bundled and exported sources)

The nine bundled plugins are built for `aarch64-linux-android` against the
`tanoshi-lib` version the server uses (0.38.0 for Tanoshi 0.40.0). The Termux
package ships them as `plugins/<name>.so`, and `sync_prebuilt.sh` renames them
to `lib<name>.so` for `nativeLibraryDir`.

The Rust sources of the five extensions ported in this repository are exported
in [`android/extensions/sources/`](../extensions/sources/):

- `sources/ported/{atsumaru,hentainexus,madaradex,mangaball,qiscans}` — the
  ported crates (they must be built inside the upstream
  `luigi311/tanoshi-extensions` workspace because they use its
  `extension-utils`/`networking`/`tanoshi-lib` crates);
- `sources/template/` — a scaffold crate for porting a new source;
- `sources/README.md` — layout, conventions and porting notes.

Build one or all of them with
[`android/native/build_extension.sh`](../native/build_extension.sh) (Termux's
native `aarch64-linux-android` toolchain, or a desktop with
`ANDROID_NDK_HOME`):

```bash
# all five ported extensions
bash android/native/build_extension.sh
# one crate (e.g. a template copy you are porting)
bash android/native/build_extension.sh --crate android/extensions/sources/template
```

The script clones/pins `luigi311/tanoshi-extensions`, applies this repository's
`patches/networking-helpers.patch` (+ the icon patch when available), copies
the crate(s) into the workspace `extensions/` directory, builds with
`RUSTFLAGS=-Clink-arg=-Wl,--allow-multiple-definition -Clink-arg=-Wl,-z,max-page-size=16384`,
strips the `.so` into `android/extensions/plugins/lib<name>.so`, and prints the
SHA-256, size and a ready-to-paste catalog entry.

The manual equivalent (what the script automates):

```bash
git clone https://github.com/luigi311/tanoshi-extensions.git extensions-src
cd extensions-src
git apply ../patches/networking-helpers.patch
git apply ../patches/extension-icons.patch
cp -r ../android/extensions/sources/ported/* ./extensions/

CARGO_TARGET_DIR="$PWD/../target-extensions" \
  cargo build --release --target aarch64-linux-android \
  -p atsumaru -p qiscans -p mangaball -p madaradex -p hentainexus
```

Each plugin ends up as `lib<name>.so`. If upstream changed the files these
patches touch, `git apply` fails; see `patches/README-patches.txt` for the
current patch contents. Never mix plugins built against a different
`tanoshi-lib` version — they refuse to load.

#### Publishing an extension to the tanoshi-extension repository

The canonical extension repository is
[**spainclaro-debug/tanoshi-extension**](https://github.com/spainclaro-debug/tanoshi-extension).
It hosts the published plugins/catalog/sources/tooling:

```
tanoshi-extension/
├── android/extensions/
│   ├── index.json                 # catalog the app fetches
│   └── plugins/lib<name>.so       # prebuilt aarch64 plugins
├── tanoshi-extensions.json        # same catalog at the repo root
├── sources/                       # Rust sources of the five ported extensions + template
├── patches/                       # patches applied to the tanoshi-extensions workspace
├── tools/build_extension.sh       # build a source into plugins/lib<name>.so
└── README.md
```

The app's built-in repository points at this repo's
`android/extensions/index.json` (label `tanoshi-extension (default)`); users
can also add it via **Sources → Add repository** with the GitHub link
`https://github.com/spainclaro-debug/tanoshi-extension` or the raw index URL.

To publish a plugin there:

1. Build it with the repo's `tools/build_extension.sh` for
   **`aarch64-linux-android`** against the same `tanoshi-lib` (`0.38.0`) and
   Rust toolchain family as the bundled server.
2. Verify it is an aarch64 shared object and compute the catalog values:

   ```bash
   file lib<name>.so
   sha256sum lib<name>.so
   stat -c %s lib<name>.so
   ```

3. Commit the file under `android/extensions/plugins/lib<name>.so` and add the
   entry to both `android/extensions/index.json` and
   `tanoshi-extensions.json`: `name`, `displayName`, `description`, HTTPS
   `url`, `sha256`, `size`, `ported`. The URL points at the raw GitHub file
   (`https://raw.githubusercontent.com/spainclaro-debug/tanoshi-extension/main/android/extensions/plugins/lib<name>.so`).
4. Commit/push. Installed apps show the new/updated entry after a **Sources**
   catalog refresh (and **Update available** if the hash changed).

This repository keeps copies of the same sources/tooling for
back-compatibility:
[`android/extensions/sources/`](../extensions/sources/),
[`android/extensions/repo-template/`](../extensions/repo-template/) and
[`native/build_extension.sh`](../native/build_extension.sh) (which writes to
`android/extensions/plugins/` here instead). The local
`android/extensions/index.json` is legacy/tooling; `tanoshi-extension` is the
canonical catalog. See
[Extensions (Sources tab)](../README.md#extensions-sources-tab) in the app
documentation for the user-facing flow and the exact JSON contract.

### 5. Extension loader and publishing to the catalog

`native/patches/0002-android-extension-loader.patch` (plus the new source file
`native/patches/android_loader.rs`, copied into
`crates/tanoshi-vm/src/extension/` by `build_tanoshi.sh`) implements the
runtime extension support:

- **Dual-directory loading** in `crates/tanoshi-vm/src/extension/manager.rs`:
  `plugin_load_dirs()` returns the writable plugin directory first and then
  `TANOSHI_PLUGIN_LOAD_DIR` (`nativeLibraryDir`, set by the app).
  `load_all()` walks both, normalizes each file to a plugin key, and skips a
  later file with the same key — so a user-installed `<name>.so` overrides
  the bundled `lib<name>.so`. Host libraries are ignored:
  `HOST_LIBRARY_NAMES` covers `libtanoshi.so`, `libarchive.so`,
  `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so`, plus
  `libandroidx*`/`libdatastore*` prefixes, managed/staged names and anything
  under 256 KB. This is what removed the old
  `undefined symbol: plugin_declaration` startup errors.
  `resolve_plugin_path()` finds `<name>.so` or `lib<name>.so` for
  load/restart operations.
- **Persistent disable/uninstall** in the same patch: the manager reads
  `files/tanoshi/plugins/.tanoshi-disabled` (one normalized extension key per
  line) and skips those plugins at load time (`skipping disabled extension
  ...`; loading a disabled plugin by name fails with `extension <name> is
  disabled`). A web-UI uninstall of a user copy deletes the file and clears the
  marker; a web-UI uninstall of a bundled plugin writes the marker (the APK
  file is read-only). The app writes the same marker for its Enable/Disable
  actions.
- **In-memory loader** in `crates/tanoshi-vm/src/extension/android_loader.rs`:
  on Android the extension worker no longer calls `dlopen()` on the file
  directly. It reads the `.so` bytes, creates an anonymous `memfd`, copies the
  bytes into the mapping, marks it `PROT_READ|PROT_EXEC` and loads it with
  `android_dlopen_ext` + `ANDROID_DLEXT_USE_LIBRARY_FD` (via `libloading`).
  If that fails it falls back to a regular `dlopen()` and logs both errors.
  This is what allows plugins in the app's writable storage to load despite
  Android 10+ W^X/SELinux rules; the app keeps its targetSdk (35).
- **No native library changes for user plugins:** they are downloaded by the
  app into `files/tanoshi/plugins/`, not packaged in the APK; the bundled nine
  still ship in `jniLibs/arm64-v8a/`.

The patch is applied automatically by `build_tanoshi.sh`. Because the loader
is compiled into the server, the checked-in `libtanoshi.so` must be rebuilt
whenever the loader changes.

### 6. Copy into the app

`native/sync_prebuilt.sh` performs the rename and the copy:

```bash
cd android
native/sync_prebuilt.sh
```

which is equivalent to:

```bash
DEST=app/src/main/jniLibs/arm64-v8a
cp target-android/aarch64-linux-android/release/tanoshi "$DEST/libtanoshi.so"
cp "$PREFIX/lib/libarchive.so"                              "$DEST/libarchive.so"

# Plugins: either from the Termux package (already aarch64 Android builds)...
for name in mangadex guya manhwa18cc weebcentral atsumaru \
            qiscans mangaball madaradex hentainexus; do
  cp "<termux-package>/plugins/$name.so" "$DEST/lib$name.so"
done
# ...or from a fresh extension-workspace build (step 4):
for so in target-extensions/aarch64-linux-android/release/lib*.so; do
  cp "$so" "$DEST/$(basename "$so")"
done
```

The plugin files must keep the `lib*.so` name; the installer only extracts
those as native libraries. The APK build then picks everything up
automatically; no Gradle or Kotlin change is needed.

---

## Verifying the results

Run these checks on every binary before committing it:

```bash
cd android/app/src/main/jniLibs/arm64-v8a

# 1. Architecture: every file must be an aarch64 ELF shared object
file *.so
#   expected: ELF 64-bit LSB shared object, ARM aarch64, ...

# 2. Dynamic dependencies: the server needs libarchive.so, liblog.so and the
#    normal libc/libm/libdl; libarchive.so itself needs only libc/libdl
#    (zlib is statically linked); plugins need only libc/libm/libdl
readelf -d libtanoshi.so | grep NEEDED
readelf -d libarchive.so | grep NEEDED

# 3. No hardcoded RPATH/RUNPATH: the app supplies LD_LIBRARY_PATH at runtime
readelf -d libtanoshi.so | grep -E 'RPATH|RUNPATH'   # expect no output

# 4. 16 KB page alignment (Android 15+ devices with 16 KB pages)
readelf -lW libtanoshi.so | grep LOAD
#   expected alignment: 0x4000 in the last column

# 5. The embedded web UI is the vendored dist (v0.2.0+; rebuilt for 0.9.1)
grep -c -a "catalogue-scroll-sentinel" libtanoshi.so   # expect 1
grep -c -a "settings:theme-palette" libtanoshi.so      # expect 1 (0.9.0+)
grep -c -a "Monochromatic Blue" libtanoshi.so          # expect 1 (0.9.0+ preset)
grep -c -a "body.themed" libtanoshi.so                 # expect >= 1 (0.9.1+)
grep -c -a -e "--theme-cell-light" libtanoshi.so       # expect >= 1 (0.9.2+)
grep -c -a "chapter downloads directory" libtanoshi.so # expect 1 (0.13.0+ bootstrap log)
grep -c -a "could not extract chapter" libtanoshi.so   # expect >= 1 (0.13.5 extraction fallback)
grep -c -a "local source can't be downloaded" libtanoshi.so # expect >= 1 (0.13.5 Local guard)
grep -c -a "Download queued" libtanoshi.so             # expect >= 1 (0.13.1+ feedback)
grep -c -a "Select at least one chapter" libtanoshi.so # expect >= 1 (0.13.1+ empty selection)
grep -c -a "Downloaded" libtanoshi.so                  # expect >= 1 (0.13.1+ marker label)
grep -c -a "layout_presets.json" libtanoshi.so         # expect >= 1 (0.13.6 server storage)
grep -c -a "/api/layout-presets" libtanoshi.so         # expect >= 1 (0.13.6 route)
grep -c -a "stored on this server" libtanoshi.so       # expect >= 1 (0.13.6 UI text)
grep -c -a "Migrate manga to source" libtanoshi.so     # expect >= 1 (0.13.7 modal title)
grep -c -a "Change Library" libtanoshi.so              # expect >= 1 (0.13.7 action)
grep -c -a "Library updated" libtanoshi.so             # expect >= 1 (0.13.7 snackbar)
grep -c -a "library-tab" libtanoshi.so                 # expect >= 1 (0.13.7 tabs CSS)
grep -c -a "All tab: Shown" libtanoshi.so              # expect >= 1 (0.13.8 toggle)
grep -c -a "Counts: Shown" libtanoshi.so               # expect >= 1 (0.13.8 toggle)
grep -c -a "Rename library" libtanoshi.so              # expect >= 1 (0.13.8 title)
grep -c -a "Library tabs" libtanoshi.so                # expect >= 1 (0.13.8 settings panel)
grep -c -a "No previous chapter" libtanoshi.so         # expect >= 1 (0.13.9 label)
grep -c -a "No more chapters" libtanoshi.so            # expect >= 1 (0.13.9 label)
grep -c -a "Webtoon layout detected" libtanoshi.so     # expect >= 1 (0.13.9 snackbar)
grep -c -a "only the fit options" libtanoshi.so        # expect >= 1 (0.13.9 settings note)
#   For 0.7.0+ the js/wasm/css asset names embedded in libtanoshi.so should
#   match the names in android/native/web-dist/index.html

# 6. The Android extension loader is compiled in (v0.5.0 and later)
grep -c -a "android_dlopen_ext failed" libtanoshi.so   # expect 1

# 7. Persistent disable/uninstall support is compiled in (v0.6.0 and later)
grep -c -a "tanoshi-disabled" libtanoshi.so            # expect 1
```

And on the built APK (since 0.3.0 the output is
`android/app/build/outputs/apk/debug/tanoshi-mobile-server-release-<date>.apk`):

```bash
APK=android/app/build/outputs/apk/debug/tanoshi-mobile-server-release-*.apk
unzip -l $APK | grep 'lib/arm64-v8a'
#   all eleven .so files must be present
zipalign -c -P 16 4 $APK   # Android 15+ 16 KB alignment check
```

If either alignment check reports `0x1000`, rebuild with
`-Wl,-z,max-page-size=16384` (see the environment section) and re-copy.

---

## How the checked-in binaries were produced

The files committed in `android/app/src/main/jniLibs/arm64-v8a/` are the
output of this pipeline, built on an x86_64 Linux host with:

- Android NDK r26+ (aarch64 clang wrapper for API 26),
- Rust 1.99.1 with the `aarch64-linux-android` target,
- upstream Tanoshi checked out at the tag in `VERSION` (`0.40.0`) and patched
  with the Termux patch set + the web-UI patch series
  (`patches/tanoshi-web-ui.patch`, `patches/tanoshi-web-ui-settings.patch`,
  `native/patches/0003-web-ui-hide-source-management.patch`,
  `native/patches/0004-web-ui-themes.patch` + copied `theme_settings.rs`,
  `native/patches/0007-web-ui-download-progress.patch`) +
  `native/patches/0001-android-app.patch` +
  `native/patches/0002-android-extension-loader.patch` (+ `android_loader.rs`) +
  `native/patches/0005-downloads-tempchapter.patch`,
- the embedded web UI from `android/native/web-dist/` (rebuilt from source with
  the 0.8.0 web features, infinite-scroll, app-only extension-management,
  per-device theme patches, the 0.13.5 per-chapter download UI and the 0.13.6
  shared-layout/double-page changes, the 0.13.7 manga actions/library-tabs patch,
  the 0.13.8 tab styling/options/rename patch and the 0.13.9 continuous/webtoon
  reader patch; 0.9.1 reworked the theme application and 0.9.2 themed the
  Settings list cells),
- libarchive **3.8.11**, built with zlib linked statically and SONAME
  `libarchive.so`,
- the plugin ABI pinned to `LIB_VERSION` (`0.38.0`); plugin files are named
  `lib<plugin>.so`.

`sync_prebuilt.sh` does not rebuild anything — it only copies what the other
scripts produced, so `git diff` after a rebuild shows exactly which binaries
changed. The 0.3.0/0.4.0 app releases did not change `libtanoshi.so`; **0.5.0
did** (extension loader + catalog), **0.6.0 rebuilt it again** with the
host-library filtering fix and the `.tanoshi-disabled` marker support, and
**0.7.0 rebuilt it once more** to embed the rebuilt web UI with the
backend-only extension-management patch, **0.8.0 rebuilt it** for the Delete
History/filters/layout-preset/tap-area web features, **0.9.0 rebuilt it again**
for the per-device theme palettes, **0.9.1 rebuilt it** with the reworked
full-UI theme application and adaptive text, and **0.9.2 rebuilt it** with the
themed Settings list cells, and **0.13.0/0.13.1 rebuilt it** for the
early download cache/UI, **0.13.5 rebuilt it** for the public page-image
extraction (`AuthGuard`, folder downloads, directory reading, removal) and the
final per-chapter download UI (patch 0006 deleted), **0.13.6 rebuilt it** with
the shared layout-presets route/module and the double-page reader fix, and
**0.13.7 rebuilt it** with patch 0010 (manga Migrate/Change Library + tabbed
Library), **0.13.8 rebuilt it** with patch 0011 (browser-style/desktop-safe
tabs, per-device tab options, inline category rename), and **0.13.9 rebuilt it**
with patch 0012 (seamless continuous chapters + automatic webtoon layout). The exported extension sources
(`android/extensions/sources/`), repo template and `build_extension.sh` were
added in 0.6.0; the canonical catalogs/plugins moved to the separate
`tanoshi-extension` repository in 0.7.0. Releases that only change the app UI
(e.g. 0.7.1) do not change the native binaries. The project repository was
renamed to `spainclaro-debug/Yakushi` (old `tanoshi-termux` URLs
redirect); the Termux tarball/asset name `tanoshi-termux-aarch64.tar.gz` is
unchanged.

### Reproducibility caveats

Bit-for-bit identical binaries are **not** expected between rebuilds:

- The extension workspace is built from its default branch unless a ref is
  pinned, so upstream may have moved.
- Cargo resolves dependencies from the lock files; a `Cargo.lock` update or a
  different Rust/NDK toolchain changes code generation.
- The web UI is the vendored rebuilt dist; its hashed asset names and
  embedded build output change if it is regenerated with another
  `trunk`/`wasm-bindgen`/`sass`/`wasm-opt` toolchain or after another web-UI
  patch.
- Timestamps and absolute build paths can end up in binaries and archives.

Functionally equivalent is what matters: the server version, the
`tanoshi-lib` ABI that plugins must match, and the checks above. When you
rebuild, keep `VERSION`, `LIB_VERSION` and the bundled plugin versions in
sync, and commit the whole `jniLibs/arm64-v8a/` directory in one change.

---

## Troubleshooting

**`Unable to find libarchive`** during `cargo build` — the vendored
`compress-tools` uses `pkg-config`. Export `PKG_CONFIG_ALLOW_CROSS=1` and
`PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"` (pointing at the
`cmake --install` prefix), and confirm the file
`$PREFIX/lib/pkgconfig/libarchive.pc` exists.

**`error: linking with ... failed` / `multiple definition of ...`** — the
Termux patch set requires `-Clink-arg=-Wl,--allow-multiple-definition` in
`RUSTFLAGS`, as shown above.

**`error[E0463]: can't find crate for core`** — the
`aarch64-linux-android` target is not installed: `rustup target add
aarch64-linux-android`.

**`wasm-bindgen` version mismatch when running `trunk`** — install exactly
`cargo install wasm-bindgen-cli --version 0.2.129 --locked` (the version
pinned by the upstream web workspace).

**Plugins do not load in the app** — the plugin's embedded `tanoshi-lib`
version differs from the server's (0.38.0). Rebuild both from the same
`Cargo.lock` state, then reinstall the APK.

**`cannot execute: required file not found` / `Permission denied` when the
server starts** — the binary was not packaged as a native library. The
installer only extracts files whose names match `lib*.so` from
`jniLibs/<abi>/`, so keep `libtanoshi.so`, `libarchive.so` and `lib<plugin>.so`
naming.
