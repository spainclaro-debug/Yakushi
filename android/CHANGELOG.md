# Changelog — Tanoshi for Android

All notable changes to the standalone Android app in `android/`. The app is
versioned independently of the upstream Tanoshi server it bundles (currently
Tanoshi **0.40.0**) and is released as GitHub releases tagged **`android-vX.Y.Z`**
(for example `android-v0.6.0`). The Termux package in the repository root has
its own `PACKAGE_TAG` and `v*` release tags.

Versioning: local/Gradle builds default to the version in
`android/app/build.gradle.kts`. CI derives `versionName` from the
`android-vX.Y.Z` tag and sets
`versionCode = major * 10000 + minor * 100 + patch`, passing both to Gradle
with `-PappVersionName` / `-PappVersionCode`. Since 0.3.0 the sideload APK is
named `tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`, where the date is the
UTC build date in CI (via `-PapkDate`) and the local date otherwise.

---

## 0.6.0

### Added

- **Custom extension repositories.** **Sources → Add repository** accepts a
  direct HTTPS catalog `.json` URL or a GitHub repository link
  (`https://github.com/<owner>/<repo>`, `.git`/trailing slash tolerated). For
  GitHub links the app probes
  `raw.githubusercontent.com/<owner>/<repo>/{main,master}/android/extensions/index.json`
  and then `{main,master}/tanoshi-extensions.json`, validates the catalog and
  stores the resolved raw URL. Custom repositories are persisted (up to 10);
  the built-in `tanoshi-termux` repository is always present and cannot be
  removed. Catalogs from all repositories are merged, deduplicated by
  extension name (built-in/earlier repositories win), with per-repository
  failure warnings and Retry. A header refresh button reloads repositories and
  catalogs.
- **Real Enable/Disable/Uninstall.** Bundled extensions can be disabled
  persistently via the server's
  `files/tanoshi/plugins/.tanoshi-disabled` marker (one normalized extension
  name per line); the server skips listed plugins on the next start. The
  Sources tab shows a **Disabled** badge and **Enable/Disable** actions for
  bundled and user copies, and user copies additionally have **Remove**.
  Removing a user copy or installing/updating an extension clears the marker.
  Tanoshi's own web-UI uninstall now works for bundled sources too: it writes
  the marker instead of failing on the read-only plugin file, and for user
  copies it deletes the file.
- **Exported extension sources and build tooling:**
  - `android/extensions/sources/` with the five Rust-ported extensions
    (Atsumaru, HentaiNexus, MadaraDex, MangaBall, QiScans), a `template/`
    scaffold crate for new ports and a README describing the workspace,
    dependencies and conventions;
  - a distributable snapshot
    `android/extensions/tanoshi-extension-sources-2026-11-17.zip` (sources,
    template, build script and repo template);
  - `android/native/build_extension.sh`, which clones
    `luigi311/tanoshi-extensions`, applies this repository's
    `patches/networking-helpers.patch` (+ icon patch when available), copies
    the requested crate(s) into the workspace, builds for
    `aarch64-linux-android` (16 KB-page aligned), strips the result into
    `android/extensions/plugins/lib<name>.so` and prints the SHA-256, size and
    a ready-to-paste catalog entry;
  - `android/extensions/repo-template/` with the repository layout the app
    expects (`android/extensions/index.json` + `.so` files).

### Fixed

- **Support libraries were scanned as extensions.** The server now explicitly
  excludes `libtanoshi.so`, `libarchive.so`,
  `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so` and other
  `libandroidx*`/`libdatastore*` files (plus anything under 256 KB) from
  plugin discovery, so the spurious
  `undefined symbol: plugin_declaration` errors on startup are gone.
- **Uninstall was not persistent for bundled extensions** (the read-only APK
  file could not be removed) and **reinstall did not clear a previous
  uninstall/disable state**. The `.tanoshi-disabled` marker now records
  bundled uninstalls, user-copy uninstalls delete the file and clear the
  marker, and installing/updating clears the marker so a source comes back.

### Changed

- The Sources screen now shows the repository list, merged catalogs with
  repository labels, per-repository failures and enable/disable/remove
  actions; every mutation still requires a server restart (existing banner).
- **Version 0.6.0:** `versionCode` 6 in the Gradle default; the
  `android-v0.6.0` tag produces `versionCode` 600. The APK filename scheme is
  unchanged (`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`).

### Notes / limitations

- Extensions are still native code for the exact Tanoshi/`tanoshi-lib` ABI
  (`0.38.0`) and `arm64-v8a`; the app cannot compile them on-device. Use the
  exported sources and `build_extension.sh` on Termux or a desktop with the
  NDK.
- Custom repositories are third-party content: only add repositories and
  install plugins you trust. The app validates that a catalog parses and that
  downloads match their SHA-256/size, but cannot vouch for catalog contents.
- The catalog, repository resolution and downloads require network access to
  GitHub/raw.githubusercontent.com (or the custom catalog host); a server
  restart is required to apply plugin/disabled-state changes.

## 0.5.0

### Added

- **Sources tab with "Add extension"** (Suwayomi-style extension manager). A
  new bottom-navigation destination lists every extension the app can see —
  the nine bundled plugins and user-installed copies — with badges for
  Bundled / Installed by you / Overrides bundled / Ported to Rust / Update
  available. **Add extension** loads this repository's catalog
  (`android/extensions/index.json`), downloads the chosen prebuilt
  `aarch64-linux-android` `.so` over HTTPS into
  `filesDir/tanoshi/plugins/<name>.so` with a progress bar and SHA-256/size
  verification, and supports **Install / Update / Reinstall**. **Remove**
  deletes only user-installed copies (bundled extensions can never be
  removed). After any change the tab shows a Restart-needed banner/snackbar
  with **Restart server** (or **Start server**), because Tanoshi only loads
  plugins at startup. The catalog is cached per session and retryable on
  network failure.
- **Runtime user-extension loading in the bundled server.** The bundled
  `libtanoshi.so` was rebuilt with a plugin loader that reads the writable
  `files/tanoshi/plugins` directory (user copies, `<name>.so`) *and*
  `nativeLibraryDir` (bundled, `lib<name>.so`), writable directory first, so a
  user copy overrides a bundled extension with the same name. Plugins outside
  the app's native library directory are loaded from an anonymous **memfd**
  with `android_dlopen_ext` + `ANDROID_DLEXT_USE_LIBRARY_FD` (with a regular
  `dlopen()` fallback), because Android 10+ W^X forbids executing code from
  app-writable storage. `targetSdk` stays 35.
- **Extension catalog in the repository**
  (`android/extensions/index.json`): nine entries describing name,
  displayName, description, HTTPS URL, SHA-256, size and the `ported` flag for
  the bundled extensions. Adding an entry there publishes a new extension to
  installed apps without a new APK.
- New native build patch `android/native/patches/0002-android-extension-loader.patch`
  plus `android_loader.rs` (dual-directory manager loading and the memfd
  loader); `build_tanoshi.sh` applies them.

### Changed

- The app's extension management no longer requires shipping a new APK for
  each extension: installing/updating/removing happens in the Sources tab and
  takes effect after a server restart. Bundled plugins still ship inside the
  APK, and extension compatibility (exact Tanoshi/`tanoshi-lib` version,
  `arm64-v8a`) is unchanged.
- **Version 0.5.0:** `versionCode` 5 in the Gradle default; the
  `android-v0.5.0` tag produces `versionCode` 500. The APK filename scheme is
  unchanged (`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`).

### Notes / limitations

- Extensions are native code for the exact Tanoshi version/ABI; the app
  cannot compile them on-device and cannot install Tachiyomi/Keiyoushi `.apk`
  extensions. Only catalog-listed `.so` plugins are accepted.
- Only install extensions you trust: they run with the Tanoshi server's
  privileges. Installs are SHA-256/size checked; the catalog and binaries come
  from this repository's `main` branch.
- The catalog and plugin downloads require network access to GitHub; a server
  restart is required for changes to take effect; the memfd loader is
  best-effort.

## 0.4.0

### Added

- **Hidden silent-audio keep-alive.** While the server runs, the service loops
  a generated one-second silent WAV (`cacheDir/keepalive/silence.wav`, 44.1 kHz
  16-bit mono PCM, ~88 KB, nothing bundled with the APK) through `MediaPlayer`
  at **volume 0**, with media audio attributes and a partial wake mode. No
  MediaSession, no audio-focus request, no media-style/extra notification — the
  only notification is still the normal server one. Intent: keep the app in a
  media-playback foreground state so Android/OEMs are less likely to freeze or
  kill the app and its native server child processes. This is a best-effort
  mitigation, not a guarantee, and it adds a small continuous battery cost. It
  starts/stops with the server; there is no toggle. Failures are logged and the
  server keeps running without it.
- **Auto-restart watchdog.** If the server process exits unexpectedly while
  the user still wants it running, the service keeps the foreground
  notification, wake lock and silent audio alive and restarts the server: at
  most **5 times in a rolling 10-minute window**, about **3 s** apart. The
  dashboard shows `STARTING` and the log records each attempt
  (`restarting it automatically (attempt N/5, in 3 s)`). A user-requested
  Stop/Restart cancels it. After the budget is exhausted the app records the
  attempts, shows `ERROR`, releases the wake lock/audio and stops the
  foreground service.

### Changed

- The foreground service now declares and starts as
  **`specialUse|mediaPlayback`** (Android 14+; `mediaPlayback` on Android
  10–13, no type on older releases) and adds the
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission for the silent keep-alive.
- **Version 0.4.0:** `versionCode` 4 in the Gradle default; the
  `android-v0.4.0` tag produces `versionCode` 400. The APK filename scheme is
  unchanged (`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`).

### Documented

- The Android 12+ **phantom-process killer** is not exempted by media
  playback. The docs now describe how to recognise it (`adb logcat -s
  ActivityManager` → `Killing PhantomProcessRecord` / `excessive cpu`), the
  recommended device-side workarounds (Developer options **Disable child
  process restrictions**; ADB `settings put global
  settings_enable_monitor_phantom_procs false` on Android 12, or
  `device_config put activity_manager max_phantom_processes 2147483647` on
  Android 12L+) and the trade-offs.

## 0.3.0

### Added

- **"Share on your network" QR card** on the Server screen: while the server
  is running and bound to `0.0.0.0` (Expose on LAN), it shows a QR code
  encoding `http://<host-ipv4>:<port>` for another device on the same
  Wi-Fi/Ethernet network, plus the host `ip:port`/URL with a copy button. The
  address prefers a Wi-Fi/Ethernet IPv4 address (excluding loopback and
  link-local) and is refreshed when the screen resumes. QR generation uses
  ZXing core 3.5.3 (pure Java) off the main thread. When one of the
  requirements is missing, the card explains what to enable instead. Includes
  plain-HTTP/same-network warnings.

### Fixed

- **Saving the port or `config.yml` before the first server start** failed with
  `config.yml.tmp: open failed: ENOENT (No such file or directory)` because
  the data directory did not exist. `ConfigManager.setPort` now ensures the
  app directories exist, and the atomic write creates the target's parent
  directory defensively.

### Changed

- **Sideload APK renamed** from `app-debug.apk` to
  `tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`. CI passes the UTC build
  date via `-PapkDate`; local builds use the local date. CI uploads the dated
  file inside the `tanoshi-android-debug-apk` artifact and publishes it as the
  GitHub release asset. The in-app updater prefers release assets whose name
  starts with `tanoshi-mobile-server` and still accepts legacy
  `app-debug.apk` assets (0.1.0/0.2.0 releases).
- **Version 0.3.0:** `versionCode` 3 in the Gradle default; the
  `android-v0.3.0` tag produces `versionCode` 300.

## 0.2.0

### Added

- **In-app updates** (`Settings → Updates`): checks this repository's public
  GitHub releases (unauthenticated, only non-draft/non-prerelease `android-v*`
  tags), compares the version numerically with the installed app, downloads
  the release APK with a progress indicator (SHA-256 verified when the GitHub
  release asset exposes a digest) and installs it through the system
  `PackageInstaller`. Requires network, the "Install unknown apps" permission
  for Tanoshi, and the user's confirmation in the system install prompt.
- **Selectable app themes** (`Settings → Appearance`): Dark (default), Light,
  Nord and Catppuccin Material 3 colour schemes. The choice applies
  immediately, is persisted (`AppSettings.themeId`) and also switches the
  system-bar icon contrast. It affects the app UI only — the embedded Tanoshi
  web UI keeps its own theme.
- `REQUEST_INSTALL_PACKAGES` permission for the updater.

### Changed

- **Embedded web UI rebuilt with catalogue infinite scroll.** The vendored
  `android/native/web-dist/` is now the package-3 web UI built with
  `patches/tanoshi-infinite-scroll.patch`; `libtanoshi.so` was rebuilt with it.
  The catalogue now loads the next page automatically when an invisible
  sentinel below the grid (`catalogue-scroll-sentinel`) approaches the
  viewport; **Load More** stays as a manual fallback and becomes *No more
  results* when a source runs out of pages. The web UI is embedded in the
  server binary, so web-UI behaviour changes require rebuilding
  `libtanoshi.so`, not the Android app.
- **Stable sideload signing.** All releases (CI and local) are now signed with
  the committed key `android/keystore/tanoshi-signing.jks` (alias `tanoshi`)
  so the in-app updater can install a newer release over the existing app.
  The 0.2.0 release asset was still named `app-debug.apk`.
  The `debug` and `release` build types both use this key when the file is
  present, and fall back to the local Android debug keystore otherwise. See
  `android/keystore/README.md`.
- **Version/CI:** the app is 0.2.0 (`versionCode` 2 in the Gradle default;
  `android-v0.2.0` tags produce `versionCode` 200). CI computes both from the
  tag, and only `android-v*` tags trigger the workflow — plain `v*` Termux
  package tags are ignored.

### Upgrade note

**Users of 0.1.0 must uninstall it once before installing 0.2.0.** The 0.1.0
APK was signed with an ephemeral CI debug key, so Android refuses to install
0.2.0 over it. Uninstalling deletes the app's private data (library, accounts,
downloads); copy the `files/tanoshi/` directory out first if you need it.
This is a one-time step: every later release shares the committed signing key
and updates in place.

## 0.1.0

First standalone Android release (no Termux):

- Tanoshi 0.40.0 server (`libtanoshi.so`) and the nine extensions built for
  `aarch64-linux-android`, executed from `nativeLibraryDir`.
- Minimal `libarchive` 3.8.11 (zlib linked statically).
- Foreground `specialUse` service with wake lock, notifications and start/stop
  control; server data under `filesDir/tanoshi/` (`TANOSHI_HOME`).
- Compose UI: server dashboard, live log viewer, embedded WebView and browser
  hand-off, port / LAN exposure / `config.yml` editing, notification and
  battery-optimisation settings.
- First-run administrator creation through the server's GraphQL `register`
  mutation (no baked-in credentials).
- Debug-signed, arm64-v8a-only APK (`app-debug.apk`) built by GitHub Actions
  on pushes, pull requests and `android-v*` tags.
