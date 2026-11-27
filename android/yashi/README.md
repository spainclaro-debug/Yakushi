# Yashi — companion Android client for Yakushi

Yashi is a small Android client that connects a phone or tablet to a **Yakushi
server** running on another device (or anywhere reachable by IP). It is a
lighter alternative to typing the server URL into a browser: it remembers
servers, scans the server's LAN-sharing QR code, discovers Yakushi servers on
the local network, and hosts the full Yakushi web UI in a full-screen WebView.

- **Client only:** Yashi does not bundle `libtanoshi.so`, does not run a server
  and makes no server-side changes. The server is still Yakushi Server (or a
  Termux/desktop Yakushi install).
- **Package:** `dev.spainclaro.yashi` (debug builds use
  `dev.spainclaro.yashi.debug`), minSdk 26, target/compile SDK 35.
- **Release asset:** `yashi-client-release-<yyyy-MM-dd>.apk` (version **0.1.1**,
  versionCode **101**), signed with the repository's shared sideload key
  (`android/keystore/tanoshi-signing.jks`, alias `tanoshi`) so updates install
  over earlier Yashi builds.
- Released together with **Yakushi Server 0.16.1** on tag `android-v0.16.1`
  (`yakushi-server-release-<date>.apk` + `yashi-client-release-<date>.apk`);
  it has its own version line and can be overridden with
  `-PyashiVersionName` / `-PyashiVersionCode`.

## Features

- **Start screen:** address bar + **Go**, a **history** of every server you
  connected to (newest first, max 30, de-duplicated by URL) and a **saved
  servers** list (save a history entry, **edit** host/port, **delete**).
  Addresses accept `host`, `host:port`, `http://host:port`, full URLs and the
  server QR contents; HTTP is the default scheme and 8080 the default port.
- **QR scan:** scan the connection QR shown by Yakushi Server's **Share on your
  network** card. Camera permission is requested on first launch (and when you
  tap the QR button if not granted). QR-only scanning via
  `com.journeyapps:zxing-android-embedded`; an unrelated QR is rejected with a
  message.
- **Local-network discovery:** scans the current local IPv4 `/24` on the
  default Yakushi ports **8080** and **18084**, probing for the Yakushi
  frontend, and lists the servers in a popup for one-tap connect. Best-effort
  for default ports; custom ports via address/QR.
- **Settings bottom sheet:** **System / Dark / Light** theme and **Clear data**
  (removes saved servers and connection history; the theme is kept).
- **Full-screen web UI:** the complete Yakushi web frontend in a stable
  `remember`ed `WebView` configured like the Yakushi Server app (JavaScript,
  DOM storage, database, `loadsImagesAutomatically=true`,
  `blockNetworkImage=false`, overview/wide viewport, built-in zoom controls
  with support zoom, `javaScriptCanOpenWindowsAutomatically`, media playback,
  mixed content, cookies and a `WebChromeClient`). 0.1.1 removes the old
  `setSupportZoom(false)`, which fixes blank chapter pages/missing reader
  images. A small floating bottom-left **back** button (bottom padding
  **96dp** plus navigation-bar insets, so it clears the web UI's bottom bar)
  returns to the Yashi start screen; it is hidden on `/catalogue`, `/manga` and
  `/chapter` paths so it never covers the catalogue or reader. The system back
  button walks WebView history first, then exits to the start screen.
- **Device-local persistence:** `SharedPreferences` file **`yashi`** with
  `history`/`saved` (JSON arrays), `theme` and `camera_asked`. Nothing syncs to
  the server; each device keeps its own list.

## Build

```bash
cd android
./gradlew :yashi:assembleDebug
# optional overrides
./gradlew :yashi:assembleDebug -PyashiVersionName=0.1.0 -PyashiVersionCode=100 \
  -PapkDate=2026-11-27
```

0.1.1 fixes the floating back button covering the web UI's bottom bar and the
blank-reader/missing-image WebView issue (stable instance + Yakushi-matching
settings); Yakushi Server 0.16.1 is a re-release with no server/native changes.

The debug APK is written to
`android/yashi/build/outputs/apk/debug/yashi-client-release-<yyyy-MM-dd>.apk`.
Build both apps with `./gradlew :app:assembleDebug :yashi:assembleDebug`
(what CI does). Android Studio: open the `android/` project; the `yashi`
run configuration builds/installs this module.

## Structure

```
android/yashi/
├── build.gradle.kts          # module config, shared signing key, dated APK name
├── README.md                 # this file
└── src/main/
    ├── AndroidManifest.xml   # INTERNET/ACCESS_NETWORK_STATE/CAMERA, cleartext HTTP
    ├── java/dev/spainclaro/yashi/
    │   ├── MainActivity.kt   # theme + start/browser switch
    │   ├── MainScreen.kt     # address bar/history/saved, QR, LAN popup, settings sheet
    │   ├── BrowserScreen.kt  # full-screen WebView, back button/history
    │   ├── AddressStore.kt   # SharedPreferences persistence + theme
    │   ├── Address.kt        # ServerAddress + address parser
    │   ├── LanScanner.kt    # /24 discovery on ports 8080/18084
    │   └── Theme.kt         # Compose Material 3 light/dark palettes
    └── res/                  # strings, colors, icon, splash/theme
```

## Permissions and networking

- `INTERNET`, `ACCESS_NETWORK_STATE` and `CAMERA` (optional camera feature).
- Yakushi is served over plain **HTTP**, so the manifest sets
  `usesCleartextTraffic="true"`; only connect on networks you trust.
- Yashi does not request storage, location, install-packages or foreground
  service permissions; it does not run in the background.

## Limitations

- Discovery only scans the local `/24` and the default ports; use the address
  bar or QR for custom ports/hosts.
- No offline reading, downloads, extension management or server lifecycle —
  use the Yakushi Server app/web UI for those.
- The web UI is the normal Yakushi frontend; Yashi does not modify it.
- Yashi ships **no app-specific native libraries** (only the standard AndroidX
  `libandroidx.graphics.path.so` support library, which is packaged for all
  ABIs), so the client is not limited to arm64-v8a (the Yakushi Server app
  remains arm64-v8a); it is a normal Kotlin/Compose APK.

See the [Android README](../README.md) for the server app and the
[repository README](../../README.md) for the project overview.
