# Yashi — companion Android client for Yakushi (0.2.0-alpha)

> **ALPHA — known issue.** Yashi 0.2.0-alpha's **paged reader is unreliable on
> Android WebView**: pages can be blank/partial, mode switching may not change
> the layout, and page next/prev controls may fail. **Use Webtoon/Continuous
> mode only** for reliable reading. Other features (QR scan, address/history/
> saved edit/delete, LAN discovery, theme, clear data) are expected to work.

Yashi is a small Android client that connects a phone or tablet to a **Yakushi
server** running on another device (or anywhere reachable by IP). It remembers
servers, scans the server's LAN-sharing QR code, discovers Yakushi servers on
the local network, and hosts the Yakushi web UI in a full-screen WebView.

- **Client only:** Yashi does not bundle `libtanoshi.so`, does not run a server
  and makes no server-side changes.
- **Package:** `dev.spainclaro.yashi` (debug builds use
  `dev.spainclaro.yashi.debug`), minSdk 26, target/compile SDK 35.
- **Release asset:** **`yashi-client-alpha-0.2.0-alpha-<yyyy-MM-dd>.apk`**
  (versionName `0.2.0-alpha`, versionCode **200**), signed with the
  repository's shared sideload key (`android/keystore/tanoshi-signing.jks`,
  alias `tanoshi`).
- Released together with **Yakushi Server 0.16.8** on tag `android-v0.16.8`
  (`yakushi-server-release-<date>.apk` +
  `yashi-client-alpha-0.2.0-alpha-<date>.apk`) with combined title
  "Yakushi Server 0.16.8 + Yashi 0.2.0-alpha". Yashi has its own version line
  and can be overridden with `-PyashiVersionName` / `-PyashiVersionCode`.

## Features

- **Start screen:** address bar + **Go**, a **history** of every server you
  connected to (newest first, max 30, de-duplicated by URL) and a **saved
  servers** list (save a history entry, **edit** host/port, **delete**).
  History/saved rows use namespaced LazyColumn keys
  (`history:<url>`/`saved:<url>`), so saving the same address in both lists no
  longer crashes Compose (0.2.0-alpha fix). Addresses accept `host`,
  `host:port`, `http://host:port`, full URLs and the server QR contents; HTTP
  is the default scheme and 8080 the default port.
- **QR scan:** scan the connection QR shown by Yakushi Server's **Share on
  your network** card. Camera permission is requested on first launch (and
  when tapping the QR button if not granted). QR-only scanning via
  `com.journeyapps:zxing-android-embedded`.
- **Local-network discovery:** probes known saved/history addresses first
  (returns them if any respond, the most reliable hint even on VPN/different
  interfaces); otherwise scans subnets from every Wi-Fi/Ethernet IPv4 plus
  saved-address prefixes (up to 4) on ports **8080/18084/8000/3000** plus
  preferred saved/history ports (max 6), probing the unauthenticated
  **`/health`** endpoint first (HTTP 200 = running Yakushi) then `/` HTML.
  Found servers are listed for one-tap connect; the no-results dialog shows
  the scanned local IP, ports and subnets (best-effort; custom ports via
  address/QR).
- **Settings bottom sheet:** **System / Dark / Light** theme and **Clear data**
  (removes saved servers and connection history; the theme is kept).
- **Web UI/browser:** the complete Yakushi frontend in a stable `remember`ed
  WebView, with a normal **status-bar-padded viewport** and
  `cacheMode = LOAD_NO_CACHE` / `clearCache(true)` in this alpha (the status
  bar is not hidden). JavaScript/DOM storage/database, images, zoom controls,
  mixed content, cookies and a `WebChromeClient` are enabled. A small floating
  back button (96dp above the bottom bar) returns to the start screen and is
  hidden on `/catalogue`, `/manga`, `/chapter`; system back walks WebView
  history first.
- **Last-resort `/chapter` reader injection (alpha):** Yashi injects a
  stylesheet that sizes `#page-list` to the real WebView height, fits paged
  images (`max-width/height`, `object-fit: contain`, `pointer-events: none`),
  raises `.reader-settings`/`.topbar` above the tap overlay, and installs a
  capture-phase trusted-click fallback for left/right/middle screen thirds.
  It was verified in Chromium (image visible; next/prev hash changes) but
  **not confirmed on real Android WebView** — hence the alpha paged-reader
  warning above.
- **Device-local persistence:** `SharedPreferences` file **`yashi`** with
  `history`/`saved` (JSON arrays), `theme` and `camera_asked`. Nothing syncs to
  the server.

## Known issues (alpha)

- **Paged reader unreliable:** blank/partial pages, mode switching, and page
  next/prev may fail on real Android WebView. **Use Webtoon/Continuous mode
  only** for reliable reading.
- The `/chapter` injection is a last-resort workaround and may not help on all
  WebViews; treat paged mode as unsupported in this alpha.
- Everything else (QR scan, address/history/saved management, LAN discovery,
  theme, clear data, continuous/webtoon reading) is expected to work.

## Build

```bash
cd android
./gradlew :yashi:assembleDebug
# optional overrides
./gradlew :yashi:assembleDebug -PyashiVersionName=0.2.0-alpha -PyashiVersionCode=200 \
  -PapkDate=2026-11-27
```

The debug APK is written to
`android/yashi/build/outputs/apk/debug/yashi-client-alpha-0.2.0-alpha-<yyyy-MM-dd>.apk`
(debug builds report `0.2.0-alpha-debug`/200). Build both apps with
`./gradlew :app:assembleDebug :yashi:assembleDebug` (what CI does). CI builds
both and publishes both assets with the alpha label and known issues.

## Structure

```
android/yashi/
├── build.gradle.kts          # module config, shared signing key, alpha APK name
├── README.md                 # this file
└── src/main/
    ├── AndroidManifest.xml   # INTERNET/ACCESS_NETWORK_STATE/CAMERA, cleartext HTTP
    ├── java/dev/spainclaro/yashi/
    │   ├── MainActivity.kt   # theme + start/browser switch
    │   ├── MainScreen.kt     # address bar/history/saved, QR, LAN popup, settings
    │   ├── BrowserScreen.kt  # WebView (normal viewport/no cache), back button, /chapter injection
    │   ├── AddressStore.kt   # SharedPreferences persistence + theme
    │   ├── Address.kt        # ServerAddress + address parser
    │   ├── LanScanner.kt     # known-address/multi-subnet discovery (/health probe)
    │   └── Theme.kt          # Compose Material 3 light/dark palettes
    └── res/                  # strings, colors, icon, splash/theme
```

## Permissions and networking

- `INTERNET`, `ACCESS_NETWORK_STATE` and `CAMERA` (optional camera feature).
- Yakushi is served over plain **HTTP**, so the manifest sets
  `usesCleartextTraffic="true"`; only connect on networks you trust.
- Yashi does not request storage, location, install-packages or foreground
  service permissions; it does not run in the background.

## Limitations

- Discovery is best-effort; custom ports/hosts may need the address bar/QR.
- Yashi has **no offline reading, downloads, extension management or server
  lifecycle**; use the Yakushi Server app/web UI for those.
- Yashi ships no app-specific native libraries (only the standard AndroidX
  `libandroidx.graphics.path.so`), so it is not limited to arm64-v8a. The
  server app remains arm64-v8a.

See the [Android README](../README.md) for the server app and known issues, and
the [repository README](../../README.md) for the project overview.
