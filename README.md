# Yakushi, fork from Tanoshi self hosting server for manga.

**Yakushi** is a self-hosted manga reader for Android, forked from
[Tanoshi](https://github.com/luigi311/tanoshi). It packages the Tanoshi server
(patched for Android) inside a **standalone Android app** that runs the server
on the phone itself — no Termux, no shell, no external runtime — and shows the
full web UI in an embedded WebView or in any browser on your network. The same
source and patch set also builds a **Termux package** for people who prefer a
terminal install.

> Yakushi keeps the Tanoshi server core and its extension API, so existing
> Tanoshi extensions and data keep working. The app is distributed as a
> sideloadable APK (no Play Store build).

---

## Contents

- [What Yakushi is](#what-yakushi-is)
- [What's new in 0.13.0](#whats-new-in-0130)
- [Features](#features)
- [Architecture](#architecture)
- [Install (Android APK)](#install-android-apk)
- [Install (Termux)](#install-termux)
- [First run](#first-run)
- [Upgrading](#upgrading)
- [Building](#building)
- [Repository layout](#repository-layout)
- [Releases](#releases)
- [Security notes](#security-notes)
- [Credits and license](#credits-and-license)

---

## What Yakushi is

- **One APK, a complete manga server.** The app embeds the Tanoshi server
  binary (`libtanoshi.so`), `libarchive` and nine extension plugins. It starts
  the server as a foreground service on `127.0.0.1`, keeps it alive, and opens
  the server's web UI in a WebView.
- **The full Tanoshi frontend.** Library, catalogue and source browsing,
  search, reader, history, downloads, trackers, settings — all served by the
  bundled server and rebuilt for Yakushi (themes, backup/restore, layout
  presets, filters and more).
- **Android-native extras.** The app adds everything a plain web UI cannot do
  on a phone: server lifecycle, extension management, QR sharing over LAN,
  in-app update checks and APK installation, and a small JavaScript bridge the
  web UI uses to save backups and install missing extensions.
- **Termux package optional.** The repository root also builds a ready-to-run
  Termux package (`tanoshi-termux-aarch64.tar.gz`) with the same server and
  plugins for users who run Tanoshi from a terminal.

---

## What's new in 0.13.0

- **Save image from the reader.** Long-press an image (or right-click it in a
  browser) while reading, then choose **Save image** in the popup to save the
  page you are viewing to the phone's **Downloads** folder. In the Android app
  this goes through the app bridge (MediaStore Downloads on Android 10+, the
  app-specific Downloads folder on Android 7–9, no storage permission); in
  a browser it is a normal download. Files are named
  `yakushi-chapter-<chapter>-<timestamp>.jpg`.
- **Chapter downloads fixed.** The chapter-list **Download** button no longer
  returns **Forbidden** for non-admin users: any signed-in user can queue,
  remove or reorder chapter downloads (admin-only queue inspection/repro
  controls remain admin-only).
- **Server-side offline chapter cache.** Downloaded chapters are stored under
  the server data directory in **`.tempchapter`** (created automatically on
  install/first start; existing installs migrate from the old `downloads`
  path), so offline reading needs no phone-local copies. Removing a download
  frees the space.
- **Version 0.13.0** (tag `android-v0.13.0`, APK
  `yakushi-server-release-<date>.apk`). The repository is now
  [`spainclaro-debug/Yakushi`](https://github.com/spainclaro-debug/Yakushi)
  (the old `tanoshi-mobile`/`tanoshi-termux` URLs redirect).
- Includes all earlier features (Yakushi branding and single-APK releases,
  backup/restore, self-service registration, per-device web-UI themes,
  extension management and the reader improvements from 0.8.0).

## Features

### Reader

- **Reading modes:** paged and continuous.
- **Display:** single, double and auto (landscape-aware) pages.
- **Direction:** left-to-right and right-to-left.
- **Fit:** fit all, fit width, fit height; zoom; page padding.
- **Backgrounds:** white and black.
- **Layout Preference presets** — bundle the reader options (mode, padding,
  display, direction, background, fit) under names, with a built-in
  **Default** preset (Paged, padding off, Single, Left to Right, White, Fit
  All) and **Add New Preset**. Presets and the selected preset are stored
  **per device**; a new device starts at Default.
- **Tap Area** — choose whether the right or the left screen edge belongs to
  the next page ("bottom right: NEXT, bottom left: PREV" or the mirrored
  layout).
- **Save image** — long-press an image (right-click in a browser) and choose
  **Save image** to save the displayed page to the phone's Downloads folder
  through the app bridge (or download it normally in a browser).
- Page counter, chapter navigation, keyboard shortcuts and progress saving.

### Library and catalogue

- **Library** with categories, unread counts and recent updates.
- **Catalogue** browsing per source with popular/latest/search listings and
  **infinite scroll** (loads the next page automatically).
- **Filter sheet that shows the actual choices** — genres/tags (expanded by
  default), status, content rating and sort controls, the way Suwayomi/Mihon
  present filters. Sources without filters say so explicitly.
- Titles can be added to the library, migrated between sources and tracked.

### History

- **History page** with the last-read chapter, page and relative date.
- **Delete History** button — clears every read-history entry after an inline
  Delete/Cancel confirmation.

### Backup and Restore

- **Backup Library** — exports a versioned `tanoshi-backup` JSON file
  containing:
  - every library title, including its categories,
  - the complete reading history (chapter, read time, last page),
  - the sources those entries use (id, name, version, URL),
  - the chapter currently being read for each title.
  In the app the file is saved to the phone's **Downloads** folder
  (MediaStore on Android 10+; an app-specific Downloads folder on older
  releases); in a browser it downloads normally.
- **Restore Library** — opens the system file picker, uploads the file and
  restores the library, categories and reading progress. Titles are matched by
  source + source path and chapters by path/number, so a backup restores
  correctly on a different server. Restoring is additive and idempotent.
- **Missing sources** — sources used by the backup that are not installed are
  listed with their version; **Install Missing Sources** installs them through
  the extension catalog (the app restarts the server) and the restore is
  retried automatically.

### Accounts

- The **first account** created on a new server becomes the administrator.
- The **login page has a Register button** so additional users can create
  their own accounts. Registration is open by default and can be disabled by
  setting `enable_registration: false` in the server configuration; admins can
  also create, promote and remove users in Settings → Users.

### Themes (web UI)

- **Per-device themes** in Settings → Appearance: 12 preset palettes plus a
  **New theme** builder with Primary/Secondary/Accent colour grids, **Apply**
  and **Reset to default**.
- The palette is applied across the whole frontend: banners, icons, the unread
  badge, page background, headers/titles, settings panels and the settings list
  cells, with contrast-adaptive text in light and dark mode.
- The theme lives in the browser's `localStorage` (`settings:theme-palette`),
  so every device keeps its own theme and a new device defaults to Auto.

### Extensions and sources

- Nine bundled sources: MangaDex, Guya, Manhwa18.cc, WeebCentral, Atsumaru,
  QiScans, MangaBall, MadaraDex and HentaiNexus.
- **App-only extension management** (Settings in the app): install, update,
  reinstall, disable/enable and remove extensions from the catalog; the server
  restarts to load them.
- **Extension repositories**: the built-in `tanoshi-extension` catalog plus up
  to 10 custom HTTPS catalog repositories. Catalogs are merged, deduplicated
  and validated; per-repository failures can be retried.
- User-installed plugins live in the app's writable storage and are loaded by
  the patched server; bundled plugins stay in the APK.

### Server and app

- Start/stop/restart the server from the app; a watchdog restarts a killed
  server within a bounded budget; optional silent keep-alive.
- Port selection, **Expose on LAN** (0.0.0.0) and **QR code sharing** of the
  server URL.
- **In-app updates**: Settings → Updates checks GitHub releases, downloads the
  APK, verifies it and opens the system installer.
- Backup/restore and extension installation are exposed to the web UI through
  a restricted `window.TanoshiApp` bridge.
- Logs screen and log file.

### Downloads and trackers

- Download queue and chapter downloads managed by the server (Downloads in
  settings), including pause/resume and queue management. Any signed-in user
  can queue/remove/reorder downloads (fixed in 0.13.0 — previously
  non-admins got **Forbidden**); admin-only queue inspection/repro controls
  stay restricted.
- Downloaded chapters are cached **server-side** in the data directory under
  `.tempchapter` (created on every start; existing configs migrate from the
  old `downloads` path), so they are readable offline without copying pages
  into the phone's local storage.
- MyAnimeList/AniList tracker support from the web UI.

---

## Architecture

```
┌─────────────────────────── Android app (Compose) ───────────────────────────┐
│  TanoshiRoot / screens        Foreground service (TanhoshiService)         │
│  ┌─────────────┐   displays  ┌───────────────────────────────────────────┐  │
│  │  Browser    │◄───────────►│  libtanoshi.so  (Tanoshi server, patched)  │  │
│  │  (WebView)  │   loopback  │  ├─ web UI (wasm, embedded with rust-embed)│  │
│  └─────────────┘   HTTP/WS   │  ├─ libarchive.so                          │  │
│        │                    │  └─ extensions: lib*.so (bundled + user)    │  │
│        │ window.TanoshiApp  └───────────────────────────────────────────┘  │
│        ▼ (save backup/images, install extensions)                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

- The web UI is a Rust/wasm application built with trunk and embedded into the
  server binary; the app serves it from `127.0.0.1`.
- The server stores its data in the app's private storage
  (`files/tanoshi`), never in shared storage.
- The Termux package contains the same server binary and plugins and runs it
  from the Termux prefix.

---

## Install (Android APK)

**Requirements:** Android 8.0+ (`minSdk` 26), **arm64-v8a** device. The APK is
a sideloadable debug-signed build (signed with the project's shared sideload
key so updates install over each other).

1. Download the latest `yakushi-server-release-<date>.apk` from the
   [releases page](https://github.com/spainclaro-debug/Yakushi/releases/latest).
2. Allow installing unknown apps for your browser/file manager and open the
   APK.
3. Start the app, press **Start server**, and open the web UI (Browser tab).

> Only one APK is published per release: `yakushi-server-release-<date>.apk`
> (the Android app is called **Yakushi Server**; the web frontend/PWA keeps the
> short name **Yakushi**).

---

## Install (Termux)

The root package is the same Tanoshi/Yakushi server for Termux on Android
arm64:

```bash
# from a checkout of this repository
bash install.sh
```

or download `tanoshi-termux-aarch64.tar.gz` and its `.sha256` from the latest
release and follow the installer's instructions. The package installs the
server binary, the nine plugins and helper scripts into `$PREFIX`-managed
paths; your data lives in the package directory and is never touched by
`update.sh`/`selfupdate.sh`.

---

## First run

1. Start the server (app: **Start server**; Termux: `tanoshi`).
2. Open the web UI and **create the first account** — it becomes the admin.
3. Add sources/extensions (app: **Sources** tab; Termux: the server's Source
   settings), then browse the catalogue, add titles to your library and read.
4. Additional users can register from the login page (**Register**), or be
   created by an admin in Settings → Users.

---

## Upgrading

- **0.2.0 and later** install over each other (same signing key).
- **0.1.0** must be uninstalled once (different signing key).
- **0.11.0 and later**: update from **Settings → Updates**, or sideload the
  new `yakushi-server-release-<date>.apk` over the old one.
- **Older than 0.11.0** (including 0.10.0): install the current APK manually
  once; the old updater looked for the retired `tanoshi-mobile-server` asset.
- Your library, history, themes and reader settings are preserved; the
  application id stays `dev.spainclaro.tanoshi`.

---

## Building

### Android APK

Only JDK 17 and the Android SDK are needed — the native binaries are prebuilt
and checked in.

```bash
cd android
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # release variant (same shared key)
./gradlew :app:installDebug         # build + install on a device
```

The APK is written to
`app/build/outputs/apk/debug/yakushi-server-release-<date>.apk`.

### Native binaries and the embedded web UI

Rebuilding `libtanoshi.so` (with the web UI), `libarchive.so` or the extension
plugins needs a Linux build host with the Android NDK r26+ and Rust 1.99.1;
see [`android/native/README.md`](android/native/README.md). The web UI is a
Rust/wasm app built with trunk, wasm-bindgen 0.2.129, sass 1.106.1 and
wasm-opt 133, and is embedded into the server binary at compile time.

The Android app and server changes made by this fork are shipped as
reproducible patches in [`patches/`](patches/README-patches.txt), applied by
both `lib/build.sh` (Termux) and `android/native/build_tanoshi.sh` (app).

---

## Repository layout

| Path | Contents |
| --- | --- |
| `android/` | The standalone Android app (Kotlin/Compose) and its prebuilt `arm64-v8a` binaries, plus the native build pipeline. |
| `patches/` | Fork patches applied to upstream Tanoshi and its extensions. |
| `extensions/` | Sources of the ported extensions (Atsumaru, MadaraDex, QiScans, MangaBall, HentaiNexus). |
| `lib/` | Termux package build scripts. |
| `vendor/` | Vendored `compress-tools` crate patched for Android. |
| `install.sh`, `update.sh`, `selfupdate.sh`, `uninstall.sh` | Termux package management. |

---

## Releases

- The current Android release is **0.13.0** (`android-v0.13.0`), published as
  `yakushi-server-release-<date>.apk`.
- Android releases use tags **`android-vX.Y.Z`** (for example
  `android-v0.13.0`). CI builds the APK and publishes it on a GitHub release
  with the app changelog.
- Termux package releases use `v*` tags (`PACKAGE_TAG`), independent of the
  app's version.
- The in-app updater compares the numeric version of published `android-v*`
  releases with the installed version.

---

## Security notes

- **HTTP only, no TLS.** The server listens on loopback by default. Enabling
  **Expose on LAN** makes the UI reachable from other devices on the network
  in plain HTTP — only do that on a network you trust.
- The first account is the administrator; protect its password. Open
  registration can be disabled with `enable_registration: false`.
- The extension catalogs are fetched over HTTPS and plugin hashes are verified
  before installation when the catalog provides them.
- Keep backups (`Backup Library`) in a safe place: they contain your library,
  reading history and source list (not your account password).

---

## Credits and license

- Forked from **[Tanoshi](https://github.com/luigi311/tanoshi)** by luigi311
  (MIT). The server, web UI and extension API are upstream work; Yakushi adds
  the Android packaging, extension management, theme/reader/backup features
  and the fork branding.
- Extensions are ports of the
  [Keiyoushi](https://github.com/keiyoushi/extensions) Tachiyomi extensions and
  the upstream Tanoshi extension repository.
- The Android app, patches and build pipeline in this repository are released
  under the MIT license (see the upstream LICENSE for the server code).
