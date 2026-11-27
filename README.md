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
- [Yashi client app](#yashi-client-app)
- [What's new in 0.16.8](#whats-new-in-0168)
- [What's new in 0.16.4](#whats-new-in-0164)
- [What's new in 0.16.3](#whats-new-in-0163)
- [What's new in 0.16.2](#whats-new-in-0162)
- [What's new in 0.16.1](#whats-new-in-0161)
- [What's new in 0.16.0](#whats-new-in-0160)
- [What's new in 0.15.1](#whats-new-in-0151)
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

## Yashi client app

**Yashi** is the companion Android client for a Yakushi server, released from
this same repository. It is a small, fast browser shell for connecting an
Android phone or tablet to a Yakushi server running on another device on your
network (or anywhere reachable by IP), without needing to type the URL into a
general-purpose browser every time.

- **First launch asks for camera permission** so the built-in QR scanner can
  read the connection QR code shown by Yakushi Server's LAN sharing card.
- **Start screen:** an address bar with a **Go** button, plus a **history** of
  every server you connected to and a **saved servers** list. Any history entry
  can be saved with one tap; saved entries have **edit** (change host/port) and
  **delete** actions.
- **Connect your way:** scan the server QR code, or tap **scan local network**
  to discover running Yakushi servers on the same Wi-Fi/LAN and connect with
  one tap (best-effort scan of the local /24 on the default ports **8080** and
  **18084**; custom ports can be typed/scanned).
- **Settings:** choose **System / Dark / Light** theme, and **clear data** to
  forget all saved addresses and connection history.
- **Full server UI:** once connected, Yashi displays the complete Yakushi web
  frontend in a full-screen WebView with a normal status-bar-padded viewport
  and a disabled/cleared cache. A small floating **back** button returns to the
  Yashi start screen; it is automatically hidden on the catalogue
  (`/catalogue`), manga (`/manga`) and reader (`/chapter`) pages, so it never
  covers those screens. The system back button first walks the WebView history,
  then returns to the start screen.
- **Known issue (alpha):** the **paged reader** can show blank/partial pages and
  mode switching/page next-prev may fail on Android WebView. **Use
  Webtoon/Continuous mode only** for reliable reading. Yashi tries a client-side
  `/chapter` injection to size/fit pages and add tap fallbacks, but this is not
  confirmed on real WebView devices.
- **Download:** `yashi-client-release-<date>.apk` from the latest release on
  this repository. It is signed with the same sideload key as Yakushi Server.
  Yashi is a client only: it does not bundle or run the server.

Yashi **0.2.0-alpha** is released together with Yakushi Server 0.16.8. It is an **alpha**: the paged reader is unreliable on Android WebView (use Webtoon/Continuous only); other features are expected working. See the
[Yashi source module](android/yashi/) and [Android README](android/README.md)
for build details.

---

## What's new in 0.16.8

- **Server web reader reverted to the known-good 0.15.1 implementation.** The
  0.16.3–0.16.7 paged-renderer rewrite was removed (patch 0016 deleted); the
  embedded web UI is again the 0.15.1 dist (`tanoshi-web-4b94016ac7719c6e.js` /
  `tanoshi-web-f9597b71814b98b9_bg.wasm`) and `libtanoshi.so` was rebuilt from
  it. This restores the original browser paged/double behaviour; no other
  server changes (version 0.16.8 / 1608).
- **Yashi 0.2.0-alpha** (versionCode 200, alpha asset
  `yashi-client-alpha-0.2.0-alpha-<date>.apk`): save-address crash fixed
  (namespaced history/saved keys), normal status-bar-padded WebView viewport
  with cache disabled/cleared, known-address/multi-subnet LAN discovery with
  `/health`, and a last-resort client-side `/chapter` reader injection
  (page-list sized to the real WebView height, paged images fitted and made
  non-interactive, settings/topbar raised, capture-phase left/right tap
  fallback). The injection was verified in Chromium but **not on a real
  Android WebView**.
- **Known issues (Yashi alpha) — read before installing:** the paged reader
  remains unreliable on Android WebView (blank/partial pages; mode switching
  and page next/prev controls may fail). **Use Webtoon/Continuous mode only**
  for reliable reading. Other Yashi features (QR scan, address/history/saved
  edit/delete, LAN discovery, theme, clear data) are expected to work.
- **Release:** tag `android-v0.16.8`, title
  "Yakushi Server 0.16.8 + Yashi 0.2.0-alpha", assets
  `yakushi-server-release-<date>.apk` and
  `yashi-client-alpha-0.2.0-alpha-<date>.apk`.

Previous release — **0.16.4** — and its features:

## What's new in 0.16.4

- **Yashi: saving an address no longer crashes.** History and saved rows used
  the same `LazyColumn` key (the same URL appears in both sections), crashing
  Compose after saving until app data was cleared. Keys are now namespaced
  `history:<url>` / `saved:<url>`.
- **Yashi: stronger LAN discovery.** It probes known saved/history addresses
  first and returns them if any respond, otherwise scans subnets from every Wi-Fi/Ethernet IPv4 plus saved-address
  prefixes (up to 4), with ports 8080/18084/8000/3000 plus preferred ports,
  `/health` then `/` probing, and the no-result dialog shows ports/subnets.
  The start screen keeps the status-bar-only top inset (no WebView cutout
  padding) so fullscreen/cutout mode has no leftover gap.
- **Paged reader + mode switching fixed** (embedded web UI, patch 0016):
  Single/Double/Auto render the current page/pair directly, clamp page indices,
  derive prev/next from the page count, and avoid RefCell borrow overlaps /
  holding the pages Ref while building DOM. Verified in headless Chromium
  (opening a chapter shows the page; Paged/Continuous/Double/Single switching
  renders images). Web UI and `libtanoshi.so` rebuilt (new JS/wasm dist).
- **Yakushi Server 0.16.4** includes the rebuilt web UI/server; both APKs are
  published on `android-v0.16.4` with title/notes
  "Yakushi Server 0.16.4 + Yashi 0.1.4".

Previous release — **0.16.3** — and its features:

## What's new in 0.16.3

- **Yashi: local-network discovery fixed.** It now uses the active network's
  IPv4 (LinkProperties, with a NetworkInterface fallback), probes the
  unauthenticated **`/health`** endpoint first (HTTP 200 = running Yakushi)
  with an HTML fallback, scans more default ports **8080/18084/8000/3000**
  plus preferred ports from saved/history servers, and the no-results dialog
  shows the scanned IP/ports.
- **Yashi: inset behaviour adjusted.** The start screen reserves only the
  **status-bar** top inset (not safe-drawing/cutout), so the padding disappears
  when the phone's fullscreen/cutout setting hides the status bar; the WebView
  no longer adds display-cutout padding, so fullscreen reading has no leftover
  top gap. The browser still hides the status bar while browsing and restores
  it on exit.
- **Paged reader no longer blank.** Single/Double/Auto paged modes now render
  the current page(s) directly with a proxied image source instead of relying
  on all-page visibility/src preload signals, with clamped page indices and
  prev/next maintained from the page count. Continuous/webtoon reading is
  unchanged.
- **Yakushi Server 0.16.3** includes the rebuilt web UI/server (patch 0016);
  both APKs are published on tag `android-v0.16.3` with title/notes
  "Yakushi Server 0.16.3 + Yashi 0.1.3" (`yakushi-server-release-<date>.apk`,
  `yashi-client-release-<date>.apk`).

Previous release — **0.16.2** — and its features:

## What's new in 0.16.2

- **Yashi: display-inset / status-bar fixes.** Yashi now draws edge-to-edge
  explicitly; the start screen applies safe-drawing top + horizontal padding so
  the header/address field/buttons sit below the status bar and clear of display
  cutouts. The full-screen browser/reader hides the device status-bar icons
  (time/battery/Wi-Fi) with transient swipe-to-reveal and pads the WebView by
  the display-cutout insets; the status bar returns on the start screen. The
  floating back button keeps its 96dp-above-bottom-bar placement and hidden
  catalogue/manga/reader behaviour.
- **Yakushi Server 0.16.2** is a re-release with no server/native changes from
  0.16.1; both APKs (`yakushi-server-release-<date>.apk`,
  `yashi-client-release-<date>.apk`) are published on tag `android-v0.16.2`
  with combined notes/title "Yakushi Server 0.16.2 + Yashi 0.1.2".
- Includes all 0.16.x features.

Previous release — **0.16.1** — and its features:

## What's new in 0.16.1

- **Yashi 0.1.1:** the floating back button is raised clear of the web UI's
  bottom navigation bar (bottom padding 96dp + navigation-bar insets) so it no
  longer covers the Library button on phones, and the WebView is now a stable
  remembered instance using the same zoom/image settings as the Yakushi Server
  app (images load automatically, network images allowed, zoom controls
  enabled), fixing blank chapter pages / missing reader images. The button
  stays hidden on `/catalogue`, `/manga` and `/chapter`.
- **Yakushi Server 0.16.1** is a re-release with no server/native changes from
  0.16.0; both APKs (`yakushi-server-release-<date>.apk`,
  `yashi-client-release-<date>.apk`) are published on tag `android-v0.16.1`
  with combined notes/title "Yakushi Server 0.16.1 + Yashi 0.1.1".
- Includes all 0.16.0 features (Yashi introduction, both APKs per release) and
  earlier fixes.

Previous release — **0.16.0** — and its features:

## What's new in 0.16.0

- **New companion app: Yashi 0.1.0.** A dedicated Android client for Yakushi
  servers: first-launch camera permission, QR scanning, address bar + history +
  saved servers (with edit/delete), local-network discovery, dark/light/system
  themes, clear-data settings, and a full-screen WebView with a floating back
  button that hides itself in the catalogue and manga reader.
- **Yakushi Server 0.16.0** is re-released alongside it with no server-side
  changes from 0.15.1; both APKs are published on the same GitHub release
  (`yakushi-server-release-<date>.apk` and `yashi-client-release-<date>.apk`).
- Version 0.16.0 (`android-v0.16.0`); includes all 0.15.1 fixes (continuous
  reading read status), 0.15.0 (pinned catalogue sources, corrected
  Local/External grouping) and earlier features.

---

## What's new in 0.15.1

- **Fixed chapter read status in seamless continuous reading.** Chapters after
  the first could stay unread because completion was only sent for an exact
  last-page visit. Now reaching a chapter's end sentinel marks that chapter
  complete before appending the next one, and a `last_page_seen` /
  `mark_crossed_chapters` tracker marks every chapter whose last page was
  crossed during fast scrolling. The server keeps completion sticky, so later
  partial progress cannot unread a finished chapter.
- **Version 0.15.1** (`android-v0.15.1`, `yakushi-server-release-<date>.apk`);
  rebuilt web UI/server, CSS unchanged, no server API/schema changes; includes
  all earlier features.

Previous release — **0.15.0** — and its features:

## What's new in 0.15.0

- **Pin Catalogue sources.** Each Catalogue source row has a **Pin**/**Unpin**
  button; pinned ids are stored per device in localStorage
  (`catalogue_list_pinned`, in pin order) and a **Pinned Sources** section is
  shown at the very top of the Catalogue page. Pinned sources are removed from
  the Local/External sections to avoid duplicates and return to their group
  when unpinned.
- **Fixed Local/External source grouping.** Sources with ids >= 1,000,000
  (user-installed extensions with huge hashed plugin ids) are now under
  **External Sources**; bundled/pre-installed extensions (small ids) and the
  built-in Local folder sources (ids starting at 10000) are under **Local
  Sources** (previously inverted).
- **Version 0.15.0** (`android-v0.15.0`, `yakushi-server-release-<date>.apk`);
  rebuilt web UI/server, no server API/schema changes; includes all earlier
  features.

Previous release — **0.14.0** — and its features:

## What's new in 0.14.0

- **Extensions hot-reload without a server restart.** The server now exposes
  **`POST /api/extensions/reload`**, which re-scans both plugin directories
  through a non-destructive `ExtensionManager::reload_all()` (drains sources,
  closes queues/stops workers, then loads again) and returns
  `{"ok":true,"sources":N}`. The app calls it automatically after
  install/update/remove/enable/disable, refreshes the Sources list and shows
  **"Sources updated"**; the **Server restart needed** banner remains only as a
  fallback if the server is stopped or the reload fails.
- **Version 0.14.0** (`android-v0.14.0`, `yakushi-server-release-<date>.apk`);
  server/rebuilt, embedded web UI unchanged from 0.13.9; includes all earlier
  features.

Previous release — **0.13.9** — and its features:

## What's new in 0.13.9

- **Seamless continuous reading.** In Continuous mode the Prev/Next chapter
  buttons are gone: the next chapter's pages are **appended to the same
  scroll** when you reach the end, and scrolling back to the very top loads the
  previous chapter (with a guard against the initial position restore). Small
  **"No previous chapter"**/**"No more chapters"** labels replace the buttons,
  and progress/URL/completion are recorded per chapter via a page-to-chapter
  mapping even across appended chapters.
- **Automatic webtoon layout.** If the first loaded pages are long strips
  (height ≥ 2.5 × width), the reader switches to **Continuous + Fit Width**
  (+ padding off), saves the settings and shows a one-time "Webtoon layout
  detected" snackbar; the settings panel is reduced to the Fit controls with a
  note.
- **Version 0.13.9** (`android-v0.13.9`, `yakushi-server-release-<date>.apk`);
  includes all earlier features.

Previous release — **0.13.8** — and its features:

## What's new in 0.13.8

- **Desktop/tablet Library tabs fixed.** The tab strip now offsets past the
  fixed 5rem desktop sidebar (5rem left + right margin at ≥ 1024px), so the
  All/Default tabs are no longer covered; tabs are restyled like desktop-browser
  tabs (bottom-aligned strip, rounded top corners, connected active tab with
  primary text/inset accent).
- **Library tab options are per device.** Settings → Categories has a
  **Library tabs** panel with **All tab: Shown/Hidden** and **Counts:
  Shown/Hidden** toggles (localStorage `settings:library`, per device). The
  Library hides the All tab when disabled (falling back to the
  uncategorized/Default tab) and only shows `Name (count)` when counts are on.
- **Inline category rename.** Each category row in Settings → Categories has
  a pencil/**Rename** button; edit inline and press Enter to save via the
  server's existing `updateCategory` mutation (renames apply to every device;
  bulk edit still exists).
- **Version 0.13.8** (`android-v0.13.8`, `yakushi-server-release-<date>.apk`);
  includes all earlier features.

Previous release — **0.13.7** — and its features:

## What's new in 0.13.7

- **Migrate from the manga page.** Saved manga have a **Migrate** icon in the
  manga top bar; pick an installed source, land in its catalogue pre-filled
  with the manga title, and confirm the existing source-path migration flow
  (Suwayomi/Mihon style). Hidden for unsaved manga.
- **Change Library action.** Manga in the library get a **Change Library**
  button that opens the category picker and replaces the manga's categories
  (`deleteFromLibrary` + `addToLibrary`; empty selection = uncategorized).
  Read progress, chapters and trackers stay intact.
- **Tabbed Library page.** `/library` now has **All** plus one tab per
  category with counts; **All** merges uncategorized + every category and
  deduplicates by manga id, while selecting a tab shows that category. The old
  `/library/categories` page redirects to `/library` and the topbar Categories
  link is gone; sort/filter/search are unchanged.
- **Version 0.13.7** (`android-v0.13.7`, `yakushi-server-release-<date>.apk`);
  includes all earlier features.

Previous release — **0.13.6** — and its features:

## What's new in 0.13.6

- **Layout Preference presets are server-shared.** Presets (Settings → Layout
  Preference) are now stored on the server in `layout_presets.json` next to
  `config.yml` and served over **`GET/PUT /api/layout-presets`**, so every
  device connected to the same server sees the same preset list (this device's
  old local presets are uploaded once if the server has none). Each device
  still remembers its **own selected preset** in localStorage and falls back to
  Default if it disappears from the shared list. The endpoint is
  unauthenticated (local server; reachable on LAN when exposed).
- **Gapless double-page reader.** Display Mode **Double** no longer splits
  spare horizontal space into the middle between pages: the page list is
  centred and the images have no auto margins, so letterboxing/spare space goes
  to the outer sides and two-page spreads stay together.
- **Version 0.13.6** (`android-v0.13.6`, `yakushi-server-release-<date>.apk`);
  includes all earlier features.

Previous release — **0.13.5** — and its features:

## What's new in 0.13.5

- **Chapter downloads work for every plugin extension.** The worker now rejects
  only the built-in Local source's id range (`10000..1_000_000`) instead of
  every id above 10000, so hashed plugin extensions (QiScans etc.) download
  normally. The Local source itself stays non-downloadable (already local).
- **Downloaded chapters are page images in public storage.** Each chapter is
  extracted to
  `/storage/emulated/0/yakushi/.downloaded/<Manga Title>/<Chapter Title>/`
  (temporary CBZ removed afterwards; reader reads the folder). The app
  migrates `download_path` there and requests **All files access** on Android
  11+ (legacy storage permission on 7–10).
- **Per-chapter Download button, queue visibility and progress.** Every
  chapter row has its own download icon; signed-in users can see the download
  queue/downloaded list; rows show `downloaded/total` progress, the chapter
  list refreshes every 4 s while downloading (up to ~2 minutes), and the
  **Downloaded** check icon/label appears without a reload.
- **Reader Save image was removed** (the 0.13.0–0.13.4 popup/bridge); the
  page images are now available directly in the downloaded chapter folders.
- **Version 0.13.5** (`android-v0.13.5`, `yakushi-server-release-<date>.apk`);
  includes all earlier features.

## What's new in 0.13.1 (superseded)

0.13.1 was a UI/bridge fix release for the 0.13.0 download work, including a
now-removed reader image-save helper. Its download feedback/progress work is
included in 0.13.5 and the save feature was replaced by the public-folder
chapter downloads.

## What's new in 0.13.0 (superseded)

0.13.0 made chapter downloads work for signed-in users, added the initial
download cache/feedback and renamed the repository to Yakushi. Its private
`.tempchapter` cache and reader image-save helper were replaced in 0.13.5 by
the public-folder page-image downloads; see [Downloads and
trackers](#downloads-and-trackers).

## Features

### Reader

- **Reading modes:** paged and continuous. **Server web UI:** the 0.16.8
  release restores the known-good 0.15.1 web reader (the 0.16.3–0.16.7 paged
  rewrite was reverted), so browser paged/double behaviour is the original
  implementation. Continuous mode is seamless from
  0.13.9: the next chapter's pages append to the same scroll, scrolling to the
  very top loads the previous chapter, and "No previous chapter"/"No more
  chapters" labels replace the old Prev/Next buttons. From 0.15.1 chapters
  passed via auto-append/fast scroll are marked read correctly.
- **Display:** single, double and auto (landscape-aware) pages; double-page
  spreads are gapless (spare space at the sides, not in the middle, from
  0.13.6).
- **Direction:** left-to-right and right-to-left.
- **Fit:** fit all, fit width, fit height; zoom; page padding.
- **Automatic webtoon layout:** long-strip pages (height ≥ 2.5 × width) switch
  the reader to Continuous + Fit Width + padding off, with a one-time snackbar
  and a fit-only settings panel (from 0.13.9).
- **Backgrounds:** white and black.
- **Layout Preference presets** — bundle the reader options (mode, padding,
  display, direction, background, fit) under names, with a built-in
  **Default** preset (Paged, padding off, Single, Left to Right, White, Fit
  All) and **Add New Preset**. Since **0.13.6 presets are stored on the
  server** (`layout_presets.json` next to `config.yml`, served via
  `GET/PUT /api/layout-presets`) and shared across devices, while the
  **selected** preset stays per device; a new device starts at Default if the
  shared list lacks that name.
- **Tap Area** — choose whether the right or the left screen edge belongs to
  the next page ("bottom right: NEXT, bottom left: PREV" or the mirrored
  layout).
- Page counter, chapter navigation, keyboard shortcuts and progress saving.

### Library and catalogue

- **Library** with category tabs (plus an optional **All** tab that merges
  uncategorized and every category), unread counts and recent updates. Tab
  display (All shown/hidden, counts shown/hidden) is a per-device setting in
  Settings → Categories; the tabs are browser-style and offset past the
  desktop sidebar. The old category-list page redirects to the tabbed library.
- **Library actions on a manga** — add/remove, **Change Library** (replace the
  manga's categories with the picker), and **Migrate** to another installed
  source from the manga top bar (source-path migration via the catalogue).
  Categories can be renamed inline in Settings → Categories (pencil button,
  Enter, server `updateCategory`).
- **Catalogue** browsing per source with popular/latest/search listings and
  **infinite scroll** (loads the next page automatically). Source rows can be
  **pinned** (per device) into a **Pinned Sources** section at the top, and the
  Local/External grouping uses the plugin id boundary (external iff
  id >= 1,000,000) so user-installed extensions are External.
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
- **App-only extension management** (Sources tab in the app): install, update,
  reinstall, disable/enable and remove extensions from the catalog. Since
  0.14.0 the running server **hot-reloads** plugins immediately after each
  change; the restart/start prompt is only a fallback when the server is stopped
  or the reload fails.
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

- **Per-chapter Download button** in every chapter row (plus the selection-based
  download) queues chapters for offline reading; any signed-in user can use it
  and see the Download Queue/downloaded list (fixed/expanded by 0.13.5).
- Downloaded chapters are extracted **page images in public storage** under
  `/storage/emulated/0/yakushi/.downloaded/<Manga Title>/<Chapter Title>/`
  (temporary CBZ deleted after extraction; the reader reads the folder). The
  app asks for **All files access** on Android 11+ and the legacy storage
  permission on Android 7–10; without it the download cannot be written.
- Rows show `downloaded/total` progress while queued, the manga detail
  refreshes every 4 seconds (up to ~2 minutes) so the **Downloaded** check
  icon/label appears without reloading, and a check + "Downloaded" marker is
  shown for completed chapters.
- The old source-id guard that blocked hashed plugin extensions (QiScans etc.)
  as "Local" is fixed: only ids `10000..1_000_000` (the actual Local range) are
  rejected. The built-in Local source itself is still not downloadable because
  its pages are already local files.
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

1. Download `yakushi-server-release-<date>.apk` (the server app) and, for a
   second device, optionally `yashi-client-release-<date>.apk` (the Yashi
   client) from the
   [releases page](https://github.com/spainclaro-debug/Yakushi/releases/latest).
2. Allow installing unknown apps for your browser/file manager and open the
   APK.
3. Start Yakushi Server, press **Start server**, and open the web UI (Browser
   tab). On the other device, open Yashi and connect by address, QR or local
   scan.

> Each release publishes **two** assets: `yakushi-server-release-<date>.apk`
> (the Android app is called **Yakushi Server**; the web frontend/PWA keeps the
> short name **Yakushi**) and `yashi-client-release-<date>.apk` (the companion
> **Yashi** client). They have separate version lines (Yakushi 0.16.8,
> Yashi 0.2.0-alpha/200) but share the same release tag `android-v0.16.8` and
> signing key. The Yashi asset is named
> `yashi-client-alpha-0.2.0-alpha-<date>.apk` and is an **alpha**: paged reader
> known issues (use Webtoon/Continuous).

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
| `android/` | The standalone Yakushi Server app (Kotlin/Compose) and its prebuilt `arm64-v8a` binaries, plus the native build pipeline. |
| `android/yashi/` | **Yashi 0.2.0-alpha**, the companion Android client (pure Kotlin/Compose, no native code): address bar/history/saved servers, QR scanning, local-network discovery and a full-screen WebView for a Yakushi server. |
| `patches/` | Fork patches applied to upstream Tanoshi and its extensions. |
| `extensions/` | Sources of the ported extensions (Atsumaru, MadaraDex, QiScans, MangaBall, HentaiNexus). |
| `lib/` | Termux package build scripts. |
| `vendor/` | Vendored `compress-tools` crate patched for Android. |
| `install.sh`, `update.sh`, `selfupdate.sh`, `uninstall.sh` | Termux package management. |

---

## Releases

- The current release is **0.16.8** (`android-v0.16.8`): **Yakushi Server
  0.16.8** (`yakushi-server-release-<date>.apk`, web reader reverted to the
  0.15.1 implementation) and **Yashi 0.2.0-alpha**
  (`yashi-client-alpha-0.2.0-alpha-<date>.apk`, alpha with known paged-reader
  issues) are published together on the same GitHub release with title
  "Yakushi Server 0.16.8 + Yashi 0.2.0-alpha".
- Android releases use tags **`android-vX.Y.Z`** (for example
  `android-v0.16.8`). CI builds both apps (`:app` and `:yashi`) and publishes
  both APKs with combined release notes (including the Yashi alpha known
  issues).
- Yashi has its own version line (**0.2.0-alpha / versionCode 200**); local
  builds default to it, and CI can override it with `-PyashiVersionName` /
  `-PyashiVersionCode`. The server app defaults to 0.16.8 / versionCode 1608.
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
