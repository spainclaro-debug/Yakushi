# Tanoshi for Android (standalone APK)

A standalone Android app that bundles the same [Tanoshi](https://github.com/luigi311/tanoshi)
manga server and the same nine extensions as the Termux package in the
repository root — **without Termux**. The app runs the server as a foreground
service and shows its web UI in an embedded WebView (or your normal browser).

Current release: **0.9.1** — see the
[latest release](https://github.com/spainclaro-debug/tanoshi-mobile/releases/latest)
and [`CHANGELOG.md`](CHANGELOG.md). 0.9.1 reworks **how** the per-device
web-UI theme is applied (the 0.9.0 feature is unchanged): top and bottom
banners use the primary colour, icons and the unread-chapter badge use the
secondary, page background is the accent at 30% over the light/dark base,
headers/titles and settings panels use the accent, and all text is
contrast-adaptive per mode. 0.9.0 added the **per-device web-UI themes** in the
Tanoshi frontend (Settings → Appearance → Themes): 12 preset palettes plus a
**New theme** builder with Primary/Secondary/Accent colour grids, **Apply** and
**Reset to default**; the choice is stored only in the browser's
`localStorage` (`settings:theme-palette`) and applied at boot, so each
device/browser keeps its own theme and a new device defaults to Auto. The
0.8.0 release (still included) added the **Delete History** button, a working
**catalogue filter** sheet, device-local **Layout Preference** reader presets
and the **Tap Area** reader option. The 0.7.x line makes extension management
**app-only** (the web UI no longer shows the Available sources list or the
non-working Install/Update/Uninstall buttons), with the built-in extension
repository at
[**tanoshi-extension**](https://github.com/spainclaro-debug/tanoshi-extension).
The repository is now
[`spainclaro-debug/tanoshi-mobile`](https://github.com/spainclaro-debug/tanoshi-mobile)
(the old `tanoshi-termux` URL redirects); the APK name is unchanged
(`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`).

This directory is the Android Gradle project (`dev.spainclaro.tanoshi`).
The `arm64-v8a` native binaries are prebuilt and checked in, so building the
APK only needs JDK 17 and the Android SDK — no Rust or NDK. See
[`native/README.md`](native/README.md) to rebuild the native pieces.

> **Sideload only, arm64-v8a only, HTTP only.** There is no Play Store build,
> no TLS and no default account. Read the [Security notes](#security-notes)
> before exposing it on a LAN. **Users of 0.1.0 must uninstall it once before
> installing 0.2.0 or later** (different signing key) — see
> [Installing and updating](#installing-and-updating).

---

## Contents

- [What you get](#what-you-get)
- [What's new in 0.9.1](#whats-new-in-091)
- [Requirements](#requirements)
- [Building the APK](#building-the-apk)
- [Installing and updating](#installing-and-updating)
- [First run](#first-run)
- [Using the app](#using-the-app)
- [Sharing on your network (QR)](#sharing-on-your-network-qr)
- [The embedded web UI](#the-embedded-web-ui)
- [Themes](#themes)
- [In-app updates](#in-app-updates)
- [Extensions (Sources tab)](#extensions-sources-tab)
- [Architecture](#architecture)
- [Keeping the server alive in the background](#keeping-the-server-alive-in-the-background)
- [Storage layout](#storage-layout)
- [Settings and `config.yml`](#settings-and-configyml)
- [Native binaries and rebuilding them](#native-binaries-and-rebuilding-them)
- [Security notes](#security-notes)
- [Troubleshooting](#troubleshooting)
- [Versions](#versions)
- [Project layout](#project-layout)
- [Changelog](#changelog)
- [Credits and licences](#credits-and-licences)

---

## What you get

- The Tanoshi **v0.40.0** server, built from upstream `luigi311/tanoshi` with
  this repository's Termux patch set plus the Android-app patch, running as an
  `aarch64` native process inside the app's sandbox.
- **Nine extensions** bundled as native plugins: Mangadex, Guya, Manhwa18cc,
  WeebCentral, Atsumaru, QiScans, Manga Ball, MadaraDex and HentaiNexus. The
  **Sources** tab installs, updates, enables/disables and removes extensions
  from the built-in and custom repositories at runtime (the nine bundled ones
  included).
- The same embedded Tanoshi web UI as the Termux package, including the
  **infinite-scroll catalogue**: the next page loads automatically as you
  scroll, with **Load More** as the manual fallback.
- A small Material 3 wrapper: start/stop the server, watch its logs, manage
  sources/extensions, open the web UI, edit the port, toggle LAN exposure and
  share a QR code for other devices on the same network, inspect/edit
  `config.yml`, pick an app theme and update the app from inside it.
- The entire server data directory lives in the app's private storage; the
  phone needs no root, no Termux and no shell.

The APK is large because it carries the ~35 MB server binary plus `libarchive`
and the plugins. It is not split by ABI — only `arm64-v8a` is shipped.

---

## What's new in 0.9.1

- **Themed palette now applies across the whole web UI** (the 0.9.0 theme
  feature and per-device storage are unchanged):
  - the **top and bottom banners** use the **primary** colour;
  - **all icons** use the **secondary** colour;
  - the library-cover **unread-chapter badge** uses the **secondary** colour
    with contrast-adaptive badge text;
  - the **page background** is the **accent** colour mixed at **30%** opacity
    over the mode's base colour (`color-mix`: 30% accent over `#f4f4f4` in
    light mode, over `#0a0a0a` in dark; the derived background shades and
    modal background are tinted too);
  - **headers and titles** use the **accent** colour (readable variants for the
    top-bar title against the primary banner and for manga-cover titles
    against the dark thumbnail gradient);
  - **settings panels** (`.reader-settings` and the settings page content) use
    the **accent** colour as their panel background with adaptive text;
  - **all text is contrast-adaptive per mode**: `theme_settings.rs` computes
    separate light/dark text colours from the tinted background, plus readable
    heading, banner, panel and badge foregrounds, all under the `body.themed`
    class. **Reset to default** removes the class and variables and restores
    the normal Auto/Light/Dark appearance.
- **Web UI and server rebuilt** — the wasm dist was rebuilt with the reworked
  theme CSS/logic and vendored in `android/native/web-dist/`; the embedded
  `libtanoshi.so` was rebuilt with it. Theme presets, the custom
  Primary/Secondary/Accent builder and the per-device `localStorage`
  behaviour are unchanged.
- **Version 0.9.1** — `versionCode` 901; the `android-v0.9.1` tag produces
  `versionCode` 901. The APK filename scheme is unchanged.

Previous release — **0.9.0** — and its features:

## What's new in 0.9.0

- **Per-device web-UI themes** — the embedded Tanoshi frontend has a new
  **Settings → Appearance → Themes** section with 12 preset palettes
  (Monochromatic Blue, Complementary Orange, Triadic Yellow + Teal + Magenta,
  Analogous Green + Blue + Teal, High Contrast Black + Yellow + White, Google
  Material Blue, Airbnb Coral, Wise Mint, Duolingo Amber, Headspace Warmth,
  Slack Aubergine, Pinterest Cherry). A **New theme** button at the bottom
  opens Primary/Secondary/Accent colour swatch grids (20 colours each);
  picking colours and pressing **Apply** sets a custom palette, and **Reset to
  default** clears it. The palette changes the app's top bar (with
  luminance-based text colour), accent borders and hover colours. It is a
  **web-UI** theme, separate from the Android app's Dark/Light/Nord/Catppuccin
  themes; see [Web UI themes](#web-ui-themes).
- **Stored per device only** — the selection lives in the browser's
  `localStorage` under `settings:theme-palette` and is re-applied at boot;
  nothing is stored server-side. Other devices keep their own theme, and a new
  device/browser starts at **Auto/default**.
- **Web UI rebuilt** — the embedded frontend was rebuilt with the 0.8.0 web
  patches, the infinite-scroll and app-only extension-management patches, and
  the new `native/patches/0004-web-ui-themes.patch` (+ copied
  `theme_settings.rs`); the dist is vendored in `android/native/web-dist/` and
  embedded in the rebuilt `libtanoshi.so`.
- **Version 0.9.0** — `versionCode` 900; the `android-v0.9.0` tag produces
  `versionCode` 900. The APK filename scheme is unchanged. Includes the 0.8.0
  features (Delete History, catalogue filter sheet, Layout Preference presets,
  Tap Area) and everything from earlier releases.
- **Repository renamed** — the project now lives at
  [`spainclaro-debug/tanoshi-mobile`](https://github.com/spainclaro-debug/tanoshi-mobile);
  the old `tanoshi-termux` URL redirects, and the app updater already targets
  the new repository. The Termux package tarball/asset names
  (`tanoshi-termux-aarch64.tar.gz`) are unchanged package artifact names.

Previous release — **0.8.0** — and its features:

## What's new in 0.8.0

- **Delete History** — the History page has a **Delete History** button in the
  top right. Confirm with **Delete** and every read-history entry for the
  signed-in account is cleared (inline **Cancel** aborts).
- **Catalogue filter sheet fixed** — the filter sheet no longer inherits
  another source's cached filters, and genre/tag groups now start **expanded**
  so the available choices are visible right away. Sources that expose no
  filters show a clear message instead of a blank sheet.
- **Layout Preference presets** — Settings → **Layout Preference** stores
  reader-option presets (Reading mode, Padding, Display mode, Direction,
  Background, Fit) **on the device**. It ships the **default** preset (Paged,
  Padding disabled, Single, Left to Right, White, Fit All); use
  **Add New Preset** to snapshot your current reader options. The selected
  preset is remembered per device and a new device starts at Default even
  against the same server.
- **Reader Tap Area** — Settings → Reader → **Tap Area** picks
  "bottom right: NEXT, bottom left: PREV" or "bottom left: NEXT, bottom right:
  PREV"; the reader's tap zones follow the setting.
- **Version 0.8.0** — `versionCode` 800; the `android-v0.8.0` tag produces
  `versionCode` 800. The embedded web UI was rebuilt from source with the new
  patches and is also included in the rebuilt Termux package.

Previous release — **0.7.1** — and its features:

## What's new in 0.7.1

- **Readable Install/Update button in the Add-extension list** — catalog rows
  now stack the repository label and the **Ported to Rust** badge vertically
  (with **Disabled**/**Update available** below them) instead of squeezing them
  next to the action button, and the **Install/Update/Reinstall** button has a
  minimum width with single-line text. No behaviour or API change; installed
  cards are unchanged.

Previous release — **0.7.0** — and its features:

## What's new in 0.7.0

- **Extension management is app-only** — the embedded Tanoshi web UI now
  renders **backend-only**: Settings → Sources shows only the **Installed**
  sources list (the **Available** list is gone), the source list no longer has
  **Install/Update** buttons, and the per-source settings page no longer has
  an **Uninstall** button. Those controls never worked on Android, where
  plugins are native files loaded by the server. Adding, updating,
  reinstalling, disabling and removing extensions is done **only** in the
  app's [Sources tab](#extensions-sources-tab). See
  [The embedded web UI](#the-embedded-web-ui).
- **Dedicated extension repository: `tanoshi-extension`** — the built-in
  repository now points at
  [github.com/spainclaro-debug/tanoshi-extension](https://github.com/spainclaro-debug/tanoshi-extension)
  (raw `main/android/extensions/index.json`, label **`tanoshi-extension
  (default)`**). It hosts the nine prebuilt `aarch64-linux-android` plugins,
  the catalog (plus a root `tanoshi-extensions.json`), the ported extension
  sources, patches and `tools/build_extension.sh`. It can also be added
  manually with the GitHub link or the raw catalog URL — see
  [The extension catalog](#the-extension-catalog).
- **Web UI rebuilt from source** — the embedded wasm frontend was rebuilt
  with `patches/tanoshi-infinite-scroll.patch` +
  `native/patches/0003-web-ui-hide-source-management.patch`; the infinite-scroll
  catalogue is preserved and the rebuilt dist is vendored in
  `android/native/web-dist/` and embedded in `libtanoshi.so`.
- **Version 0.7.0** — `versionCode` 7 in local builds; the `android-v0.7.0`
  tag produces 700. The APK filename scheme is unchanged. Includes the 0.6.0
  custom repositories / Enable/Disable / exported-sources tooling and all
  earlier features.

Full details: [`CHANGELOG.md`](CHANGELOG.md).

---

## Requirements

**To run the app**

- Android **8.0 (API 26)** or newer, on an **arm64 / aarch64** device.
  (32-bit and x86/x86_64 builds are not bundled — see
  [`ASSUMPTIONS_AND_OPEN_QUESTIONS.md`](ASSUMPTIONS_AND_OPEN_QUESTIONS.md).)
- ~250 MB free space for the app and a fresh library.
- To use **in-app updates**: a network connection and the "Install unknown
  apps" permission for Tanoshi (the app links to the system setting).
- To install extensions from the **Sources** tab: a network connection (the
  catalog is fetched from GitHub) and a server restart after each change.

**To build the APK (app only)**

- **JDK 17** (`JAVA_HOME` pointing at a JDK 17; Gradle 8.9 + AGP 8.7.3 are
  configured for it).
- Android SDK with **Platform 35** and **Build-Tools 35.0.0**
  (`ANDROID_HOME` / `ANDROID_SDK_ROOT`, or `android/local.properties`).
- Android Studio **Ladybug (2024.2.1) or newer** if you prefer the IDE; its
  embedded JDK is 17-compatible.

**To rebuild the native binaries** you additionally need Android NDK r26+,
Rust 1.99.1 with the `aarch64-linux-android` target, CMake and
`pkg-config` — see [`native/README.md`](native/README.md). You do **not** need
any of that for a normal APK build.

---

## Building the APK

### Android Studio

1. Open the `android/` directory (not the repository root) as a project.
2. Let Gradle sync; select the embedded JDK 17 if prompted.
3. **Build → Build Bundle(s)/APK(s) → Build APK(s)**.

The debug APK is written to
`android/app/build/outputs/apk/debug/tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`,
where `<yyyy-MM-dd>` is the local build date (CI passes the UTC date via
`-PapkDate`).

### Command line

```bash
cd android
./gradlew :app:assembleDebug
```

The first run downloads the Gradle 8.9 distribution and the Compose
dependencies; later builds are cached. Other useful tasks:

```bash
./gradlew :app:assembleRelease   # release variant (same shared signing key)
./gradlew :app:installDebug      # build + install on a connected device
./gradlew clean                  # remove build outputs
```

Local builds use the defaults in `app/build.gradle.kts` (**0.9.1**,
`versionCode` 901) and today's date in the APK filename. CI derives both from
the release tag; you can do the same manually:

```bash
./gradlew :app:assembleDebug -PappVersionName=0.9.1 -PappVersionCode=901 \
  -PapkDate=2026-11-14
```

`-PapkDate` is optional locally (the local date is used) and is what CI sets
from the UTC build date.

### Signing

The app is sideloaded only, so the signing key does not need to be a secret —
it only needs to be **stable**. Every release (CI and local) is signed with
the key committed at
[`keystore/tanoshi-signing.jks`](keystore/tanoshi-signing.jks):

| Field | Value |
| --- | --- |
| Keystore | `android/keystore/tanoshi-signing.jks` (JKS) |
| Key alias | `tanoshi` |
| Store / key password | `tanoshi-android` |

`app/build.gradle.kts` signs **both** the `debug` and `release` build types
with this key when the file exists, and falls back to the local Android debug
keystore (`~/.android/debug.keystore`) when it does not. CI builds the `debug`
variant, whose application id is `dev.spainclaro.tanoshi.debug`; the release
variant uses `dev.spainclaro.tanoshi`. In-app updates replace the installed
app, so they only work within the same package id — i.e. the published
`tanoshi-mobile-server-release-<date>.apk` replaces a previously installed
build of the `.debug` package. Installs made from the older
`app-debug.apk` name are replaced the same way.

Why commit a keystore? Android refuses to install an update over an app signed
with a different key, and the [in-app updater](#in-app-updates) hands the next
release APK to the system installer. A per-machine debug key would make every
build incompatible. The full rationale, key details and replacement
instructions are in [`keystore/README.md`](keystore/README.md).

**Upgrading from 0.1.0:** 0.1.0 was built with an ephemeral CI debug key, so
0.2.0 or later cannot be installed over it (Android reports a signature
mismatch). Uninstall 0.1.0 first — this deletes the app's private data. See
[Installing and updating](#installing-and-updating).

**Using your own key:** for a private build, replace
`android/keystore/tanoshi-signing.jks` (or point the `signingConfigs` block at
your own file and passwords) and rebuild. Changing the key breaks in-app
updates for already-installed builds: users must uninstall once. See
[`keystore/README.md`](keystore/README.md) for the `keytool` command and the
details.

### CI

[`.github/workflows/android-apk.yml`](../.github/workflows/android-apk.yml)
builds `:app:assembleDebug` on every push, on pull requests that touch
`android/**` or the workflow, and on manual dispatch. It installs JDK 17
(Temurin) and the Android SDK packages `platforms;android-35` and
`build-tools;35.0.0` with `sdkmanager`, runs Gradle with the wrapper, and
uploads the APK as the **`tanoshi-android-debug-apk`** artifact; the file
inside it is `tanoshi-mobile-server-release-<UTC date>.apk`. No third-party
Android setup/release actions are used: the SDK is provisioned with
`sdkmanager` and releases are published with the preinstalled GitHub CLI.

Releases are created only for tags of the form **`android-vX.Y.Z`**, for
example `android-v0.9.1`. For a tag, CI derives everything from the tag and
the build date:

- `versionName` = `X.Y.Z` from the tag;
- `versionCode` = `X * 10000 + Y * 100 + Z` (so `android-v0.9.1` → `901`);
- the APK filename date = the UTC build date (`-PapkDate=$(date -u +%F)`).

The build command passes these with `-PappVersionName`, `-PappVersionCode` and
`-PapkDate`, and the dated debug APK is attached to the generated GitHub
release — exactly what the [in-app updater](#in-app-updates) consumes.
Without a release tag, CI keeps the Gradle version defaults (0.9.1 /
versionCode 901) and still uses the UTC build date; a local build that omits
`-PapkDate` uses the local date.

The plain `v*` tags that belong to the Termux package releases
(`v0.40.0-termux.3`, …) do not trigger this workflow, so the two release
trains cannot collide.

---

## Installing and updating

Download `tanoshi-mobile-server-release-<yyyy-MM-dd>.apk` from the
[latest release](https://github.com/spainclaro-debug/tanoshi-mobile/releases/latest)
(or a CI artifact), or build it yourself. Then either:

```bash
adb install -r android/app/build/outputs/apk/debug/tanoshi-mobile-server-release-*.apk
```

or copy the APK to the phone, open it, and allow "install unknown apps" for
the file manager/browser when Android asks.

The APK name carries the build date: CI uses the UTC build date, local builds
use the local date. Releases from 0.3.0 onward use
`tanoshi-mobile-server-release-<date>.apk`; the 0.1.0 and 0.2.0 release assets
were called `app-debug.apk` and are still valid downloads.

The app is **arm64-v8a only**. It will not install on 32-bit-only or x86
devices; the package manager reports a missing native library / incompatible
ABI.

### Updating

- **In-app:** once a release newer than the installed version is published,
  `Settings → Updates` offers **Download update**, then **Install update**. The
  app downloads the APK from the GitHub release and hands it to Android's
  package installer; the system asks you to confirm. The install stops the app
  (and the server), so reopen Tanoshi and start the server afterwards. See
  [In-app updates](#in-app-updates).
- **Manual:** download `tanoshi-mobile-server-release-<date>.apk` from the
  latest release and install it over the app, or use
  `adb install -r <downloaded.apk>`. Android accepts the update only because
  every release is signed with the shared key ([Signing](#signing)).
- **From 0.1.0:** 0.1.0 was signed with a throwaway CI debug key, so 0.2.0 or
  later will **not** install over it ("app not installed" / signature
  mismatch). Uninstall 0.1.0 once before installing a current release.
  Uninstalling deletes the app's private data (library, accounts, downloads)
  — copy `files/tanoshi/` out first if you need it (see
  [Storage layout](#storage-layout)). This is a one-time step: every later
  release shares the committed signing key and updates in place.

---

## First run

1. **Start the server** — open the app and tap **Start**. A foreground-service
   notification appears ("Running on port 8080") and the status card switches
   to `RUNNING` once the server answers on `http://127.0.0.1:8080/`.
2. **Create the admin account** — the app creates no default credentials.
   When the server reports that no account exists, a first-run dialog asks for
   a username (default `admin`, at least 3 characters) and a password (at
   least 8 characters) and registers the first account through the server's
   GraphQL `register` mutation with `isAdmin: true`.
   You can skip the dialog and register in the web UI instead, but do that
   immediately: until an account exists, anyone who can reach the port could
   claim the first (administrator) account.
3. **Open the web UI** — tap **Open in app** for the embedded WebView or
   **Open in browser** to use your normal browser at
   `http://127.0.0.1:<port>`. Log in with the account from step 2.
   To open it on another device on the same Wi-Fi, enable **Expose on LAN** in
   Settings, restart the server, then scan the
   [Share on your network QR code](#sharing-on-your-network-qr) from the
   Server screen.
4. **Install sources / configure** — the nine bundled extensions are already
   present in the web UI. Use the **Sources** tab to install, update or remove
   extensions from the repository catalog (see
   [Extensions (Sources tab)](#extensions-sources-tab)); the web UI itself is
   configured like any other Tanoshi server.

The server keeps running while the app is in the background, held by the
foreground service and a partial wake lock, until you tap **Stop** (in the app
or in the notification). Starting/stopping the server is always explicit: the
service does not silently resume after a reboot.

---

## Using the app

The app has four screens plus an embedded browser:

| Screen | What it does |
| --- | --- |
| **Server** | Server state (`STOPPED` / `STARTING` / `RUNNING` / `STOPPING` / `ERROR`), PID, port, bind address (`127.0.0.1` or `0.0.0.0`), uptime, Start/Stop, the local URL, **Open in browser**, **Open in app**, and (when LAN-exposed) the **Share on your network** QR card. The first-run admin dialog appears here. |
| **Sources** | Suwayomi-style extension manager: built-in + custom repositories, installed extensions (bundled/user/disabled), **Add extension** from merged catalogs, install/update/enable/disable/remove, and the restart prompt. See [Extensions (Sources tab)](#extensions-sources-tab). |
| **Logs** | Live server stdout/stderr with timestamps and stream colouring; auto-scroll toggle; copy-all and clear. `server.log` in the data directory mirrors the same output. |
| **Settings** | Appearance (Android app themes + web-UI themes), port, "Expose on LAN", raw `config.yml` editor, notification/battery permissions, in-app updates, and the storage paths. |
| **Browser** | WebView pointed at the local server (JavaScript and DOM storage enabled) with Back and Reload buttons; the catalogue auto-loads the next page as you scroll. The system back button exits the WebView before leaving the app. |

**Default port: 8080.** The advertised URL uses `127.0.0.1`; when LAN exposure
is on, other devices on the same network can use the phone's IP address
instead.

The notification has a **Stop server** action, so you can stop the server
without reopening the app. If you deny the notification permission on
Android 13+, the foreground service still runs but its notification is not
shown — keep the app icon/battery settings in mind when troubleshooting.

The **Settings** screen also holds the app-only [Appearance](#themes) theme
picker and the [Updates](#in-app-updates) section; both are described below.

---

## Sharing on your network (QR)

When the server is **running** and bound to `0.0.0.0` — i.e. **Expose on LAN**
is enabled in Settings *and* the server was restarted after enabling it — the
Server screen shows a **Share on your network** card:

- a QR code encoding `http://<host-ipv4>:<port>`, generated with ZXing (pure
  Java, no native code) off the main thread;
- the host `ip:port` and the full URL below it, with a copy button;
- a warning that this only works on the same Wi-Fi/Ethernet network and that
  the web UI uses plain HTTP.

Scanning the code with another device on the same network opens the Tanoshi
web UI directly in that device's browser. The app uses the first IPv4 address
of the active network when `ConnectivityManager` reports it as Wi-Fi or
Ethernet, and otherwise falls back to enumerating network interfaces —
always skipping loopback and link-local (`169.254.x.x`) addresses. The address
is re-resolved whenever the screen resumes, so it follows Wi-Fi changes.

If the card cannot show a QR code it explains what is missing instead: enable
Expose on LAN, start the server, restart the server to apply a bind-address
change, or connect to Wi-Fi/Ethernet. The card is informational — it never
changes the bind address itself.

**Security:** the other device reaches this server over your LAN in plain HTTP,
so anyone on that network who knows the address can reach the login page. Only
share on a network you trust, keep a strong admin password, and turn Expose on
LAN off when you don't need it — see [Security notes](#security-notes).

---

## The embedded web UI

The Tanoshi web UI (the same UI the Termux package serves) is compiled into
the server binary: the wasm bundle is embedded into `libtanoshi.so`, which
serves it over the loopback HTTP port. The app's **Open in app** screen is a
`WebView` pointed at that URL, and **Open in browser** hands the URL to your
browser. Both show exactly the same UI.

**Extension management is app-only (0.7.0).** The embedded web UI is
**backend-only** for extensions: Settings → Sources shows only the
**Installed** sources list and each source's preferences — the
**Available** sources list, the source-list **Install/Update** buttons and the
per-source **Uninstall** button are hidden (they never worked on Android,
where extensions are native `.so` files loaded by the server). Adding,
updating, reinstalling, disabling and removing extensions is done exclusively
in the app's [Sources tab](#extensions-sources-tab).

**Rebuilt from source (0.7.0).** The embedded dist was rebuilt from the
Tanoshi web workspace with `patches/tanoshi-infinite-scroll.patch` (the
auto-loading catalogue) and `native/patches/0003-web-ui-hide-source-management.patch`
(the extension-management hide), then vendored in `android/native/web-dist/`
and embedded in `libtanoshi.so`. The infinite-scroll catalogue is preserved.
With the original setup, a sentinel below the catalogue grid
(`catalogue-scroll-sentinel`) is watched with
sentinel below the catalogue grid (`catalogue-scroll-sentinel`) is watched with
an `IntersectionObserver`: when it comes within about 600 px of the viewport,
the next page is fetched automatically, so normally you never have to press
anything. The **Load More** button stays as a manual fallback and changes to
*No more results* once a source runs out of pages.

Because the UI lives inside the server binary, **changing web-UI behaviour
means rebuilding `libtanoshi.so`** (see [`native/README.md`](native/README.md))
and shipping a new APK — rebuilding the Android app alone is not enough. The
web UI also keeps its own Tanoshi theme; the app's [themes](#themes) affect
only the Android UI around it.

## Themes

`Settings → Appearance` selects the Material 3 colour scheme for the app UI:

| Theme | Notes |
| --- | --- |
| **Dark** (default) | Near-black blue-grey surfaces with the amber Tanoshi accent. |
| **Light** | Warm paper surfaces with a darker amber primary; not an inverted dark scheme. |
| **Nord** | Polar Night backgrounds, Snow Storm text and Frost/Aurora accents. |
| **Catppuccin** | Mocha palette: base/mantle/crust surfaces with mauve, blue and peach accents. |

The choice applies immediately, is persisted in `AppSettings.themeId`
(unknown ids fall back to Dark) and also switches the system-bar icon
contrast. It affects the Android UI only — the embedded Tanoshi web UI has its
own theme, described next.

### Web UI themes

The embedded Tanoshi frontend (the same UI you open in **Open in app** or a
browser) has its own **Settings → Appearance → Themes** section, added in
0.9.0:

- **12 preset palettes**, each with a primary/secondary/accent triplet:
  Monochromatic Blue, Complementary Orange, Triadic Yellow + Teal + Magenta,
  Analogous Green + Blue + Teal, High Contrast Black + Yellow + White, Google
  Material Blue, Airbnb Coral, Wise Mint, Duolingo Amber, Headspace Warmth,
  Slack Aubergine, Pinterest Cherry. A preset row shows the three colour
  swatches and its name; select it and press **Apply**.
- **New theme** at the bottom opens **Primary**, **Secondary** and **Accent**
  colour swatch grids (20 colours each). Pick one colour per group and press
  **Apply** to use the custom palette; **Reset to default** clears the custom
  theme and returns to the stock appearance (your existing Auto/Light/Dark
  appearance choice).
- The section shows which theme is applied (**Applied: …**) and confirms
  actions with a snackbar. Since 0.9.1 the palette is applied across the UI:
  - **top and bottom banners** → background **primary** (text/buttons adapt to
    its luminance; the active bottom-bar item and the top-bar title use a
    readable accent);
  - **all icons** (SVG) → **secondary**;
  - **library-cover unread-chapter badge** → background **secondary** with
    contrast-adaptive text;
  - **page background** → **accent** mixed at **30%** opacity over the mode's
    base (`color-mix`: 30% accent over `#f4f4f4` in light mode, over `#0a0a0a`
    in dark; the derived background shades and modal background are tinted
    too);
  - **headers/titles** → **accent**, with readable variants for the top-bar
    title against the primary banner and manga-cover titles against the dark
    thumbnail gradient;
  - **settings panels** (`.reader-settings` and the settings page content) →
    **accent** panel background with adaptive panel text;
  - **all text** → contrast-adaptive **per mode**: `theme_settings.rs`
    computes separate light/dark text colours from the tinted background plus
    readable heading, banner, panel and badge foregrounds, applied through the
    `body.themed` class. **Reset to default** removes the class/variables and
    restores the normal Auto/Light/Dark appearance.

**Per-device storage.** The theme is stored **only in the browser's
`localStorage`** under the key `settings:theme-palette` (a small JSON object
with `kind`, `name`, `primary`, `secondary`, `accent`) and is re-applied at
boot before the UI renders. It is **not stored server-side or synced**: each
device/browser keeps its own selection, every new device/browser starts at the
Auto/default appearance, and clearing site data or pressing **Reset to
default** removes it. Other Android app themes ([above](#themes)) are
independent of this web-UI theme.

## In-app updates

`Settings → Updates` shows the installed version and a **Check for updates**
button. Tapping it asks this repository's GitHub API for releases, considers
only published (non-draft, non-prerelease) tags starting with `android-v`, and
compares them numerically with the installed version (debug builds carry a
`-debug` version suffix such as `0.9.1-debug`; the updater strips it before
comparing).

When a newer release exists the section shows its version, tag, a release-notes
excerpt and the download size, with a **Download update** button (progress
bar). After the APK is downloaded, **Install update** hands it to Android's
`PackageInstaller`; the system then shows its own confirmation dialog and
replaces the app.

Requirements and limitations:

- **Network.** The check uses `https://api.github.com` without a token; if
  GitHub's rate limit is hit the app shows a message and you can try again
  later.
- **Install permission.** Android requires the "Install unknown apps"
  permission for Tanoshi (`REQUEST_INSTALL_PACKAGES` is declared; the user
  grants it). Without it, the section shows a warning and an **Allow app
  installs** button that opens the system setting.
- **Same signing key.** Updates install only because every release is signed
  with the shared key — see [Signing](#signing). An APK signed with another
  key is rejected by the system.
- **Download integrity.** The download is written to a temporary file, its
  size is checked, and the SHA-256 is verified when the GitHub release asset
  exposes a digest. Downloads land in the app cache and stale ones are cleared
  first.
- **Asset naming.** 0.3.0 releases publish
  `tanoshi-mobile-server-release-<date>.apk`; the updater prefers asset names
  starting with `tanoshi-mobile-server`, accepts any other `.apk` asset as a
  fallback, and still understands the legacy `app-debug.apk` name used by
  0.1.0/0.2.0 releases.
- **The system installs it.** Tanoshi never installs anything silently: you
  approve each install in the system dialog, and Android stops the app (and
  therefore the server) while it is replaced — reopen Tanoshi and start the
  server afterwards.
- **Manual only.** There is no background/automatic update check, and no
  delta or incremental update: each update is a full APK download.
- **Channel.** Only GitHub releases of
  [`spainclaro-debug/tanoshi-mobile`](https://github.com/spainclaro-debug/tanoshi-mobile/releases)
  tagged `android-v*` are considered; there is no Play Store channel.
- **From 0.1.0** the updater cannot help, because 0.1.0 predates it and is
  signed with a different key — see the one-time uninstall note in
  [Installing and updating](#installing-and-updating).

---

## Extensions (Sources tab)

The bottom navigation has a **Sources** tab (between Server and Logs): it is a
Suwayomi-style extension manager for the Tanoshi plugins that provide manga
sources. It manages **repositories**, lists the merged catalogs, and lets you
install, update, enable, disable and remove extensions.

### What the tab shows

- **Repositories** lists the built-in **`tanoshi-extension`** repository
  (label **`tanoshi-extension (default)`**, cannot be removed) and every custom
  repository you added, with its catalog URL and a Remove action. **Add
  repository** accepts a direct HTTPS `index.json` URL or a GitHub repository
  link — see [Custom extension repositories](#custom-extension-repositories).
  A refresh button in the header reloads repositories and catalogs.
- If a repository cannot be fetched, a **"Some repositories could not be
  loaded"** card lists each failure (`label: reason`) with a **Retry** action;
  the other repositories still load.
- **Installed** lists every extension the app can see: bundled plugins
  (`lib<name>.so` in the APK), user-installed copies
  (`filesDir/tanoshi/plugins/<name>.so`) and extensions marked disabled.
  Badges show **Bundled**, **Installed by you**, **Overrides bundled**,
  **Ported to Rust**, **Update available**, the **repository label** it came
  from, and **Disabled**.
- Available actions: **Reinstall/Update** (from the catalog; also clears a
  disabled marker), **Enable/Disable** (writes/removes the server's disabled
  marker), and **Remove** (user-installed copies only; bundled extensions can
  never be deleted). **Add extension** opens the merged catalog with Install /
  Update / Reinstall, per-entry repository badges, progress and repository
  failure warnings.
- Every install/update/remove/enable/disable makes the tab show a **Server
  restart needed** banner and a snackbar ("Restart the server to load the new
  sources") with a **Restart server** (or **Start server**) action — Tanoshi
  only reads plugins and the disabled marker at startup.

### How installation works

The app (not the server) performs the download on a background thread using
`HttpURLConnection`:

1. Fetch every configured repository's catalog (built-in first, then custom
   repositories in the order they were added) and merge the entries by
   normalized extension name; earlier repositories win on duplicates.
2. Download the entry's HTTPS `.so` to
   `filesDir/tanoshi/plugins/<name>.so.part`, streaming with progress.
3. Verify the byte count and, when the catalog provides one, the SHA-256 while
   streaming; files smaller than 256 KB are rejected as implausible plugins.
4. Atomically rename to `<name>.so` (replacing a previous user copy), clear any
   disabled marker for that name, and mark **restart required**.

The server is not touched while installing; the restart loads the new file.

### The extension catalog

The built-in catalog is served from the dedicated
[`tanoshi-extension`](https://github.com/spainclaro-debug/tanoshi-extension)
repository at
`https://raw.githubusercontent.com/spainclaro-debug/tanoshi-extension/main/android/extensions/index.json`
(it also mirrors the catalog as a root `tanoshi-extensions.json`). The copy in
this repository at [`extensions/index.json`](extensions/index.json) is kept for
tooling/back-compatibility; **tanoshi-extension is the canonical catalog**.
Custom repositories use the same format:

```json
{
  "version": 1,
  "updated": "2026-11-17",
  "extensions": [
    {
      "name": "mangadex",
      "displayName": "MangaDex",
      "description": "MangaDex source for the Tanoshi mobile server.",
      "url": "https://raw.githubusercontent.com/spainclaro-debug/tanoshi-extension/main/android/extensions/plugins/libmangadex.so",
      "sha256": "d6b0e83f3b02e180f4d65e09fa083bcd14f8b10ccee1bd11ff7983b6566c7a47",
      "size": 4461728,
      "ported": false
    }
  ]
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `version`, `updated`, `note` | no | Documentation for humans; the app reads only `extensions[]`. |
| `name` | yes | Bare extension id, `^[a-z0-9][a-z0-9_-]{0,63}$` (e.g. `mangadex`). |
| `displayName` | no | Name shown in the app (falls back to a prettified `name`). |
| `description` | no | Short description shown under the name. |
| `url` | yes | **HTTPS** URL of the prebuilt `aarch64-linux-android` `.so`. |
| `sha256` | no | Lowercase 64-hex SHA-256 of the file; verified on install when present. |
| `size` | yes | File size in bytes; used for progress/verification (must be > 256 KB to install). |
| `ported` | no | `true` for extensions ported to Rust in this repository; shows the "Ported to Rust" badge (defaults `false`). |

Malformed entries are skipped without hiding the rest of that catalog. The
nine entries in the built-in catalog mirror the nine plugins prebuilt in
`tanoshi-extension` (`android/extensions/plugins/lib<name>.so`), with matching
`sha256`/`size`; the same plugin files are bundled in the APK so a fresh
install already has all nine. The `tanoshi-extension` repo also contains the
ported sources (`sources/`), the patches used to build the plugins, and
`tools/build_extension.sh`.

You can also add that repository manually in
**Sources → Add repository**, with either:

- `https://github.com/spainclaro-debug/tanoshi-extension`, or
- `https://raw.githubusercontent.com/spainclaro-debug/tanoshi-extension/main/android/extensions/index.json`.

### Custom extension repositories

Open **Sources → Repositories → Add repository** and paste one of:

- a **direct HTTPS catalog URL** ending in `.json` (for example another
  repository's raw `index.json`), or
- a **GitHub repository link** such as `https://github.com/owner/repo`
  (`.git` suffix and a trailing slash are accepted).

For a GitHub link the app probes, in order:

```
https://raw.githubusercontent.com/owner/repo/main/android/extensions/index.json
https://raw.githubusercontent.com/owner/repo/master/android/extensions/index.json
https://raw.githubusercontent.com/owner/repo/main/tanoshi-extensions.json
https://raw.githubusercontent.com/owner/repo/master/tanoshi-extensions.json
```

and stores the first URL that returns a non-empty, valid catalog. A direct URL
is fetched and validated immediately (it must be HTTPS, parse as a catalog and
list at least one extension). Custom repositories are persisted in the app's
settings, up to **10** of them; duplicates are a no-op. The built-in
`tanoshi-extension` repository is always present and cannot be removed or
disabled. Removing a custom repository only removes it from the app list —
extensions you installed from it stay installed.

Catalogs from all repositories are **merged** for display/install:
deduplication is by normalized extension name and the built-in repository wins
over custom ones, then earlier-added custom repositories win over later ones.
A failing repository does not block the others; its error is shown in the
failures card and can be retried.

### Enable, disable and uninstall

Tanoshi loads extensions and the disabled list only at server start, so every
change below needs a **server restart** (the app offers it).

- **Disable / Enable** (app): the app maintains the server's marker file
  `files/tanoshi/plugins/.tanoshi-disabled` — one normalized extension name
  per line (comments with `#` ignored, `lib` prefix/`.so` suffix stripped,
  names lowercased). The server skips every extension listed there on the next
  start and shows a **Disabled** badge in the app. Disabling works for bundled
  and user-installed extensions and does not delete files.
- **Remove** (app, user-installed copies only): deletes
  `files/tanoshi/plugins/<name>.so` and clears the disabled marker, so a
  bundled copy with the same name becomes active again after a restart.
  Bundled extensions cannot be deleted from the app; their card has no Remove.
- **Reinstall / Update** (app): replaces the user copy (or installs one over a
  bundled extension) and **clears** the disabled marker, re-enabling the
  source.
- **From 0.7.0 the web UI no longer exposes uninstall.** The server still has
  the uninstall handling for older clients: for a user-installed copy it
  deletes the file; for a bundled extension it writes the same
  `.tanoshi-disabled` marker instead of trying to delete the read-only plugin.
  In the app, use **Disable** for bundled extensions and **Remove** for user
  copies.
- Bundled extensions disabled this way stay visible in the app as
  **Disabled**; use **Enable** to bring them back. A removed user-installed
  copy with no bundled equivalent disappears from **Installed** (it can be
  reinstalled from **Add extension**, and the disabled marker was cleared).

### How user-installed plugins are loaded (technical note)

Android 10+ forbids executing or `dlopen()`ing code from app-writable storage
(W^X/SELinux), which is why bundled plugins ship as `lib*.so` in
`nativeLibraryDir`. To support user-installed plugins the bundled server
(0.5.0+) was rebuilt with a custom loader; the app still targets API 35:

- The server loads plugins from **two directories**: the writable
  `filesDir/tanoshi/plugins` first, then `nativeLibraryDir` (passed via
  `TANOSHI_PLUGIN_LOAD_DIR`). A user copy wins over a bundled copy with the
  same name; bundled plugins are still loaded normally.
- `.tanoshi-disabled` is read from the writable plugin directory at startup;
  listed extension keys are skipped (`skipping disabled extension ...` in the
  server log), and loading a disabled extension by name fails with
  `extension <name> is disabled`.
- Support libraries in `nativeLibraryDir` (`libtanoshi.so`, `libarchive.so`,
  `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`, other
  `libandroidx*`/`libdatastore*` files) and files under 256 KB are never
  treated as extensions, which is what produced the old
  `undefined symbol: plugin_declaration` errors on some devices.
- For a plugin outside `nativeLibraryDir`, the extension worker reads the
  `.so` bytes, copies them into an anonymous **memfd**, and loads the library
  from that file descriptor with `android_dlopen_ext` +
  `ANDROID_DLEXT_USE_LIBRARY_FD`. If that path fails it falls back to a regular
  `dlopen()` and reports both errors. This is the same class of technique used
  by other on-device runtimes; it does not lower `targetSdk`.
- Plugin compatibility is unchanged: every plugin must embed the same
  `tanoshi-lib` version as the server (`0.38.0` for Tanoshi `0.40.0`) and
  match the device ABI (`arm64-v8a`). Incompatible plugins fail the server's
  version check and are ignored (see the Logs tab).
- User-installed copies and `.tanoshi-disabled` live in
  `files/tanoshi/plugins/` and are not part of the APK; app updates keep them,
  but uninstalling the app deletes them with the rest of the app data.

### Porting and publishing extensions

The canonical extension repository is
[**`tanoshi-extension`**](https://github.com/spainclaro-debug/tanoshi-extension).
It contains everything needed to build and publish plugins:

```
tanoshi-extension/
├── android/extensions/
│   ├── index.json                 # catalog the app fetches
│   └── plugins/lib<name>.so       # prebuilt aarch64 plugins (9)
├── tanoshi-extensions.json        # same catalog at the repo root (convenience)
├── sources/                       # Rust sources of the five ported extensions + template
├── patches/                       # patches applied to the tanoshi-extensions workspace
├── tools/build_extension.sh       # build a source into plugins/lib<name>.so
└── README.md
```

The app's built-in repository points at this repo's
`android/extensions/index.json` (label `tanoshi-extension (default)`); you can
also add it manually with the GitHub link or the raw catalog URL (see
[The extension catalog](#the-extension-catalog)).

This repository keeps copies of the same tooling for back-compatibility:
[`extensions/sources/`](extensions/sources/) (sources + template),
[`extensions/tanoshi-extension-sources-2026-11-17.zip`](extensions/tanoshi-extension-sources-2026-11-17.zip)
and [`native/build_extension.sh`](native/build_extension.sh). They behave the
same way as the `tanoshi-extension` versions; the dedicated repo is where the
published plugins/catalog live.

Building a source (Termux on the phone, or a desktop with `ANDROID_NDK_HOME`):

```bash
# in tanoshi-extension: build all five ported extensions...
bash tools/build_extension.sh
# ...or one crate (e.g. a template copy you are porting)
bash tools/build_extension.sh --crate sources/ported/mysource
```

The script clones `luigi311/tanoshi-extensions`, applies the repo's
`patches/`, copies the crate in, builds for `aarch64-linux-android` (16 KB-page
aligned), strips it to `android/extensions/plugins/lib<name>.so`, and prints
the SHA-256, size and a ready-to-paste catalog entry. The
`tanoshi-mobile` copy is at `android/native/build_extension.sh` and writes to
`android/extensions/plugins/` instead.

Publishing a new source to `tanoshi-extension`:

1. Copy `sources/template/` to `sources/ported/<your-source>/`, rename the
   crate, give it a unique extension `ID`, and implement the `Extension` trait
   methods (the five ported crates are the reference). Build it with
   `tools/build_extension.sh`.
2. Commit the built `android/extensions/plugins/lib<name>.so` and add/refresh
   the entry in `android/extensions/index.json` (and root
   `tanoshi-extensions.json`) with `name`, `displayName`, `description`, HTTPS
   `url`, `sha256`, `size`, `ported`.
3. Commit/push; the app shows the new/updated entry on the next **Sources →
   Add extension** catalog refresh (and **Update available** when a catalog
   hash changes).
4. To publish your own extension repository instead, the same layout works —
   see `android/extensions/repo-template/` in this repository — then add its
   GitHub link or raw `index.json` URL in **Sources → Add repository**.

Requirements and caveats: a plugin must be built for the **same
Tanoshi/`tanoshi-lib` version** (`0.38.0` for Tanoshi `0.40.0`; a mismatch is
reported in the Logs and the plugin is ignored) and the device ABI
(`arm64-v8a`). The app cannot compile extensions on-device; porting/building
happens on Termux or a desktop with the Android NDK. Published plugins are
native code and run with the server's privileges — only publish/install what
you trust.

### Limitations and security

- **Native code with exact version requirements.** A plugin must be built for
  the bundled Tanoshi version, the `tanoshi-lib` ABI and `arm64-v8a`. It
  cannot be compiled on the phone by the app, and incompatible `.so` files
  fail to load.
- **Only `.so` plugins from catalogs.** The app cannot install Tachiyomi /
  Keiyoushi `.apk` extensions (different runtime/format), and it does not
  accept arbitrary plugin URLs from the UI — only entries in the built-in or
  added repositories. Only HTTPS catalog URLs and GitHub links are accepted.
- **Trust.** Extensions run as native code with the Tanoshi server's
  privileges inside the app sandbox. Only install extensions and add
  repositories you trust. Installs are integrity-checked (SHA-256/size), but
  the built-in catalog comes from this repository's `main` branch and custom
  catalogs are third-party content — treat both as part of your trust
  boundary. The app can only verify a custom repository is a well-formed
  catalog; it cannot vouch for its contents.
- **Network.** Catalogs and downloads need internet access to GitHub /
  raw.githubusercontent.com (or whichever host serves a custom catalog). Failed
  repositories show a retryable warning while the others keep working.
- **Restart required.** The server reads plugins and the disabled marker at
  startup, so install/update/remove/enable/disable only apply after a restart
  (the UI offers the action).
- **Best-effort loader.** If a plugin does not appear even after a restart,
  check the Logs tab, confirm it matches the server/ABI, and use
  Remove/Disable to fall back to a bundled copy.

---

## Architecture

```
┌──────────────────────────────┐
│ Compose UI (MainActivity)    │
│  Server / Logs / Settings    │
│  WebView ────────────────────┼── HTTP ──┐
└──────────────┬───────────────┘          │
               │ start / stop             │ 127.0.0.1:<port> (default)
┌──────────────▼───────────────┐          │
│ TanhoshiService              │          │
│  foreground service          │          │
│ mediaPlayback|specialUse     │          │
│  wake lock + silent audio    │          │
│  ProcessBuilder              │          │
└──────────────┬───────────────┘          │
               │ exec                     │
┌──────────────▼───────────────┐    ┌─────▼──────────────────────┐
│ nativeLibraryDir/            │    │ Tanoshi HTTP server        │
│   libtanoshi.so  (server)    │    │ (child process)            │
│   libarchive.so              │    │ data: filesDir/tanoshi/    │
│   lib<plugin>.so  (plugins)  │    └────────────────────────────┘
└──────────────────────────────┘
```

### The server process

`TanhoshiService` runs in the app's own process (no binder plumbing needed)
and starts the server with `ProcessBuilder`:

- **Binary:** `<nativeLibraryDir>/libtanoshi.so` — see
  [Why `lib*.so` and `nativeLibraryDir`](#why-libso-and-nativelibrarydir).
- **Working directory:** the data home (`filesDir/tanoshi/`).
- **Environment:**

  | Variable | Value | Why |
  | --- | --- | --- |
  | `TANOSHI_HOME` | absolute `filesDir/tanoshi` | Tanoshi's data directory. |
  | `HOME` | absolute `filesDir` | Keeps any home-relative default inside the sandbox. |
  | `TANOSHI_LOG` | `info` | Server log level. |
  | `TANOSHI_BIND` | `127.0.0.1` (default) or `0.0.0.0` when LAN exposure is on | Listen address; upstream hardcodes `0.0.0.0`, the Android build reads this variable instead. |
  | `LD_LIBRARY_PATH` | `<nativeLibraryDir>` plus the system defaults | Lets the dynamic linker find `libarchive.so` next to the server. |
  | `TMPDIR` | the app's `cacheDir` | Scratch files stay in the app sandbox. |
  | `RUST_BACKTRACE` | `1` | Useful crash output in the log. |

- The service performs a readiness probe against
  `http://127.0.0.1:<port>/` and only then reports `RUNNING` (with a timeout
  and a process-death check).
- Stop sends `SIGTERM`, waits, then force-kills if needed. The service is
  `START_STICKY` and only restarts the server if the user had asked it to run.

### Why `lib*.so` and `nativeLibraryDir`

Android 10+ enforces W^X and SELinux rules: an app may no longer execute or
`dlopen` code written into its own writable data directory. The one executable
location the app owns is the installer-managed native library directory
(`nativeLibraryDir`). Therefore:

- the server binary is **shipped as `libtanoshi.so`** (and the plugins as
  `lib<name>.so`), so the package manager extracts them to
  `nativeLibraryDir/` during installation;
- `android:extractNativeLibs="true"` in `AndroidManifest.xml` and
  `packaging.jniLibs.useLegacyPackaging = true` in `app/build.gradle.kts` are
  required — without them libraries stay compressed inside the APK and are
  never extracted to a path that can be executed;
- the app never copies the server binary to `filesDir`.

The native libraries are linked 16 KB-page aligned (`-Wl,-z,max-page-size=16384`)
so they load on Android 15+ devices that use 16 KB memory pages. Rebuilds
must keep that flag — see [`native/README.md`](native/README.md).

### Plugins

The patched server loads plugins from **two directories**, writable first:
`filesDir/tanoshi/plugins/` (user-installed `<name>.so` files installed from
the Sources tab) and `nativeLibraryDir` (the nine bundled `lib<name>.so` files,
passed to the server as `TANOSHI_PLUGIN_LOAD_DIR`). A user copy with the same
name overrides the bundled one. The writable directory also holds the
`.tanoshi-disabled` marker: every extension key listed there is skipped at
startup (used by the app's Enable/Disable actions, and by the server's
uninstall handling for older web-UI builds that still exposed it). Server
executable and support libraries in
`nativeLibraryDir` (`libtanoshi.so`, `libarchive.so`, AndroidX/datastore libs)
are explicitly not treated as extensions.

Plugins outside `nativeLibraryDir` are loaded through the in-memory `memfd` +
`android_dlopen_ext` loader described in
[Extensions (Sources tab)](#extensions-sources-tab) — details of the server
changes are in [`native/README.md`](native/README.md).

The `plugin_path` in `config.yml` points at the writable
`filesDir/tanoshi/plugins/` directory, which doubles as the extensions'
preferences directory. Plugins are native code built for the exact
`tanoshi-lib` ABI (`0.38.0` for Tanoshi `0.40.0`) and the device ABI
(`arm64-v8a`).

### `libarchive`

Tanoshi uses `libarchive` to read chapter archives. The APK bundles
**libarchive 3.8.11**, built minimally: zlib is linked **statically into the
library** (so there is no separate `libz.so` runtime dependency), and OpenSSL,
libxml2 and iconv are disabled. The only dynamic dependencies left are libc
and libdl, and its SONAME is patched to plain `libarchive.so` so the server's
`LD_LIBRARY_PATH` lookup finds it.

### Foreground service and battery

- The service is declared as **`specialUse|mediaPlayback`**:
  - `specialUse` (with `FOREGROUND_SERVICE_SPECIAL_USE` and a
    `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` description) describes the
    self-hosted-server use case. The obvious alternative, `dataSync`, is
    capped at **6 hours per day** on Android 15+, which would stop a
    self-hosted server.
  - `mediaPlayback` (with `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) is used
    together with a hidden silent-audio loop so the app is treated as playing
    media while the server runs — see
    [Keeping the server alive in the background](#keeping-the-server-alive-in-the-background).
  - At runtime the app passes `specialUse|mediaPlayback` on Android 14+
    (API 34+), `mediaPlayback` on Android 10–13 (API 29–33; the
    `specialUse` type only exists on 14+) and no type on older releases, which
    predate foreground-service types.
- A **partial wake lock** keeps the process alive while the screen is off; it
  is released whenever the server stops.
- The silent keep-alive audio adds a small, continuous battery cost while the
  server runs; it is inaudible and creates no extra notification.
- `POST_NOTIFICATIONS` is requested on Android 13+ (API 33+); the app also
  offers an "ignore battery optimisations" shortcut, because many OEM builds
  kill long-running background apps aggressively.
- If the server process is killed unexpectedly, a watchdog restarts it
  automatically (bounded) — see the next section.
- The server does not auto-start at boot. You start it from the app.

---

## Keeping the server alive in the background

Android 12+ and many OEM ROMs are aggressive about killing background apps and
their **child processes** — the bundled `libtanoshi.so` server runs as a child
of the app process, so it is a target. The app ships two best-effort
mitigations while the server is running; the most reliable fixes for the
Android 12+ phantom-process killer are device-side settings (below). None of
this is a guarantee: a force-stop, an OEM "deep sleep" policy or the
phantom-process killer can still stop the server.

### Hidden silent-audio keep-alive

While the server runs, `TanhoshiService` loops a **generated one-second silent
WAV** through `MediaPlayer` and keeps the foreground service in the
`mediaPlayback` (plus `specialUse`) state:

- The WAV is generated at runtime into the app cache
  (`cacheDir/keepalive/silence.wav`): 44.1 kHz, 16-bit mono PCM silence,
  about 88 KB, reused between starts. Nothing is bundled with the APK.
- Playback uses media audio attributes, looping, **volume forced to 0**
  (inaudible even at maximum media volume) and a partial wake mode. There is
  **no MediaSession, no media-style notification, no audio-focus request and no
  extra UI** — the only notification remains the normal "Tanoshi server"
  foreground notification.
- It starts when the server starts and stops when the server stops (or fails,
  or the service is destroyed). **There is no setting or toggle**: stopping
  the server is the only way to stop it.
- Cost: a continuously looping silent audio stream (plus the wake lock) keeps
  the CPU awake, so expect a small, continuous battery drain while the server
  runs. It is silent and does not use the speaker.

`Settings → Permissions → Ignore battery optimisations` remains recommended on
top of this.

### Auto-restart watchdog

If the server process exits **unexpectedly** while you still want it running,
the service keeps the foreground notification, wake lock and silent audio
alive and restarts the server:

- at most **5 automatic restarts in a rolling 10-minute window**, about **3
  seconds** apart;
- the dashboard shows `STARTING` during the restart, and the Logs tab records
  lines such as `The Tanoshi server process was killed; restarting it
  automatically (attempt 1/5, in 3 s)`;
- a user-requested Stop (or Restart) cancels the pending restart;
- when the budget is exhausted the app records `giving up automatic restarts
  (5/5 attempts within 10 min)`, switches to `ERROR`, releases the wake lock
  and silent audio and stops the foreground service — the same terminal state
  as before 0.4.0.

The budget is rolling: after ten minutes without a restart the app can restart
the server automatically again.

### If the Android phantom-process killer is killing the server

Android 12 introduced the **phantom-process killer**. It limits app *child*
processes device-wide: when more than **32 phantom processes** exist on the
device, or when a child process uses excessive CPU, `ActivityManager` kills
those child processes. The Tanoshi server (plus its extension worker
processes) is exactly such a child; the app itself may survive, so the server
can disappear while the UI still works. The silent-audio keep-alive improves
the app's importance and how OEMs treat it, but it **does not exempt the app
from the phantom-process limits**.

How to tell it is the cause:

- The Logs tab shows `Tanoshi server process <pid> exited (code ...)`, then
  either the watchdog restart lines or (after 5 attempts) the `ERROR` state.
- From a computer with USB debugging:

  ```bash
  adb logcat -s ActivityManager
  ```

  Look for `Killing PhantomProcessRecord`, `Trimming phantom processes` or an
  `excessive cpu` reason. Other OEM task killers may log different messages.

Recommended workarounds (device-side, no root; **not required for everyone**):

- **Developer options (no ADB):** enable Developer options and look for
  **"Disable child process restrictions"** (Samsung/One UI and some other
  builds expose this toggle). Enable it, then restart the server.
- **ADB, Android 12 (API 31):**

  ```bash
  adb shell settings put global settings_enable_monitor_phantom_procs false
  adb shell settings get global settings_enable_monitor_phantom_procs  # -> false
  ```

- **ADB, Android 12L/13/14/15 (API 32+):**

  ```bash
  adb shell device_config put activity_manager max_phantom_processes 2147483647
  adb shell device_config get activity_manager max_phantom_processes   # -> 2147483647
  ```

  On those builds the `settings` flag may not exist or may not be honoured;
  the `device_config` value is the one the killer uses. A reboot may be needed
  before the change takes effect, and an OS update can reset it (re-check and
  re-apply afterwards).

Not every device or OEM build exposes these settings or honours them; the
commands can be ignored, restricted or reset by a system update. Trade-offs:
these are **device-wide** settings, not app settings — disabling the
phantom-process monitor affects every app, can increase background
activity/memory use and battery drain, and removes a protection Android added
deliberately. Only do it if you actually see the killer in `logcat`; on most
phones the silent audio, watchdog and battery-optimisation exemption are
enough. The app cannot change these settings itself.

If the server still dies, check the OEM's battery/"deep sleep"/autostart
settings and keep the app exempted from battery optimisation.

---

## Storage layout

Everything lives in the app's private files directory. For the debug build
the package is `dev.spainclaro.tanoshi.debug`; the release build uses
`dev.spainclaro.tanoshi`.

```
/data/data/<package>/files/tanoshi/      # TANOSHI_HOME
├── config.yml         # port, secret, absolute paths, extension timeouts
├── tanoshi.db         # SQLite: accounts, library, history
├── server.log         # mirrored stdout/stderr (rotates to server.log.1 at ~5 MB)
├── downloads/         # downloaded chapters
├── cache/             # image cache
├── manga/             # local ("Local source") folder
└── plugins/           # user extensions (<name>.so), .tanoshi-disabled marker, preferences
```

The bundled native libraries live in `nativeLibraryDir`
(`/data/app/.../lib/arm64/libtanoshi.so`, …), managed by the package
manager. `files/tanoshi/plugins/` holds only extensions you installed from the
Sources tab (plus the plugins' preference files); a user extension overrides a
bundled one with the same name.

Notes:

- The app has `android:allowBackup="false"`, so Android does not back this
  data up. **Uninstalling deletes it** (library, accounts, downloads).
- Downloaded update APKs are kept in the app cache (`cacheDir/updates/`),
  outside `files/tanoshi/`; Android may clear them at any time.
- To move data, copy the `tanoshi/` directory out of a debug build with
  `adb exec-out run-as dev.spainclaro.tanoshi.debug tar -C files -cf - tanoshi > tanoshi-data.tar`
  and push it back into a new install the same way.
- Nothing is written to shared/external storage.

---

## Settings and `config.yml`

The Settings screen controls the Android wrapper and the server's config:

- **Appearance** — the four Android app themes ([Themes](#themes)) and the
  embedded web UI's per-device themes ([Web UI themes](#web-ui-themes)).
- **Updates** — the in-app updater ([In-app updates](#in-app-updates)).
- **Port** — 1024–65535 (Android does not allow unprivileged apps to bind
  below 1024). The value is written to `config.yml` and remembered; the
  server must be restarted to listen on the new port. Saving the port (or the
  `config.yml`) works even before the server has ever been started: since
  0.3.0 the app creates the data directory as part of the save.
- **Expose on LAN** — when on, the next start uses `TANOSHI_BIND=0.0.0.0`, so
  other devices can reach `http://<phone-ip>:<port>`. The app warns that this
  is plain HTTP with no TLS. The default is **off** (loopback only). Once it is
  on and the server is running, the Server screen also shows the
  [Share on your network QR card](#sharing-on-your-network-qr).
- **`config.yml` editor** — shows the file as text, validates it on save and
  writes it atomically. Keep all paths **absolute** and inside the app's
  private directory; **`secret` must stay exactly 16 characters** because it
  is used as an AES-128 key. Changing the secret invalidates existing login
  tokens (everyone must log in again). Restart the server after editing.
- **Notifications** — requests `POST_NOTIFICATIONS` on Android 13+.
- **Ignore battery optimisations** — asks Android to exempt the app from
  battery optimisation (particularly relevant on OEM ROMs).
- The screen also shows the data directory path so you can find
  `server.log`, `config.yml` and `tanoshi.db`.

The config file is generated on first start with Tanoshi's current schema and
the app's private paths. The Termux package's `data/config.yml` from this
repository's root README is interchangeable in format, but paths must be
rewritten for the app sandbox — in practice, don't copy it across; let the
app generate it.

---

## Native binaries and rebuilding them

The APK embeds these files in
`app/src/main/jniLibs/arm64-v8a/`:

| File | Source |
| --- | --- |
| `libtanoshi.so` | Tanoshi v0.40.0 server (renamed from `tanoshi`) |
| `libarchive.so` | libarchive 3.8.11, minimal build with zlib linked statically (only libc/libdl dependencies, SONAME `libarchive.so`) |
| `libmangadex.so`, `libguya.so`, `libmanhwa18cc.so`, `libweebcentral.so`, `libatsumaru.so`, `libqiscans.so`, `libmangaball.so`, `libmadaradex.so`, `libhentainexus.so` | The nine extension plugins |

They are prebuilt and committed; a Gradle build does not compile Rust. Since
0.5.0 the bundled `libtanoshi.so` contains the dual-directory/in-memory plugin
loader, and 0.6.0 rebuilt it with the `.tanoshi-disabled` marker support and
the host-library filtering fix (see
[Extensions (Sources tab)](#extensions-sources-tab)); any extension you install
at runtime lives in `files/tanoshi/plugins/`, not in the APK. To update Tanoshi or the bundled plugins, run the pipeline documented in
[`native/README.md`](native/README.md):

- [`native/build_tanoshi.sh`](native/build_tanoshi.sh) — clone upstream,
  apply `patches/` + `native/patches/0001-android-app.patch`, embed the
  vendored [`native/web-dist/`](native/web-dist) (the package-3 infinite-scroll
  web UI) when the source tree has no `crates/tanoshi-web/dist`, and build the
  `aarch64-linux-android` server;
- [`native/build_libarchive.sh`](native/build_libarchive.sh) — cross-build the
  minimal `libarchive.so` with the NDK;
- [`native/sync_prebuilt.sh`](native/sync_prebuilt.sh) — copy the outputs into
  `jniLibs/arm64-v8a/` with the required `lib` prefix and verify them.

The root-of-repository Termux packaging (`tanoshi-termux-aarch64.tar.gz`,
`patches/`, `vendor/compress-tools-0.16.1/`, `lib/build.sh`) remains the
source of the bundled server binary and plugins: the Android pipeline reuses
its patch set, vendored crate and prebuilt extension plugins, and rebuilds
the server with the Android-app patch. Checked-in binaries can be replaced by
re-running the scripts and committing `jniLibs/arm64-v8a/` — no Kotlin or
Gradle change is needed.
Bit-for-bit reproducibility is not guaranteed (moving upstream extension
refs, toolchain versions, embedded build paths); see the "Reproducibility
caveats" section of the native guide.

---

## Security notes

- **HTTP only, no TLS.** Everything, including your password and session
  tokens, travels unencrypted. This is acceptable for loopback, not for a
  network you don't trust.
- **Loopback by default.** The server binds `127.0.0.1` unless you enable
  "Expose on LAN". Prefer a VPN (WireGuard, Tailscale) over LAN exposure for
  remote access; a reverse proxy with TLS is the other option.
- **LAN exposure is a login surface.** When enabled, any device on the same
  network can reach the login page over plain HTTP — the
  [QR share card](#sharing-on-your-network-qr) just makes that URL easier to
  open. Use a strong password and only turn it on when needed.
- **No default account.** No `admin`/`tanoshi123`-style credentials are baked
  in. Register the administrator immediately on first run; before the first
  account exists, whoever reaches the port could register it.
- **`config.yml` contains the JWT signing secret.** It lives in app-private
  storage; don't copy it into issues/logs. Rotating it logs everyone out.
- **Cleartext network policy.** The app's
  `network_security_config.xml` permits cleartext HTTP only for `127.0.0.1`,
  `localhost` and `10.0.2.2` (emulator loopback); WebView/browser traffic to
  the local server works, and cleartext to other hosts stays blocked.
- **Third-party content and native extensions.** The extensions scrape
  third-party sites; respect their terms of service and your local laws.
  Extensions downloaded in the Sources tab are **native code** that runs with
  the Tanoshi server's privileges. Only install extensions you trust; installs
  are SHA-256/size checked, but both the catalog and the binaries come from
  this repository's `main` branch.
- **Updates come from GitHub.** The updater contacts `api.github.com` and
  downloads release assets from this repository only. It never installs
  anything without the system confirmation dialog, and Android only accepts an
  APK signed with the same key as the installed app. Do not sideload APKs from
  other sources: they will not share this key or this app's data.
- **Shared signing key.** The committed keystore is for stable sideload
  updates, not secrecy. It is not a Play Store signing key; a Play Store
  release would need Play App Signing, a privacy policy and the `specialUse`
  foreground-service declaration reviewed — see the open questions document.

---

## Troubleshooting

Open the **Logs** screen first; the same output is appended to
`filesDir/tanoshi/server.log`, which rotates to `server.log.1` at ~5 MB.
Clearing the Logs screen clears the in-memory view only.

**"Native library missing" / the server cannot be started.** The APK was built
without `jniLibs` or for the wrong ABI. Confirm the APK contains all eleven
libraries (the server, libarchive and the nine plugins) and that the device is
arm64:

```bash
unzip -l android/app/build/outputs/apk/debug/tanoshi-mobile-server-release-*.apk \
  | grep 'lib/arm64-v8a'
adb shell getprop ro.product.cpu.abi     # expect arm64-v8a
```

Reinstall a correct build; do not copy `libtanoshi.so` into app storage —
Android will refuse to execute it there.

**Port already in use.** Another app (Termux, a previous Tanoshi, a proxy) is
on port 8080. Change the port in Settings or the `config.yml` editor, stop and
start the server. Keep it ≥ 1024.

**Saving the port or `config.yml` fails with `config.yml.tmp: open failed:
ENOENT`.** This was a 0.2.0 and earlier bug when the server had never been
started (the data directory did not exist yet). 0.3.0 creates the data
directory and the parent of `config.yml` before writing, so update to 0.3.0 or
later if you hit it.

**Server starts and immediately stops / `ERROR` with an exit code.** Check the
logs. Common causes: a `config.yml` with an invalid `secret` (the app repairs
non-16-character secrets on start), an unwritable data directory, or a crashed
plugin build. Restarting the app regenerates missing directories; deleting
`config.yml` makes the app generate a fresh one.

**The web UI has no Install/Update button or "Available" sources list, and no
Uninstall button on a source page.** This is intentional from 0.7.0: the
embedded web UI is **backend-only** for extensions (those controls never
worked on Android). Use the app's **Sources** tab to install/update/reinstall/
disable/remove extensions; the web UI still lists installed sources and their
preferences.

**A source/extension does not appear in the web UI or the app.** Open the
**Sources** tab to check it is listed and not marked **Disabled**, and
**restart the server** after installing, updating, enabling/disabling or
removing it — Tanoshi reads plugins and `.tanoshi-disabled` only at startup.
If it is listed in the app but not the web UI after a restart, the plugin's
`tanoshi-lib` ABI (0.38.0 for Tanoshi 0.40.0) or `arm64-v8a` target does not
match the bundled server; the Logs tab reports the failed load. Remove/Disable
the user copy in Sources to fall back to the bundled extension.

**An extension shows "Disabled" after a disable/uninstall.** That is expected
for bundled extensions: their APK files are read-only, so disabling (or an
uninstall in 0.6/older builds whose web UI still had the button) writes the
server's `.tanoshi-disabled` marker instead of deleting the file. The app
shows it as Disabled; tap **Enable** and restart the server to load it again.
Removing a user-installed copy deletes the file and clears the marker, so the
bundled extension becomes active again.

**"Add repository" rejects my link.** Only `https://` input is accepted: either
a direct catalog URL ending in `.json`, or a GitHub repository link such as
`https://github.com/owner/repo` (`.git`/trailing slash allowed). For a GitHub
link the app probes `main`/`master` for `android/extensions/index.json` and
`tanoshi-extensions.json`; if none exists there is no catalog to add. You can
have at most 10 custom repositories; adding a duplicate is a no-op. The
built-in default already points at
[`tanoshi-extension`](https://github.com/spainclaro-debug/tanoshi-extension);
adding that repo explicitly is unnecessary but harmless.

**"Some repositories could not be loaded" / one repository is missing from the
catalog.** Only that repository failed (network, GitHub rate limit, deleted
repo or a malformed catalog); the others still work. Use **Retry** or the
header refresh, or remove the repository from the Repositories list. A newly
added catalog entry appears only after its repository's `main`/`master` branch
is updated.

**The Sources catalog will not load / "Add extension" is empty.** Every
configured catalog is fetched over HTTPS (built-in `tanoshi-extension` +
custom), so it needs network access (GitHub can be unreachable or
rate-limited). Check the per-repository failure card and use Retry; extensions
that are already installed keep working. The legacy built-in catalog is not used by the app by default anymore.

**Old builds logged `undefined symbol: plugin_declaration`.** That was the
server trying to load support libraries from `nativeLibraryDir` as
extensions; it is fixed in 0.6.0, which filters `libtanoshi.so`,
`libarchive.so`, AndroidX/datastore libraries and files under 256 KB. Update
the app/server.

**Installing an extension fails with a checksum/size error.** The download was
incomplete or the catalog entry is stale. Retry the install; if it keeps
failing, the catalog's `sha256`/`size` and the uploaded `.so` do not match —
that is a catalog/maintainer issue, not an app bug.

**An extension file was downloaded but Tanoshi still ignores it.** Native
plugins must be built for the exact Tanoshi version, `tanoshi-lib` ABI and
`arm64-v8a`, and be a real Android `.so` (not a Tachiyomi/Keiyoushi `.apk`).
Check the Logs tab for a version/load error, then remove the user copy and
retry with a compatible build.

**I picked a web-UI theme, but another device still shows the default.**
Expected: web-UI themes are stored per browser/device in `localStorage`
(`settings:theme-palette`) and are not synced through the server. Pick the
theme on each device; a new device starts at the Auto/default appearance.

**The web-UI theme is not applied / I want to undo it.** In the Tanoshi web UI
open **Settings → Appearance → Themes**, select a preset (or use **New
theme**) and press **Apply**; **Reset to default** clears the stored palette
and returns to the stock appearance. Clearing the browser site data also
resets it. Note this is separate from the Android app themes in the app's
[Settings → Appearance](#themes) — the app theme affects the Android UI, the
web-UI theme affects the Tanoshi frontend pages.

**Cannot reach the server from another device on the LAN.** Enable "Expose on
LAN", restart, and check the status card shows `0.0.0.0`. Make sure both
devices are on the same network (guest Wi-Fi / AP isolation blocks this), and
use `http://<phone-ip>:<port>` — or scan the
[Share on your network QR code](#sharing-on-your-network-qr).

**The Share on your network card shows no QR code.** It needs all three: the
server running, bound to `0.0.0.0` (Expose on LAN enabled *and* the server
restarted afterwards), and a discoverable Wi-Fi/Ethernet IPv4 address. The
card's text says which one is missing — connect to Wi-Fi/Ethernet, start or
restart the server, or enable Expose on LAN. Guest Wi-Fi / client-isolation
networks will not let the other device reach the phone even when a QR code is
shown.

**The shared QR code does not scan or the other device cannot open the URL.**
Use a non-guest Wi-Fi/Ethernet network that allows client-to-client traffic,
raise the screen brightness, and make sure the phone's hotspot/client
isolation is not blocking the connection. The QR encodes a plain
`http://<ipv4>:<port>` URL; a device on a different network (mobile data,
another VLAN) cannot reach it.

**"Check for updates" fails.** The app queries GitHub's public API without a
token. Temporary network problems and GitHub API rate limits both fail this
way — wait and retry. A release is only offered when it is a published
(non-draft, non-prerelease) `android-v*` tag with an `.apk` asset.

**"Install update" says Android does not allow the app to install updates.**
Grant "Install unknown apps" for Tanoshi (the **Allow app installs** button in
the Updates section opens the right system screen), then press **Install
update** again.

**An update downloads but Android refuses to install it ("App not installed").**
Most likely the installed app is signed with a different key — this is
expected for 0.1.0 builds, which must be uninstalled once. It can also mean
the wrong app variant/application id is installed, not enough free storage, or
a corrupted download (delete the update and download it again). Every release
from this repository shares the same signing key.

**No update is offered although a release exists.** Check that the release is
tagged `android-vX.Y.Z` (not a Termux `v*` tag), is not a draft or
pre-release, has an `.apk` asset, and that its version is actually newer than
the installed one (a local debug build can already be at or above the release
version).

**The server is killed in the background.** Exempt the app from battery
optimisation (Settings → "Ignore battery optimisations"), then check the OEM's
"autostart"/"background app" settings. From 0.4.0 the app also loops an
inaudible silent-audio keep-alive and auto-restarts a killed server (up to 5
times in 10 minutes) — see
[Keeping the server alive in the background](#keeping-the-server-alive-in-the-background).
If it still happens, the Android 12+ *phantom-process killer* is the likely
cause: confirm with `adb logcat -s ActivityManager` (`Killing
PhantomProcessRecord`) and use the Developer-options/ADB workaround described
in that section. Keeping the notification visible helps.

**The server restarts itself (or shows ERROR after doing so).** This is the
0.4.0 auto-restart watchdog: an unexpected server-process exit triggers up to
5 automatic restarts in a rolling 10-minute window, roughly 3 s apart. The
Logs tab shows each attempt (`restarting it automatically (attempt N/5...)`).
After five attempts the server stays `ERROR` and stays stopped; check the log
for the exit code, then start it again or follow the phantom-killer guidance
above.

**Notification not shown on Android 13+.** Grant the notification permission
(Settings → Notifications). The server still runs when it is denied, but the
foreground-service notification and its Stop action are hidden.

**WebView is blank or the login page does not load.** Wait until the status
card says `RUNNING` (the WebView cannot load during startup); then use the
browser's back button/reload. If the server was restarted on a different
port, reopen the WebView from the Server screen.

**Login loop / "Unauthorized" after editing `config.yml`.** Changing
`secret` (or the app repairing a bad one) invalidates all sessions. Log in
again.

**"secret must be exactly 16 characters (AES-128 key)" when saving.**
Tanoshi encrypts cover URLs with AES-128-CBC and the key is the secret; use
exactly 16 characters.

**Crash or `dlopen` failure only on Android 15+ devices with 16 KB pages.** The
binaries were not linked 16 KB-page aligned. Rebuild with
`-Wl,-z,max-page-size=16384` as described in
[`native/README.md`](native/README.md) and reinstall.

**Uninstalling deletes my library.** Yes — the data is in app-private storage
and `allowBackup` is off. Copy the `tanoshi/` folder out first (see
[Storage layout](#storage-layout)).

---

## Versions

| Component | Version |
| --- | --- |
| Gradle wrapper | 8.9 |
| Android Gradle Plugin (AGP) | 8.7.3 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.12.01 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8.0) |
| JDK | 17 |
| Tanoshi server | 0.40.0 (`VERSION`) |
| `tanoshi-lib` (plugin ABI) | 0.38.0 (`LIB_VERSION`) |
| libarchive | 3.8.11 (custom minimal build; `compress-tools` 0.16.1 requires ≥ 3.2.0) |
| Embedded web UI | Rebuilt wasm dist (`native/web-dist/`), built from source with the 0.8.0 web-feature patches + infinite-scroll + app-only extension-management + 0.9.x theme patches (`native/patches/0004-web-ui-themes.patch` + `theme_settings.rs`), embedded in `libtanoshi.so`; 0.9.1 rebuilt it with the full-palette application and adaptive text |
| Web UI themes | 12 preset palettes + custom Primary/Secondary/Accent colour builder; per-device `localStorage` (`settings:theme-palette`), from 0.9.0; full-UI palette mapping (banners/icons/badge/background/titles/panels) with contrast-adaptive text from 0.9.1 |
| QR sharing | ZXing core 3.5.3 (pure Java) |
| Foreground service types | `specialUse` + `mediaPlayback` (silent keep-alive; from 0.4.0) |
| Extension catalogs | Built-in **`tanoshi-extension`** repository (raw `main/android/extensions/index.json`, label `tanoshi-extension (default)`, from 0.7.0) + up to 10 custom HTTPS catalog repositories; the local `android/extensions/index.json` is kept for tooling/back-compat |
| Plugin loading | Dual directory (`files/tanoshi/plugins` first, then `nativeLibraryDir`); in-memory memfd + `android_dlopen_ext` loader for user plugins (from 0.5.0); `.tanoshi-disabled` marker + host-library filtering (from 0.6.0) |
| Extension sources/tooling | Canonical `tanoshi-extension` repo (`android/extensions/plugins/`, catalog, `sources/`, `patches/`, `tools/build_extension.sh`); local copies `android/extensions/sources/`, `repo-template/` and `native/build_extension.sh` |
| App version | 0.9.1 (`versionName`); `versionCode` 901 in local builds and for the `android-v0.9.1` tag |
| APK filename | `tanoshi-mobile-server-release-<yyyy-MM-dd>.apk` (0.3.0+; 0.1.0/0.2.0 used `app-debug.apk`) |
| Signing key | `android/keystore/tanoshi-signing.jks` (alias `tanoshi`), shared by all releases |
| Android NDK (native rebuilds only) | r26+ |

The Termux package and the APK ship the same Tanoshi and extension versions;
when bumping one, bump `VERSION` / `LIB_VERSION` and rebuild both.

---

## Project layout

```
android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/dev/spainclaro/tanoshi/   # app, service, admin bootstrap, updater, extensions, Compose UI
│       ├── res/                           # strings, themes, icons, network policy
│       └── jniLibs/arm64-v8a/             # libtanoshi.so, libarchive.so, lib<plugin>.so
├── extensions/                            # legacy catalog, exported sources/template, repo-template, built plugins
├── gradle/wrapper/                        # Gradle 8.9 wrapper
├── keystore/                              # shared sideload signing key + README
├── native/                                # native pipeline, patches 0001-0004 + theme_settings.rs, build_extension.sh, rebuilt web-dist
├── build.gradle.kts
├── gradle.properties
├── README.md                              # this file
├── CHANGELOG.md
├── ASSUMPTIONS_AND_OPEN_QUESTIONS.md
├── gradlew
└── settings.gradle.kts
```

The repository root keeps the Termux packaging (`install.sh`, `lib/`,
`patches/`, `vendor/`, `extensions/`, the release tarball) and the CI
workflows live in `.github/workflows/`.

---

## Changelog

Release history lives in [`CHANGELOG.md`](CHANGELOG.md), and published APKs are
on the [latest release](https://github.com/spainclaro-debug/tanoshi-mobile/releases/latest).

---

## Credits and licences

- Tanoshi © luigi311, MIT — <https://github.com/luigi311/tanoshi>
- Extensions © luigi311 — <https://github.com/luigi311/tanoshi-extensions>
- The five ported extensions derive from
  [Keiyoushi](https://github.com/keiyoushi/extensions) (Apache-2.0) and are
  adapted to Tanoshi's plugin API; their sources are in `extensions/` at the
  repository root.
- Termux/Android patches: `patches/` (Termux package) and
  `native/patches/0001-android-app.patch` (this app), documented in
  `patches/README-patches.txt` and [`native/README.md`](native/README.md).
- See [`ASSUMPTIONS_AND_OPEN_QUESTIONS.md`](ASSUMPTIONS_AND_OPEN_QUESTIONS.md)
  for design decisions that still need the repository owner's confirmation.
