# Android app — assumptions and open questions

This document lists the assumptions the standalone Android app (the
`android/` project) is built on, and the decisions the repository owner should
confirm. It complements [`README.md`](README.md) (build/usage) and
[`native/README.md`](native/README.md) (native pipeline).

Legend: **A** = assumption we built on, **Q** = question for the owner.
Each assumption notes where it comes from and what breaks if it is wrong.

---

## Assumptions

### A1 — Upstream versions are pinned: Tanoshi 0.40.0 / `tanoshi-lib` 0.38.0

The server is built from `luigi311/tanoshi` **v0.40.0** (the root `VERSION`
file) and the extension plugins are built against `tanoshi-lib`
**0.38.0** (the root `LIB_VERSION` file). Plugins embed that ABI version and
refuse to load on a mismatch.

*If wrong:* the bundled plugins would not load (plugin count 0 in the web UI)
and the app would still run but with no sources. Fix by rebuilding server and
plugins from the same upstream state.

### A2 — Data lives under `TANOSHI_HOME`

Tanoshi reads its data directory from `TANOSHI_HOME`; the app sets it to
`filesDir/tanoshi/`. Everything the server writes — `config.yml`,
`tanoshi.db`, `downloads/`, `cache/`, `manga/`, `plugins/`, `server.log` —
stays below that directory, i.e. in app-private storage.

*If wrong:* data would leak into other paths; the app is sandboxed, so the
practical effect would be a server that cannot find/create its files.

### A3 — `config.yml` schema is the current upstream one

The app generates the same schema the Termux package writes: `base_url`,
`port`, `database_path`, `create_database`, `secret`, `update_interval`,
`max_concurrent_update_sources`, `auto_download_chapters`, `plugin_path`,
`extension.*`, `local_path`, `download_path`, `cache_path`,
`enable_playground`, and the notification blocks (`telegram`, `pushover`,
`gotify`, `myanimelist`, `anilist`). Paths are absolute and literal — Tanoshi
does not expand `~` or environment variables.

*If wrong:* upstream added/renamed keys; the server fills in defaults for
missing ones (it already does for the Termux package), but the app's
`config.yml` editor only validates `port` and `secret`.

### A4 — `secret` must be exactly 16 characters

Tanoshi uses it as an AES-128-CBC key, so the app generates a 16-character
alphanumeric secret and repairs (rotates) any other length on start. Rotating
it invalidates all existing sessions.

*If wrong:* cover-image URLs fail to decrypt; the server must generate a new
secret and users log in again.

### A5 — Upstream binds `0.0.0.0`; the Android build adds `TANOSHI_BIND`

Upstream hardcodes the listen address. The Android-app patch
(`native/patches/0001-android-app.patch`) makes the server read
`TANOSHI_BIND` instead. The app defaults to **`127.0.0.1`** and sets
**`0.0.0.0`** only while "Expose on LAN" is enabled.

*If wrong:* the app could be reachable on the LAN even with the toggle off
(security regression), or LAN mode would not work.

### A6 — Extensions: nine bundled + user-installable from the Sources tab

Nine plugins are compiled into the APK as `lib<name>.so` in
`nativeLibraryDir`; the **Sources** tab can additionally install, update,
enable/disable and remove user copies in `filesDir/tanoshi/plugins/<name>.so`
from the built-in and custom extension repositories (A27/A30). The server
loads the writable directory first and then `nativeLibraryDir`, so a user copy
overrides a bundled extension with the same name. Bundled copies can never be
deleted, but they can be **disabled** persistently through the
`.tanoshi-disabled` marker (A31); removing a user copy clears the marker so a
bundled copy becomes active again. The writable `plugin_path` directory also
holds extension preferences and the marker. Any change requires a server
restart to take effect.

*If wrong:* plugins either fail to load or fail to persist preferences. The
old "plugins are frozen in the APK" assumption was true up to 0.4.0 only; see
A27/A28 for the 0.5.0 install/load model.

### A7 — The first administrator is created through GraphQL `register`

No default credentials are baked in. On first run the app shows a dialog and
calls `mutation { register(login: {username, password}, isAdmin: true) }`
against `http://127.0.0.1:<port>/graphql`, after checking
`{ serverStatus { activated } }`. Skipping the dialog leaves registration to
the web UI.

*If wrong:* first-run onboarding breaks; no data is lost, the user can
register in the browser.

### A8 — arm64-v8a only

The only ABI shipped is `arm64-v8a` (`minSdk` 26), pinned in
`app/build.gradle.kts` with `ndk.abiFilters`. There is no `armeabi-v7a`,
`x86` or `x86_64` build, and the APK is not an ABI split or bundle.

*If wrong:* 32-bit-only and emulator devices cannot install the app. See Q2.

### A9 — No TLS; HTTP only

The server speaks plain HTTP and the app is configured to allow cleartext
only to loopback (`127.0.0.1`, `localhost`, `10.0.2.2` emulator). LAN
exposure therefore exposes the login page and session over plain HTTP.

*If wrong:* the app would need a TLS terminator (reverse proxy) or an
in-app HTTPS stack, which is out of scope for now. This is intentional and
documented in the security notes.

### A10 — Foreground service types `specialUse|mediaPlayback`

The service declares `foregroundServiceType="specialUse|mediaPlayback"`:

- `specialUse` (with `FOREGROUND_SERVICE_SPECIAL_USE` and the
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property) rather than `dataSync`, because
  Android 15+ caps `dataSync` at 6 h/day;
- `mediaPlayback` (with `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) together with the
  hidden silent keep-alive (A24), so the app is in a media-playback FGS state
  while the server runs.

At runtime the passed type is `specialUse|mediaPlayback` on Android 14+,
`mediaPlayback` on Android 10–13 (the `specialUse` type only exists on 14+),
and 0 on older releases that predate FGS types. A partial wake lock is held
while the server runs.

*If wrong (Play Store policy):* Google may reject the `specialUse`/media
justification for a sideloaded/self-hosted app; see Q4. The audio is inaudible
and has no media session/notification, but a future Play review may still ask
about a hidden audio path; the docs state exactly what it does.

### A11 — The APK is sideloaded, not distributed via Play Store

The documented and CI-built artifact is the **debug** APK
(`applicationId dev.spainclaro.tanoshi.debug`), signed with the committed
shared sideload key `android/keystore/tanoshi-signing.jks` (A18). Distribution
is GitHub releases tagged `android-v*` plus sideloading only — there is no Play
Store channel. The project repository is now
`spainclaro-debug/Yakushi` (the old `tanoshi-mobile`/`tanoshi-termux` URLs
redirect; the in-app updater already targets `Yakushi`). The Termux package tarball
name (`tanoshi-termux-aarch64.tar.gz`) is an artifact name and is unchanged.

*If wrong:* Play distribution would need a Play-compliant signing setup (Play
App Signing), a privacy policy, target-SDK policy compliance and an FGS
declaration review.

### A12 — Native libraries must be extracted from the APK

`android:extractNativeLibs="true"` and
`packaging.jniLibs.useLegacyPackaging = true` are required so the installer
extracts `libtanoshi.so`/`libarchive.so`/plugins into the executable
`nativeLibraryDir`. `LD_LIBRARY_PATH` includes `nativeLibraryDir` so the
server finds `libarchive.so`.

*If wrong:* the server cannot be executed (`Permission denied`) or fails to
resolve `libarchive.so`; both are documented troubleshooting cases.

### A13 — 16 KB page alignment is required for Android 15+ devices

The native libraries are linked with `-Wl,-z,max-page-size=16384`. Android 15
can run on devices with 16 KB memory pages; 4 KB-aligned binaries fail to
load there.

*If wrong:* the app crashes with `dlopen`/linker errors on such devices only.
This must be re-checked on every native rebuild.

### A14 — The web UI is embedded and shown in a WebView

Tanoshi's wasm web UI is embedded into the server binary; the app's "Open in
app" screen is a `WebView` pointed at the loopback URL (JavaScript + DOM
storage enabled), while "Open in browser" uses an `ACTION_VIEW` intent. As of
0.2.0 the embedded UI is the vendored `android/native/web-dist/` package-3
dist, built with `patches/tanoshi-infinite-scroll.patch`: the catalogue
auto-loads the next page via the `catalogue-scroll-sentinel` observer, with
**Load More** as the manual fallback.

*If wrong:* the WebView could need additional configuration for a future UI
feature; the browser path is unaffected. Note that changing web-UI behaviour
requires rebuilding `libtanoshi.so` (and shipping a new APK), not just
rebuilding the Android app, and that the web UI keeps its own theme.

### A15 — Data is app-private and not backed up

`allowBackup="false"` and app-private storage mean uninstalling the app
deletes the library/accounts. Moving data requires copying the
`files/tanoshi/` tree out (debug builds can use `adb run-as`).

*If wrong:* users lose their library on uninstall. This matches the Termux
package's "back up `data/`" warning; a future export/import feature is a
possible enhancement (see Q7).

Upgrading from **0.1.0 to 0.2.0 requires exactly this one-time uninstall**
because the signing key changed (A18); copy `files/tanoshi/` out first if the
library matters.

### A16 — The Termux package remains authoritative for the binary

The checked-in `jniLibs/arm64-v8a/` binaries are built from the same upstream
source and patch set as the Termux package (`patches/`,
`vendor/compress-tools-0.16.1/`, `lib/build.sh`), via
`android/native/build_tanoshi.sh`, `build_libarchive.sh` and
`sync_prebuilt.sh`. `libtanoshi.so` is the patched Tanoshi 0.40.0 binary;
`libarchive.so` is a minimal libarchive **3.8.11** build with zlib linked in
statically (only libc/libdl `NEEDED`) and SONAME patched to `libarchive.so`;
plugin files are named `lib<plugin>.so`. From 0.5.0 `libtanoshi.so` was
rebuilt with the dual-directory/in-memory extension loader
(`native/patches/0002-android-extension-loader.patch` +
`android_loader.rs`), and the bundled plugin set is unchanged (the catalog's
nine entries mirror those files).

*If wrong:* the two deliverables would silently diverge in server/plugin
version; the docs tell maintainers to bump both together.

### A17 — CI only needs the Gradle build

The native binaries are committed, so the GitHub Actions workflow installs
JDK 17 + Android SDK and runs `./gradlew :app:assembleDebug`; it does not
install the NDK/Rust. CI builds on pushes and pull requests, and creates a
GitHub release (with the debug APK) only for **`android-v*`** tags; plain `v*`
Termux package tags do not trigger the workflow at all (see Q6). For a tag,
CI derives `versionName` from `android-vX.Y.Z`, sets
`versionCode = X * 10000 + Y * 100 + Z`, and names the APK
`yakushi-server-release-<UTC date>.apk` via `-PapkDate`, passing all
three to Gradle; local builds use the Gradle defaults (0.13.5 / 1305, local date).

*If wrong:* if the repository owner prefers binaries rebuilt in CI, the
workflow becomes much heavier (NDK, Rust, libarchive, wasm tooling) — see Q9.

### A18 — All releases share one committed sideload signing key

`android/keystore/tanoshi-signing.jks` (JKS, alias `tanoshi`, store/key
password `tanoshi-android`) is committed on purpose. `app/build.gradle.kts`
signs both the `debug` and `release` build types with it when the file is
present, and falls back to the local Android debug keystore when it is not.
This is what makes the in-app updater possible: Android only replaces an
installed app when the new APK has the same signing key, and CI and local
builds both need one stable signature. The key is not a secret — it protects
nothing on a sideload-only app — it only has to stay stable.

0.1.0 was built with an ephemeral CI debug key, so **0.1.0 users must
uninstall once** before installing 0.2.0 or later (losing app data). Replacing
the key
for a private build has the same consequence. Details and `keytool` commands:
[`keystore/README.md`](keystore/README.md).

*If wrong:* in-app updates would fail with a signature mismatch, or users
would have to uninstall (and lose their library) on every key change.

### A19 — In-app updates are manual, full-APK downloads from GitHub releases

`Settings → Updates` (`UpdateManager`) queries
`https://api.github.com/repos/spainclaro-debug/Yakushi/releases?per_page=50`
without authentication and considers only non-draft, non-prerelease tags
starting with `android-v` that have an `.apk` asset. It prefers asset names
starting with `tanoshi-mobile-server` (the 0.3.0+ naming), accepts any other
`.apk` as a fallback, and still understands the legacy `app-debug.apk` name
used by 0.1.0/0.2.0. It compares the tag version numerically with the installed
`versionName` (the debug `-debug` suffix is stripped), downloads the APK into
`cacheDir/updates/` (temporary `.part` file, size checked, SHA-256 checked
when the release asset exposes a digest), and installs it via
`PackageInstaller` after the system confirmation dialog. This requires
`REQUEST_INSTALL_PACKAGES` and the user-granted "Install unknown apps"
permission; the app also needs network access and is subject to GitHub's
unauthenticated rate limit.

Deliberate limits: **no automatic/background update check**, **no silent
install**, **no delta/incremental updates** (every update downloads the full
APK), no Play Store channel, and only releases signed with the shared key
(A18) can replace the app. The install stops the running app/server; the user
reopens Tanoshi afterwards.

*If wrong:* users would expect background/automatic updates; the fallback is
a manual APK download from the release page.

### A20 — The app version is independent of the Tanoshi server version

The app follows its own `android-vX.Y.Z` version scheme (0.13.5 / versionCode
1305 locally; the same value as `X*10000+Y*100+Z` for the `android-v0.13.5`
tag), independent of the bundled Tanoshi server (0.40.0 /
`VERSION`) and of the Termux package (`PACKAGE_TAG`). An app update may or may
not change the bundled server; the two version lines are documented
separately.

*If wrong:* users could mistake app releases for server/extension releases;
the docs state both versions explicitly in the Versions table and changelog.

### A21 — LAN sharing uses a discovered IPv4 address over plain HTTP

The Server screen's **Share on your network** card shows a QR code encoding
`http://<host-ipv4>:<port>` only when the server is `RUNNING`, bound to
`0.0.0.0`, and a usable LAN address was found. The address is discovered by
taking the first IPv4 address of the active network when `ConnectivityManager`
reports it as Wi-Fi or Ethernet, and otherwise falling back to enumerating
non-loopback interfaces; loopback and link-local (`169.254.x.x`) IPv4
addresses are excluded, and the address is re-resolved when the screen
resumes. The QR is generated with ZXing core 3.5.3 (pure Java, no native
code) off the main thread. The card also
shows `ip:port` with a copy button, warns that it only works on the same
network and that the connection is plain HTTP, and merely describes what to
enable when requirements are missing — it never changes the bind address.

*If wrong:* the QR may encode an unreachable address (VPN, VLAN, hotspot
isolation) or the wrong interface; the docs state the same-network/plain-HTTP
limits and the app falls back to the existing LAN guidance text.

### A22 — Release APKs use a dated filename

Since 0.3.0, app builds produce
`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk` via the Gradle
`androidComponents.onVariants` output rename. CI sets `-PapkDate` from the UTC
date; local builds default to the local date. The workflow's artifact name
stays `tanoshi-android-debug-apk`, but the file inside it and the GitHub
release asset use the dated name. The in-app updater prefers asset names
starting with `tanoshi-mobile-server` and still accepts legacy
`app-debug.apk` assets, so 0.1.0/0.2.0 releases and manual downloads remain
valid. Historical releases keep their original `app-debug.apk` name.

*If wrong:* users/automation looking for `app-debug.apk` in new releases will
not find it; the docs and updater cover both names.

### A23 — Settings can be saved before the first server start

Since 0.3.0, `ConfigManager.setPort` calls `AppPaths.ensureDirectories` and
`writeAtomically` creates the target's parent directory defensively. This
fixes the 0.2.0-and-earlier `config.yml.tmp: open failed: ENOENT` failure when
the port or `config.yml` was saved before the server had ever run. Existing
`config.yml`/data are untouched by the fix.

*If wrong:* the first settings save before starting the server would fail
again; the docs troubleshooting section describes the old symptom.

### A24 — Hidden silent-audio keep-alive while the server runs

While the server runs, `TanhoshiService` loops a generated one-second silent
WAV through `MediaPlayer` and declares/starts the service with the
`mediaPlayback` FGS type (plus `specialUse`), to keep the app in a
media-playback foreground state so Android/OEMs are less likely to freeze or
kill it and its native server child processes.

Details: the WAV (`cacheDir/keepalive/silence.wav`, 44.1 kHz/16-bit mono PCM,
~88 KB) is generated at runtime and reused — nothing is bundled in the APK;
playback uses media audio attributes, looping, **volume forced to 0** and a
partial wake mode; there is **no MediaSession, no media-style/extra
notification, no audio-focus request and no extra UI** (the only notification
is the normal server FGS one). It starts and stops with the server (including
failure/terminal paths and service destroy) and there is **no user toggle** —
stopping the server is the only way to stop it. It has a small continuous
battery cost while the server runs. Failures are logged (app Logs tab SYSTEM
line and Logcat) and ignored: the server keeps running without it.

This is a **best-effort mitigation, not a guarantee**; it cannot defeat a
force-stop, OEM "deep sleep" or the phantom-process killer (A26).

*If wrong:* the app would be playing hidden audio without benefit, or a store
policy would object; the docs explain exactly what it is, that it is
inaudible, and that the server does not depend on it. If the no-toggle
decision changes, a setting would need to be added (see Q15).

### A25 — Auto-restart watchdog with a bounded rolling budget

If the server process exits unexpectedly while `AppSettings.serverShouldRun`
is true (not a user-requested stop/restart), the service keeps the foreground
notification, wake lock and silent keep-alive alive and restarts the server a
bounded number of times: **at most 5 automatic restarts in a rolling
10-minute window, ~3 s apart**. The dashboard shows `STARTING` and the log
records each attempt (`restarting it automatically (attempt N/5, in 3 s)`).
A user-requested Stop/Restart cancels a pending restart. After the budget is
exhausted the app records the attempts, sets `ERROR`, clears
`serverShouldRun` (so a sticky service restart cannot resurrect the server)
and releases the wake lock/audio before stopping the foreground service.

*If wrong:* a crash loop could spin (bounded by the budget); the docs describe
the 5-in-10-minutes cap and the terminal `ERROR` state.

### A26 — The Android phantom-process killer is out of the app's control

Android 12+ (API 31) limits app *child* processes device-wide: when more
than **32 phantom processes** exist on the device, or a child process uses
excessive CPU, `ActivityManager` kills those child processes. The Tanoshi
server (and its extension workers) is such a child, so the server can be
killed while the app survives. The silent keep-alive (A24) improves the app's
importance/OEM treatment but **does not exempt it from these limits**; the
watchdog (A25) shortens downtime but does not remove the cause.

The reliable fixes are device-side and outside the app: Developer options
**"Disable child process restrictions"** where the OEM exposes it, or ADB
`settings put global settings_enable_monitor_phantom_procs false` (Android
12) / `device_config put activity_manager max_phantom_processes 2147483647`
(Android 12L+), with verification commands and reboot caveats. These are
**device-wide** changes with battery/background-activity trade-offs and are
not required on most phones; the app cannot make them itself.

*If wrong:* users may expect the keep-alive to guarantee survival. The docs
state it is best-effort, show how to confirm the killer from
`adb logcat -s ActivityManager` (`Killing PhantomProcessRecord`, `excessive
cpu`) and document the workaround with its trade-offs.

### A27 — Extensions are installed from merged repository catalogs

The bottom nav has a **Sources** tab (order: Server, Sources, Logs, Settings).
It manages a built-in repository plus up to 10 custom repositories (A30); the
built-in default is now the dedicated
[`tanoshi-extension`](https://github.com/spainclaro-debug/tanoshi-extension)
repository (raw `main/android/extensions/index.json`, label
`tanoshi-extension (default)`), while the local
`android/extensions/index.json` in this repo is kept for tooling/back-compat.
It
lists the bundled plugins plus user copies with badges (Bundled / Installed by
you / Overrides bundled / Ported to Rust / Update available / Disabled / the
source repository label) and provides **Add extension**. The app fetches every
configured catalog (plain `HttpURLConnection`, 15 s timeouts, no auth, no
third-party library), merges them (dedupe by normalized extension name,
built-in/earlier repositories win) and reports per-repository failures
separately. Installing an entry downloads its **HTTPS** `.so` into
`filesDir/tanoshi/plugins/<name>.so.part`, verifies size and (when present)
SHA-256, atomically renames to `<name>.so` and clears any disabled marker for
the name. The catalog contract used by the app: `extensions[]` entries with
`name` (`^[a-z0-9][a-z0-9_-]{0,63}$`), `url` (`https://`), `size` (> 0;
install requires > 256 KB), optional `sha256` (64 hex) and `ported`;
malformed entries are skipped. The app rejects plugins below 256 KB and never
installs arbitrary URLs from the UI — only catalog entries. Remove deletes
only user copies (and clears the disabled marker); enable/disable update the
disabled marker instead of deleting bundled files (A31). Any
install/update/remove/enable/disable marks **restart required**; the UI offers
Restart/Start server.

*If wrong:* the app could install incompatible or untrusted plugin files. The
docs state the trust boundary (the `tanoshi-extension` built-in repo and
third-party custom catalogs), that hashes are integrity not signatures, and
that only reviewed catalog entries should be published.

### A28 — User extensions are loaded via memfd + `android_dlopen_ext`

The bundled server (0.5.0+) was rebuilt with the extension loader patch
(`android/native/patches/0002-android-extension-loader.patch` +
`android_loader.rs`):

- `PluginManager::load_all` loads plugins from `self.dir`
  (`filesDir/tanoshi/plugins`, writable) **first** and then the directory in
  `TANOSHI_PLUGIN_LOAD_DIR` (`nativeLibraryDir`); entries are keyed by
  normalized name so a user copy wins over a bundled one. `.tanoshi-disabled`
  entries are skipped at startup (`skipping disabled extension ...`), and
  loading a disabled plugin by name fails with `extension <name> is
  disabled`.
- Host libraries are never treated as extensions: `libtanoshi.so`,
  `libarchive.so`, `libandroidx.graphics.path.so`,
  `libdatastore_shared_counter.so`, other `libandroidx*`/`libdatastore*` files,
  managed/staged names and anything under 256 KB are filtered out. This fixes
  the older `undefined symbol: plugin_declaration` startup errors.
- Uninstall handling: a user-installed copy in the writable directory is
  deleted and its disabled-marker entry cleared; a bundled (read-only) plugin
  is not deleted — the server instead writes its key to
  `.tanoshi-disabled` so it stays disabled until re-enabled.
- For Android plugins the worker calls `android_loader::open_plugin_library`:
  it reads the `.so` bytes, creates an anonymous `memfd_create` descriptor,
  maps and copies the bytes, marks the mapping `PROT_READ|PROT_EXEC` (warning,
  not fatal, if `mprotect` fails), and loads with
  `android_dlopen_ext(ANDROID_DLEXT_USE_LIBRARY_FD)` (`RTLD_NOW|RTLD_LOCAL`).
  If that fails it falls back to a normal `dlopen()` and reports both errors;
  bundled plugins remain loadable via the normal path.
- Rationale: Android 10+ W^X/SELinux forbids `dlopen()` of code from
  app-writable storage. This is the same class of technique used by other
  on-device runtimes; the app still targets API 35 (no `targetSdk` downgrade).

*If wrong:* user-installed plugins would not load (W^X) and only the bundled
nine would work; the fallback path and Logs messages make the failure visible.

### A29 — Extension compatibility, trust and availability limits

Extensions remain native code with strict compatibility: they must be built
for the bundled Tanoshi 0.40.0 / `tanoshi-lib` 0.38.0 ABI, the Rust toolchain
family the package pins, and `arm64-v8a`. The app cannot compile extensions
on-device, cannot install Tachiyomi/Keiyoushi `.apk` extensions, and only
installs `.so` plugins listed by the built-in or added catalogs. Catalogs and
downloads need network access to GitHub/raw.githubusercontent.com (or the
custom catalog host); catalogs are not signed and SHA-256/size only verify
integrity against the catalog, so **custom repositories are third-party
content** the app cannot vouch for beyond structural validation. Extensions
run with the Tanoshi server's privileges inside the app sandbox, so users
should only install entries and add repositories they trust. Incompatible
plugins fail the server-side version check and are ignored (visible in the
Logs tab), and the in-memory loader is best-effort.

*If wrong:* users might expect arbitrary plugin compatibility or sandbox
isolation; the docs and UI clearly state the version/ABI, trust and restart
requirements.

### A30 — Custom repositories: resolution, persistence and dedupe

**Sources → Add repository** accepts only `https://` input:

- a direct catalog URL ending in `.json` — fetched and parsed immediately; it
  must be a valid catalog with at least one extension, and is then stored as
  the catalog URL; or
- a GitHub repository link `https://github.com/<owner>/<repo>` (`.git` and a
  trailing slash are tolerated) — the app probes, in order,
  `raw.githubusercontent.com/<owner>/<repo>/{main,master}/android/extensions/index.json`
  then `{main,master}/tanoshi-extensions.json`, validates the first non-empty
  catalog and **stores the resolved raw URL** (label `owner/repo`).

Constraints: at most **10** custom repositories; duplicates (same catalog URL,
or the same GitHub `owner/repo` label) are a no-op returning the existing
repository. Repositories are persisted in `AppSettings.extensionRepositoriesJson`
(a JSON array of catalog URLs) in insertion order; the built-in
`tanoshi-extension` repository (label `tanoshi-extension (default)`) is
synthesised first, cannot be removed and is not stored in the list. Adding
`https://github.com/spainclaro-debug/tanoshi-extension` explicitly resolves to
the same raw catalog URL and is deduplicated against the built-in entry. The displayed
repository list is built-in first, then custom repositories in insertion
order. Catalogs are fetched/merged in that order and deduplicated by
normalized extension name with earlier entries winning; a failing repository
yields a `RepositoryFailure` warning while the others still load. Removing a
custom repository only removes it from the app list — extensions already
installed from it stay on disk.

*If wrong:* duplicate/conflicting extension names across repositories could
resolve unexpectedly; the documented precedence (built-in, then insertion
order) and per-repository labels/failures make it predictable.

### A31 — Disabled marker (`.tanoshi-disabled`) semantics

Extensions can be disabled without deleting them. The state lives in
`filesDir/tanoshi/plugins/.tanoshi-disabled` (writable plugin directory), one
normalized extension name per line: `#` comments and blank lines are ignored,
an optional `lib` prefix and `.so` suffix are stripped, names are lowercased.
The server reads it at plugin load time (missing file = nothing disabled) and
skips the listed keys. The app wrote it from 0.6.0 for **Enable/Disable** on
bundled and user extensions and still rewrites it atomically (sorted names,
deletes the file when the set becomes empty). From 0.7.0 the embedded web UI
no longer exposes uninstall, but the server keeps the uninstall handling for
older clients: it deletes a user copy and clears the marker, or writes the
marker for a bundled extension. **Remove** in the app (user copy only) deletes
`<name>.so` and clears the marker entry so a bundled copy becomes active
again; **Reinstall/Update** clears the marker too. The Sources tab shows a
**Disabled** badge and Enable/Disable actions; every change needs a server
restart.

*If wrong:* extensions could be hidden without an obvious way back, or stale
marker entries could keep a source disabled; the docs/UI expose the Disabled
badge and the Enable action, and removing a user copy clears the marker.

### A32 — Extension management is backend-only (app), not in the web UI

From 0.7.0 the embedded Tanoshi web UI is deliberately **backend-only** for
extensions. Settings → Sources renders only the **Installed** source list
and per-source preferences; it no longer renders the **Available** sources
list, the source-list **Install/Update** buttons, or the per-source
**Uninstall** button. Those controls never worked on Android, where plugins
are native `.so` files installed by the app and loaded by the server only at
startup; the web-UI backend install path would fail or target a different
distribution format.

Implementation: the wasm frontend was rebuilt from source with
`patches/tanoshi-infinite-scroll.patch` and
`native/patches/0003-web-ui-hide-source-management.patch`; the resulting dist
is vendored in `android/native/web-dist/` and embedded in `libtanoshi.so`.
The infinite-scroll catalogue is unaffected. Extension management remains in
the app's **Sources** tab only (A27).

*If wrong:* users could be confused by a web UI that looks less capable than
the app, or someone could re-enable controls that do not work; the docs/UI
state that extension management is app-only and point to the Sources tab.

### A33 — Web-UI themes are per-device (`localStorage`), not server settings

From 0.9.0 the embedded Tanoshi frontend has a **Settings → Appearance →
Themes** section: 12 preset palettes plus a **New theme** builder with
Primary/Secondary/Accent colour swatches (20 colours each), **Apply** and
**Reset to default**. The chosen palette is serialized as a small JSON object
(`kind`, `name`, `primary`, `secondary`, `accent`) into the browser's
`localStorage` under the key **`settings:theme-palette`** and re-applied at
boot by `apply_stored_theme()` before the UI renders (invalid/non-hex values
are ignored). It is **not** part of `config.yml`, the database, or any
server-side settings; there is no sync between devices. Each device/browser
keeps its own theme, a new device/browser defaults to the stock Auto
appearance, and Reset or clearing site data removes the stored palette. This
is independent of the Android app's Material 3 themes
(Dark/Light/Nord/Catppuccin), which are stored in `AppSettings.themeId` for the
app UI only.

**How the palette is applied (0.9.1 rework).** From 0.9.1 the palette is mapped
across the whole frontend in both light and dark mode via CSS variables plus a
`body.themed` class: top and bottom banners use **primary** (their text/active
colours adapt to its luminance, with readable accent variants for the top-bar
title and active bottom-bar item); all SVG icons use **secondary**; the
library-cover unread-chapter badge uses **secondary** with contrast-adaptive
text; the page background is **accent** mixed at **30%** over the mode base
(`color-mix`: 30% accent over `#f4f4f4` light / `#0a0a0a` dark, with the
derived background shades and modal background tinted); headers/titles use
**accent** (readable variants per mode, against the primary banner for the
top-bar title and against the dark thumbnail gradient for cover titles);
settings panels (`.reader-settings` and the settings page content) use
**accent** as their panel background with adaptive panel text. `theme_settings.rs`
computes the per-mode text colours (`--theme-text-light` / `--theme-text-dark`),
readable heading colours, and the banner/panel/badge foregrounds; **Reset to
default** removes the `body.themed` class and all theme variables and restores
the normal Auto/Light/Dark appearance. (0.9.0 applied the palette mainly to the
top bar/borders; the storage and per-device semantics did not change.)

**Settings list cells (0.9.2).** The Settings-page list cells (Appearance,
Chapters, Library, Categories, Reader, Sources, Users, Download Queue,
Migrate, …) previously kept the stock `--list-group-background-color`, which
resolves at `:root`, so they stayed white regardless of the themed background
(and could show light text on white in some theme/mode combinations). From
0.9.2 `theme_settings.rs` computes **`--theme-cell-light`** (accent mixed
**22%** into white), **`--theme-cell-dark`** (accent mixed **30%** into
`#111111`) and contrast-adaptive **`--theme-cell-text-light`** /
**`--theme-cell-text-dark`**; the CSS themes
`body.themed .settings-page .list.group` in both modes and makes the cell
contents inherit the adaptive cell text. No other theme behaviour changed from
0.9.1.

*If wrong:* users could expect the theme to follow their account across
devices; the docs/UI state that themes apply to this browser/device only and
new devices default to Auto.

### A34 — The reader image-save feature was removed in 0.13.5

0.13.0–0.13.4 had a reader "Save image" action (long-press/right-click popup,
`window.TanoshiApp.saveImage`, DownloadManager/MediaStore saving, save CSS/JS
and a WebView `setDownloadListener`). **It was removed in 0.13.5** together
with patch `0006-web-ui-reader-image-save.patch`: the reader suppresses the
context menu as before, `WebAppBridge` has no image-save method, and
`BrowserScreen` has no download listener. Individual page images are available
directly in the downloaded chapter folders (A35). This is intentional — the
chapter download now produces the page images, so a separate per-image save
path was redundant.

*If wrong:* users looking for a save-one-page action can download the chapter
and copy the image from the public folder; the docs say so explicitly.

### A35 — Chapter downloads: public page-image folders, All files access, guard

From 0.13.5 offline chapters are **page images in public storage**:

- Layout: `<external storage>/yakushi/.downloaded/<Manga Title>/<Chapter Title>/`
  (`Environment.getExternalStorageDirectory()`), e.g.
  `/storage/emulated/0/yakushi/.downloaded/<manga>/<chapter>/0000_00.webp`.
  There is **no source-name level**; `<Manga Title>/details.json` is written
  next to the chapter folders. The worker downloads a temporary CBZ inside the
  manga folder, extracts every ZIP entry into the chapter folder (a
  `spawn_blocking` ZipArchive extractor; existing folder replaced), deletes the
  archive on success, and stores the **folder path** as the chapter's
  downloaded path (if extraction fails it keeps the archive and stores the
  archive path). Removal/re-download handles folders and legacy archives
  (`remove_dir_all`/`remove_file`). The reader reads the folder:
  `ChapterService` uses `local::get_pages_from_dir` when the path is a
  directory, which keeps image files only and sorts them naturally; page images
  are served from the file path.
- Path/permission: the app seeds/migrates `download_path` to
  `<Environment.getExternalStorageDirectory()>/yakushi/.downloaded`, creates
  the directory before start (`ConfigManager.ensurePublicDownloadPath`, quoted
  or unquoted keys), and declares **`MANAGE_EXTERNAL_STORAGE`** +
  legacy `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=29`) with
  `requestLegacyExternalStorage="true"`. `MainActivity.requestStorageAccess()`
  opens the system **All files access** screen on Android 11+
  (`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`) or requests the legacy
  permission on Android 7–10. Without the grant the worker cannot write the
  public folder.
- Downloads work for **all plugin extensions**: the worker's guard now rejects
  only `10000..1_000_000` (the Local source's id range) instead of
  `source_id >= 10000`, so hashed plugin ids (e.g. QiScans
  `5806040666300479660`) download and extract (verified end-to-end, page
  images served). The built-in **Local** source remains non-downloadable
  because its pages are already local files (the guard still rejects it).
- Queue visibility: `download_queue`, `download_queue_updates`,
  `get_downloaded_chapters` and the four download mutations use `AuthGuard`
  (any signed-in user); the admin-only download-queue repro endpoints remain
  `AdminGuard`. The web UI has a per-chapter download icon
  (`download_chapter` stops row navigation), progress `downloaded/total`, a
  4 s refresh while any `download_status` is pending (up to 30 ticks) and a
  check icon + `Downloaded` title/label.
- The server's own default `download_path` (`<TANOSHI_HOME>/.tempchapter`)
  remains for non-app runs, but the app always writes the public path and
  migrates old `downloads`/`.tempchapter` configs.

*If wrong:* downloads could land in a non-public folder or fail without the
All-files permission; the app migrates the config, creates the directory, asks
for the permission on launch and the docs explain where the files are and how
to grant it.

## Open questions for the repository owner

### Q1 — What should the default bind address be?

We default to **loopback only** (`127.0.0.1`), matching the Termux package's
security note ("only expose when needed"), with a LAN toggle. The Termux
package binds `0.0.0.0` by default, so the two deliverables behave
differently. **Decided (owner, 2026-11-14): loopback default + explicit LAN
toggle stays.**

### Q2 — Should 32-bit or emulator ABIs be bundled?

Only `arm64-v8a` is built. `armeabi-v7a` (old 32-bit phones) and
`x86_64` (emulators) would require rebuilding the server, libarchive and all
plugins for each ABI and roughly doubling/tripling APK size and CI time.
Confirm arm64-only, or specify which extra ABIs are required.

### Q3 — Should the app auto-create a default administrator?

Currently the first admin is created interactively (no baked credentials),
which is safer than Termux's `admin`/`tanoshi123` default. If install-time
provisioning without a dialog is desired (e.g. for a fixed kiosk setup),
confirm the expected behaviour and credentials policy — a baked default
password would have to be called out in the docs and changed on first login.
**Decided (owner, 2026-11-14): no baked-in admin; keep the first-run
dialog.**

### Q4 — Sideload only, or Play Store?

Sideload-only is assumed (debug APK, `specialUse` FGS, self-hosted scraper
app). A Play Store release would need: a Play-compliant signing setup, privacy
policy, data-safety declaration, target-SDK/FGS policy compliance (including
the `specialUse` declaration review), and possibly removal of
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

**Decided (owner, 2026-11-14):** sideload-only for now; GitHub releases tagged
`android-v*` (consumed by the in-app updater, A19) are the update channel.
A Play Store release remains out of scope.

### Q5 — Who owns the release signing key, and where does it live?

**Resolved (owner, 2026-11-14):** the key lives in the repository at
`android/keystore/tanoshi-signing.jks` (alias `tanoshi`, password
`tanoshi-android`) and is used by CI and local builds alike, so in-app updates
work. It is a stable sideload key, not a secret; there is no separate
production key while the app is sideload-only. A future Play Store release
would use Play App Signing instead. See A18 and
[`keystore/README.md`](keystore/README.md).

### Q6 — Should app releases share the Termux package's version tags?

**Resolved (owner, 2026-11-14):** no. Android releases use a distinct
**`android-v*`** tag (0.1.0, 0.2.0, ...); the Termux package keeps its `v*`
tags such as `v0.40.0-termux.3`. The workflow triggers only on `android-v*`
tags (plus ordinary pushes and pull requests), so a Termux tag can neither
start it nor have files attached to its release.

### Q7 — Do you want data export/import (backup) in the app?

Uninstall currently deletes the library (A15). A simple export/import of
`files/tanoshi/` (ZIP via SAF or share sheet) would make upgrades/migrations
safe. This is a feature decision, not a bug.

### Q8 — Should LAN exposure stay a single toggle?

Today the toggle switches between `127.0.0.1` and `0.0.0.0`. Alternative
options the owner may want: a custom bind address, per-interface selection,
or a separate "allow LAN login" warning/confirmation step. Confirm the
desired UX and whether the plain-HTTP warning is enough.

**Update (0.3.0):** the Server screen's
[QR share card](README.md) now surfaces LAN sharing only while the server
is actually reachable on `0.0.0.0`, but the underlying setting is still the
single Expose on LAN toggle. Confirm that this is the desired UX (and whether
a per-interface choice is still wanted).

### Q9 — Should CI rebuild the native binaries?

The workflow builds only the app because the binaries are committed (A17).
If the owner wants reproducible/audited builds from source in CI, a second
(heavier) workflow would install NDK r26+, Rust 1.99.1, wasm tooling and
libarchive, then run the `native/` scripts and build the APK. Confirm whether
that is in scope.

### Q10 — Should Termux and Android binary builds be unified?

Both use the same patches but different build environments (Termux native
build vs. NDK cross-compile). Keeping two pipelines risks drift; unifying
them (or at least sharing version pins) is worth deciding after the first
successful APK release.

### Q11 — How should extension updates be delivered?

**Resolved (0.5.0, extended 0.6.0/0.7.0):** per-extension
install/update/remove from the **Sources** tab, backed by the built-in
`tanoshi-extension` catalog plus custom repositories added by URL (A30);
enable/disable uses the `.tanoshi-disabled` marker (A31). The web UI no longer
offers extension management at all (A32). Publishing/updating a catalog entry reaches installed apps
without a new APK; a server restart is required to load the change. There is
still no automatic/background extension update check (the user taps
Install/Update), no per-extension auto-update, and no on-device compilation of
extensions — porting/building is done with the exported sources +
`native/build_extension.sh` on Termux/desktop.

### Q12 — Is the current README default-login section still correct for the APK?

The root README documents `admin` / `tanoshi123` for the Termux package; the
APK has no default account (A7). The new "Android app (no Termux)" section
calls this out, but confirm the final user-facing wording and whether the
Termux default login should change as well.

### Q13 — Should updates be automatic or incremental?

**Resolved (owner, 2026-11-14):** no. The updater is user-initiated (a
**Check for updates** button in Settings), downloads the full release APK, and
never installs without Android's confirmation dialog. There is no background
update service and no delta/incremental patching — the simplest and safest
model for a sideloaded, self-hosted app (see A19).

### Q14 — Should release APKs keep a generic `app-debug.apk` name?

**Resolved (owner, 0.3.0):** no. New builds are named
`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk` (UTC date in CI, local date
otherwise) so downloads are self-describing. The in-app updater accepts both
the new prefix and legacy `app-debug.apk` assets, and the 0.1.0/0.2.0 release
assets keep their historical name. See A22.

### Q15 — Should the silent keep-alive be a user-facing setting?

**Resolved (owner, 0.4.0):** no. The hidden silent-audio keep-alive is active
exactly while the server runs, with no toggle; stopping the server stops it.
This keeps the anti-kill behaviour predictable and avoids a setting whose
effect users would struggle to observe (it is inaudible and has no separate
notification). See A24. If a future release needs a battery-focused option,
this is where to revisit it.

### Q16 — Should extension updates be automatic?

**Resolved (owner, 0.5.0):** no. Extension installs/updates are explicit
actions in the Sources tab; there is no background catalog polling, no
automatic download and no silent install. Catalogs are fetched when the tab is
opened or refreshed (per-repository failures are retried on demand), and a
server restart applies the change. See A27/A29.

### Q17 — Should custom extension repositories be verified/allow-listed?

**Resolved (0.6.0):** no central allow-list. Any HTTPS catalog URL or GitHub
repository link can be added (up to 10), because the feature exists to let the
community publish sources. The app enforces structural validation (HTTPS,
parseable catalog, HTTPS `.so` URLs, name/size/SHA-256 checks) and shows the
repository label/failure warnings, but cannot vet content; the UI/docs warn
that custom repositories are third-party native code and a trust decision (see
A29/A30).

### Q18 — Should the embedded web UI keep extension install/uninstall controls?

**Resolved (owner, 0.7.0):** no. The embedded Tanoshi web UI is backend-only
for extensions: the Available list and the Install/Update/Uninstall controls
are hidden because they never worked on Android. Installed sources and
per-source preferences remain in the web UI, and all extension management
happens in the app's Sources tab. See A32.

### Q19 — Should web-UI themes sync server-side across devices?

**Resolved (owner, 0.9.0):** no. Web-UI theme palettes are stored only in the
browser's `localStorage` (`settings:theme-palette`), so each device/browser
keeps its own theme and a new device defaults to Auto. Making them
server-side would need a settings schema/API change and would surprise users
who expect appearance to be local; this is documented as per-device (A33).

### Q20 — Should downloaded chapters live in the phone's shared storage?

**Resolved (owner, 0.13.0):** no. Offline chapters are a server-side cache in
`<TANOSHI_HOME>/.tempchapter`, created automatically and migrated from the old
`downloads` path; they are read through the server and never copied into
shared/local storage. Only explicit user exports (reader **Save image**,
**Backup Library**) write to the phone's Downloads folder via the app bridge
(A34/A35).
