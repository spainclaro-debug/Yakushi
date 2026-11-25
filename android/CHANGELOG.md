# Changelog — Yakushi Server for Android

All notable changes to the standalone Android app in `android/`. The app is
versioned independently of the upstream Tanoshi server it bundles (currently
Tanoshi **0.40.0**) and is released as GitHub releases tagged **`android-vX.Y.Z`**
(for example `android-v0.13.8`). The Termux package in the repository root has
its own `PACKAGE_TAG` and `v*` release tags.

Versioning: local/Gradle builds default to the version in
`android/app/build.gradle.kts`. CI derives `versionName` from the
`android-vX.Y.Z` tag and sets
`versionCode = major * 10000 + minor * 100 + patch`, passing both to Gradle
with `-PappVersionName` / `-PappVersionCode`. The sideload APK is named
`yakushi-server-release-<yyyy-MM-dd>.apk` (0.12.1 renamed the asset; earlier
0.3.0–0.12.0 builds used `tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`),
where the date is the UTC build date in CI (via `-PapkDate`) and the local
date otherwise.

---

## 0.13.8

### Fixed

- **Desktop/tablet Library tabs no longer hidden under the sidebar.** The
  desktop navigation is a fixed 5rem left sidebar; the tab strip was not offset
  so its first tabs (All/Default) were covered. `.library-tabs` now gets
  `margin-left: 5rem` and a right margin at `min-width: 1024px`, matching the
  main content offset.

### Added

- **Category tab display options (per device).** Settings → Categories now
  has a **Library tabs** panel with two toggles persisted in localStorage
  (`settings:library`, per device): **All tab: Shown/Hidden** and
  **Counts: Shown/Hidden**. The Library page hides the All tab when disabled
  (an All selection falls back to the uncategorized/Default tab) and only
  appends `Name (count)` to category tab labels when counts are enabled.
- **Rename a library inline.** Each category row in Settings → Categories
  has a pencil/**Rename** button (title "Rename library") that turns that row
  into an inline text input; pressing **Enter** saves through the existing
  `updateCategory` mutation and refreshes the list. The existing bulk edit
  mode is unchanged, and the rename button is hidden while bulk editing; the
  row's count/Other entries are untouched.

### Changed

- **Tabs restyled like desktop-browser tabs.** `.library-tabs`/`.library-tab`
  are now a bottom-aligned strip with a bottom border, rounded top corners,
  adjacent borders and a negative bottom margin so the active tab connects to
  the content area; the active tab is raised, uses the primary colour for text
  plus an inset top accent, and tabs get a hover opacity. The strip remains
  horizontally scrollable.
- **Web UI and `libtanoshi.so` rebuilt** with the new
  `android/native/patches/0011-web-ui-library-tabs-options-sidebar.patch`
  (applied after 0010; `library_settings.rs`, `settings_categories.rs`,
  `library.rs`, `styles.scss`); version **0.13.8** (versionCode 1308, tag
  `android-v0.13.8`). APK filename scheme unchanged. No server API/schema
  changes.

### Notes

- The tab display options are **per device/browser** (localStorage
  `settings:library`); category renames are **server-side** through the
  existing `updateCategory` mutation, so they appear on every device.
- Hidden-All fallback: if the selected library was the synthetic All tab and
  All is turned off, the Library page switches to the uncategorized/Default
  tab rather than showing an empty selection.

## 0.13.7

### Added

- **Migrate from the manga page.** Saved manga (id ≠ 0) now show a **Migrate**
  icon in the manga top bar next to Refresh/Open. It opens a source-picker
  modal listing the installed sources (`installedSources`); choosing one writes
  the existing migration context (`migration::set` with
  from_manga_id/from_source_id/from_title/to_source_id) and routes to that
  source's catalogue **pre-filled with the manga title**
  (`Route::Catalogue { id, latest:false, query:Some(title) }`). The catalogue's
  existing migration banner/confirm then calls `migrateManga` (source-path
  based) and opens the migrated manga — the same flow as Suwayomi/Mihon.
  The button is hidden for unsaved manga; attempting without an id shows a
  snackbar.
- **Change Library on the manga page.** Manga already in the library show a
  **Change Library** action (folder icon) next to In Library/Read. It opens the
  existing category selector (`SelectCategoryModal`); on OK the UI calls
  `deleteFromLibrary` then `addToLibrary(mangaId, category_ids)` so the
  selected categories **replace** the old ones (no category = uncategorized/
  Default). Read progress, chapters and trackers are manga-level and stay
  intact; a **"Library updated"** snackbar confirms, errors are shown.
- **Tabbed Library page.** `/library` now shows **All** plus one tab per
  library category (labels include counts) instead of the separate category
  list. **All** merges uncategorized manga with every category — the API's
  `categoryId: null` only returns uncategorized, so each category is fetched
  and results are deduplicated by manga id. Selecting a tab fetches that
  category; the existing sort/filter/search still apply. The old
  `/library/categories` page immediately redirects to `/library`, and the
  topbar Categories link is hidden. New `.library-tabs`/`.library-tab` CSS
  (active tab uses the primary colour).

### Changed

- **Web UI and `libtanoshi.so` rebuilt** with the new
  `android/native/patches/0010-web-ui-manga-actions-library-tabs.patch`
  (applied after 0009; `manga.rs`/`library.rs`/`library_list.rs`/`styles.scss`);
  version **0.13.7** (versionCode 1307, tag `android-v0.13.7`). APK filename
  scheme unchanged (`yakushi-server-release-<yyyy-MM-dd>.apk`). No server API or
  database schema changes.

### Notes

- Migration is source-path based and only available for manga already saved in
  the library; the flow starts from the manga page and completes in the
  catalogue's migration mode.
- `Change Library` is a category replacement (delete + re-add), so a manga
  leaves its previous categories; progress/trackers are untouched.
- The Library **All** tab can be slightly slower on large libraries because it
  fetches every category and merges/dedupes client-side.

## 0.13.6

### Added

- **Layout Preference presets are shared by the server.** The web UI used to
  keep the Layout Preference presets only in browser localStorage, so a new
  device showed only the **Default** preset. The server now has a new REST
  endpoint **`GET/PUT /api/layout-presets`** backed by
  `crates/tanoshi/src/presentation/rest/layout_presets.rs`, storing
  **`layout_presets.json` next to `config.yml`** (atomic tmp+rename; returns
  `{"presets":[]}` when the file is missing or invalid). The web UI fetches
  the shared list on load, **uploads this device's existing local presets once
  when the server has none** (migration for existing users), and saves every
  newly created preset with `PUT`. Presets created on one device therefore
  appear on every device connected to the same server.
  - The **selected** preset name stays **per device** in localStorage
    (`settings:layout_selected`), so a phone and tablet can use different
    layouts from the same shared list; if the selected name disappears from the
    shared list the device falls back to Default.
  - The endpoint is **unauthenticated**, like the server's image endpoint
    (loopback by default; LAN clients can reach it when Expose on LAN is on).
  - Creating presets is unchanged (no rename/delete feature); the layout UI
    text now says presets are stored on the server and shared.

### Fixed

- **Double-page reader gap.** In Display Mode **Double** the two page images
  used `margin-left:auto; margin-right:auto`, dividing spare horizontal space
  into the middle between the pages. The double page-list is now
  `justify-content: center` and the images use `margin-left/right: 0`, so any
  spare space/letterboxing goes to the **outer sides** and two-page spreads are
  no longer pushed apart/cropped in the middle. Continuous and single-page
  modes are unchanged.

### Changed

- **Web UI and `libtanoshi.so` rebuilt** with patches **0008** (server REST
  module + route + copied `layout_presets.rs`) and **0009** (layout-preset
  server sync + double-page CSS); version **0.13.6** (versionCode 1306, tag
  `android-v0.13.6`). APK filename scheme unchanged
  (`yakushi-server-release-<yyyy-MM-dd>.apk`).

### Notes

- `layout_presets.json` lives in the server data directory next to
  `config.yml`; it contains the custom presets only (Default is implicit) and
  is uploaded to all devices by the web UI. It is included in the app's private
  data, so uninstalling deletes it unless exported.
- The API accepts/returns a JSON object with a `presets` array; malformed GET
  payloads are treated as an empty list and writes are rejected with
  `400`/`500` rather than corrupting the file.

## 0.13.5

### Added

- **Per-chapter Download button.** Every chapter row now has its own download
  icon that queues just that chapter (the row's tap/long-press navigation
  ignores the button). The **Download Queue** and downloaded-chapters list are
  visible to any signed-in user (the queue queries/subscriptions now use
  `AuthGuard` instead of `AdminGuard`), with per-chapter `downloaded/total`
  progress, automatic detail refresh every 4 seconds while a download is
  pending (up to 30 ticks) and a check icon + **Downloaded** label/title.
- **Downloads from every plugin extension.** The download worker's guard used
  to treat every `source_id >= 10000` as the built-in Local source and silently
  dropped the queue entry. It now rejects only `10000..1_000_000` (the actual
  Local range), so hashed plugin ids (e.g. QiScans `5806040666300479660`)
  download normally; verified end-to-end (external chapter → complete extracted
  chapter folder → page images served). The built-in **Local** source is still
  not downloadable (its pages are already local files).

### Fixed

- **Downloaded chapters are extracted to public storage as image files.**
  The server still downloads a temporary CBZ into the manga folder, but now
  extracts every page into
  **`/storage/emulated/0/yakushi/.downloaded/<Manga Title>/<Chapter Title>/`**
  (no source-name level anymore), deletes the archive on success, and stores
  the **folder path** as the chapter's downloaded path. The reader reads image
  folders (`ChapterService` selects `get_pages_from_dir`, which returns
  image-only files in natural order), so offline reading works directly from
  the folder; removal/re-download handles both folders and legacy archives
  (`remove_dir_all`/`remove_file`).
- **Public download path + storage permission.** The Android app seeds
  `download_path` as
  `<Environment.getExternalStorageDirectory()>/yakushi/.downloaded`, creates
  it, and migrates existing configs from `<home>/downloads` /
  `<home>/.tempchapter` (quoted keys included). The manifest declares
  **`MANAGE_EXTERNAL_STORAGE`** (All files access) and legacy
  `WRITE_EXTERNAL_STORAGE` for SDK 29, with
  `requestLegacyExternalStorage="true"`; `MainActivity` asks for All files
  access on Android 11+ (or the legacy storage permission on 7–10) at launch.
- **Web UI and `libtanoshi.so` rebuilt** with patches 0005/0007; version
  **0.13.5** (versionCode 1305, tag `android-v0.13.5`). APK filename scheme
  unchanged.

### Removed

- **Reader Save image feature removed.** The long-press/right-click save
  popup, `TanoshiApp.saveImage` bridge, the image-save CSS/JS and the WebView
  `setDownloadListener` were removed; the reader suppresses the context menu as
  before (patch 0006 deleted). Downloading a chapter now saves its page images
  to the public folder above, and individual images can be copied from there.

### Notes

- The server's own default `download_path` remains `.tempchapter` for
  non-app runs, but the app always writes the public
  `.../yakushi/.downloaded` path into `config.yml` and migrates existing
  installs. Downloaded chapters are now intentionally in user-visible public
  storage.
- All 0.13.0–0.13.4 behaviour not listed here is unchanged; patch 0006
  (Save image) is deleted, patches 0005 and 0007 are the final download/UI
  patches.

## 0.13.1

### Fixed

- **Reader Save image works in paged mode too.** Paged reading renders the
  navigation/tap overlays above the page images, so the long-press/right-click
  event target was the overlay rather than an `<img>` and no menu appeared.
  `image_menu_for` now falls back to the currently visible image in the
  `#page-list` container when the target is not an image (the fallback is
  restricted to **Paged** mode); continuous mode is unchanged. The browser
  fallback also now starts the normal download link when the app bridge
  returns a non-OK result, not only when the bridge is absent.
- **Saving an image actually downloads it to Downloads.** `WebAppBridge.saveImage`
  no longer fetches the image itself into MediaStore; it enqueues the proxied
  image URL with the system **DownloadManager**
  (`setDestinationInExternalPublicDir(Downloads, name)`, MIME chosen from the
  file extension, `VISIBILITY_VISIBLE_NOTIFY_COMPLETED`), which writes the
  image to the phone's public **Downloads** folder on every supported Android
  version without storage permissions and shows a completion notification.
  `BrowserScreen` also installed a `setDownloadListener` fallback so the
  in-WebView anchor download is queued through DownloadManager too.
- **Chapter download feedback, progress and the Downloaded marker.** The
  chapter download action now refuses an empty selection ("Select at least one
  chapter to download") and confirms a queued download ("Download queued —
  chapters are saved on the server"); the chapter list shows per-chapter
  progress (`downloaded/total`) while a download is queued, refreshes the manga
  detail every 4 seconds while any chapter has a pending download status, up to
  30 refreshes (~2 minutes) (so the marker appears without a manual reload), and the downloaded marker now
  has a `title="Downloaded"` plus a visible **Downloaded** label next to the
  check icon. The actual server-side worker was verified end-to-end (a real
  Guya chapter produced a complete CBZ under `.tempchapter`).
- **Quoted-key `download_path` migration.** `ConfigManager`'s migration regex
  now also matches quoted YAML keys (`'download_path':` / `"download_path":`),
  so existing configs written with quoted keys are migrated to
  `<TANOSHI_HOME>/.tempchapter` as intended.
- **Web UI and `libtanoshi.so` rebuilt** with the updated reader patch and the
  new `0007-web-ui-download-progress.patch`; version **0.13.1** (versionCode
  1301, tag `android-v0.13.1`). The APK filename scheme is unchanged
  (`yakushi-server-release-<yyyy-MM-dd>.apk`).

### Notes

- The reader menu now works in both continuous and paged modes; saved images
  are queued through DownloadManager into the public Downloads folder on all
  supported Android versions (the 0.13.0 app-specific external Downloads path
  for Android 7–9 is no longer used for image saves).
- All 0.13.0 behaviour is unchanged: any signed-in user can download chapters,
  offline chapters are cached server-side in `.tempchapter`, and the app
  migrates old configs.

## 0.13.0

### Added

- **Save images from the reader.** Long-press (or right-click) an image while
  reading to open a popup with **Save image** / **Cancel**. **Save image**
  writes the image you are viewing (the already-proxied page URL) to the
  phone's **Downloads** folder: the web UI calls
  `window.TanoshiApp.saveImage(url, name)` through the app bridge, which
  fetches the bytes and saves them via **MediaStore Downloads** on Android 10+
  or the app's external Downloads folder on Android 7–9. In a normal browser
  the same action falls back to a standard download link. Files are named
  `yakushi-chapter-<chapter>-<timestamp>.jpg`, and a snackbar reports the
  saved location or the error. No storage permission is requested. Implemented
  in `android/native/patches/0006-web-ui-reader-image-save.patch` (reader menu
  + CSS) and `WebAppBridge.saveImage` / `writeBytesToDownloads`.
- **Chapter downloads work for every signed-in user.** The chapter-list
  **Download** button no longer returns **Forbidden**: the download mutations
  now use a new `AuthGuard` that only requires a signed-in account (the
  admin-only queue inspection and download-queue repro controls stay
  admin-only). Implemented in
  `android/native/patches/0005-downloads-tempchapter.patch`.

### Fixed

- **Offline chapter downloads are cached server-side.** Downloaded chapters
  now live in the server data directory under **`.tempchapter`**
  (`<TANOSHI_HOME>/.tempchapter`) instead of `<home>/downloads`. The server
  creates the directory on every start (and the default `download_path` in
  `config.yml` now points there); the Android app seeds
  `download_path: <home>/.tempchapter` for new installs and migrates existing
  configs from the old `<home>/downloads` path before starting the server.
  Downloaded chapters are readable offline without copying anything into the
  phone's shared/local storage, and removing a download frees the space. The
  app exposes the directory as `AppPaths.tempChapters`.
- **Web UI and `libtanoshi.so` rebuilt** with the reader image-save and
  downloads patches; the vendored dist in `android/native/web-dist/` and the
  embedded server binary were rebuilt.
- **Version 0.13.0:** `versionCode` 1300 in the Gradle default; the
  `android-v0.13.0` tag produces `versionCode` 1300. The APK filename scheme
  is unchanged (`yakushi-server-release-<yyyy-MM-dd>.apk`, a single asset per
  release since 0.12.1).

### Notes

- The repository is now `spainclaro-debug/Yakushi` (the old `tanoshi-mobile`
  and `tanoshi-termux` URLs redirect); the app id, data directories and
  signing key are unchanged, so existing installs update in place.
- The reader menu works in both the embedded app WebView and a normal browser.

## 0.12.1

### Changed

- **The Android app is named Yakushi Server.** The launcher label, the
  notification channel/title and the in-app server labels now say **Yakushi
  Server**; the web frontend/PWA keeps the short name **Yakushi** (tab title,
  manifest, icons and splash unchanged).
- **A single APK asset per release.** The workflow no longer copies the APK
  under the legacy `tanoshi-mobile-server-release-<date>.apk` name; releases
  attach only `yakushi-server-release-<date>.apk`. The in-app updater still
  accepts the previous prefix if an older release is encountered.
- **Version 0.12.1:** local builds default to `versionCode` 1201; the
  `android-v0.12.1` tag produces `versionCode` 1201.

## 0.12.0

### Changed

- **Complete Yakushi branding in the web UI.** The browser tab title, the
  favicon, the PWA icons (192/512/apple-touch) and the PWA splash now use the
  Yakushi logo; the installable-app manifest name/colors were already Yakushi.
- **New launcher icon and splash screen.** The Android adaptive icon is the
  Yakushi glyph on the brand red (the previous Tanoshi vector was removed), and
  the app's launch screen shows the Yakushi logo on red for Android 12+ (system
  splash API) and older releases (window background). The notification small
  icon is the monochrome Yakushi glyph.
- **Local source icon.** The server's built-in "Local" source uses
  `/icons/192.png`, which is now the Yakushi logo, so local source rows in the
  catalogue show the fork's icon.
- **Settings footer links.** "Changelog" points at the Yakushi repository's
  `android/CHANGELOG.md` and "Github" at the Yakushi repository; "Website" and
  "Discord" links were removed because the fork does not have them yet.
- **Frontend version matches the server.** The web UI's package/manifest
  version is now the server version (0.40.0) instead of the upstream frontend
  version, so everything reports the same version.
- **Version 0.12.0:** local builds default to `versionCode` 1200; the
  `android-v0.12.0` tag produces `versionCode` 1200.

## 0.11.0

### Changed

- **The app is now Yakushi.** Yakushi is a fork of the Tanoshi self-hosted
  manga server. The launcher name, notification titles, screen labels, the
  embedded web UI title/manifest, the documentation and the APK name
  (`yakushi-server-release-<date>.apk`) use the new name. The application id
  (`dev.spainclaro.tanoshi`), data directories and signing key are unchanged,
  so existing installs upgrade in place.
- **In-app updater** now looks at the Yakushi repository and prefers
  `yakushi-server` release assets, while still accepting the previous
  `tanoshi-mobile-server` asset name. Releases publish a compatibility copy
  under the old name so 0.10.0 and earlier installs can still update.
- **Version 0.11.0:** local builds default to `versionCode` 1100; the
  `android-v0.11.0` tag produces `versionCode` 1100.

### Added

- **Register button on the login page.** Once the server has its first
  (admin) account, the login page shows **Register** next to **Login**. A new
  account is created as a regular user and signed in immediately. Registration
  is controlled by the new `enable_registration` server option (default
  `true`); when disabled, only admins can create accounts (Settings → Users).
- The web UI was rebuilt with the fork branding (`yakushi-branding.patch`) and
  the login changes; `libtanoshi.so` and the Termux package binary were
  rebuilt. The backup/restore and theme features are unchanged and included.

## 0.10.0

### Added

- **Backup and Restore page.** Settings → **Backup and Restore** has two
  actions:
  - **Backup Library** writes a versioned `tanoshi-backup` JSON file with the
    signed-in user's library titles (including categories), the complete
    reading history (chapter, read time and last page), the sources those
    entries use and the chapter currently being read. In the app the file is
    saved to the phone's **Downloads** folder (MediaStore on Android 10+); in
    a normal browser it is downloaded normally.
  - **Restore Library** opens the system **file picker**, uploads the chosen
    backup and restores the library, categories and read progress. Titles are
    matched by source + source path and chapters by path (falling back to
    number/title), so a backup restores correctly on a different server.
  - **Missing sources** used by the backup are listed with their version and an
    **Install Missing Sources** button. The app installs them through its
    extension catalog, restarts the server and the restore is retried
    automatically; on a Termux/desktop server the install is done by the
    server's own extension installer.
- **App ↔ web bridge.** The WebView now exposes a small `TanoshiApp` bridge
  for saving the backup file and installing extensions, and it handles
  `<input type="file">` through the system file picker
  (`WebChromeClient.onShowFileChooser`).

### Changed

- **Embedded web UI rebuilt** with the new
  `patches/tanoshi-web-ui-backup.patch` +
  `patches/tanoshi-web-ui-backup-settings.patch` (applied by both the Android
  and Termux build scripts). The vendored dist in `android/native/web-dist/`
  and `libtanoshi.so` were rebuilt; the Termux package binary was rebuilt with
  the same patches.
- **Version 0.10.0:** local builds default to `versionCode` 1000; the
  `android-v0.10.0` tag produces `versionCode` 1000.

### Notes

- The backup format is documented by the code constant
  `tanoshi-backup`/version `1`; unknown formats or versions are rejected with a
  clear message. Restoring is additive and idempotent: existing library titles
  are skipped and history rows are upserted.
- A backup can contain manga whose source is not installed: the restore
  imports everything else, reports the missing sources and offers to install
  them, then retries.

## 0.9.2

### Fixed

- **Settings list cells now follow the web-UI theme.** On the Settings page the
  list cells (**Appearance**, **Chapters**, **Library**, **Categories**,
  **Reader**, **Sources**, **Users**, **Download Queue**, **Migrate**, etc.)
  kept the stock light `--list-group-background-color`, which resolves at
  `:root` and therefore ignored the themed background. With a dark theme (and
  especially the light appearance mode) their text could turn light while the
  cell stayed white, making the setting names unreadable. `theme_settings.rs`
  now computes **`--theme-cell-light`** (accent mixed 22% into white),
  **`--theme-cell-dark`** (accent mixed 30% into `#111111`) and
  contrast-adaptive **`--theme-cell-text-light`** / **`--theme-cell-text-dark`**
  (black/white chosen for the cell surface); the CSS themes
  `body.themed .settings-page .list.group` for both modes and makes the cell
  contents inherit the adaptive cell text. No other behaviour changed from
  0.9.1 (banners/icons/badge/background/titles/panels/adaptive body
  text/presets/custom builder/per-device storage are unchanged).
- **Web UI and `libtanoshi.so` rebuilt** with the cell fix; the vendored dist
  in `android/native/web-dist/` and the embedded server binary were rebuilt.
- **Version 0.9.2:** `versionCode` 902 in the Gradle default; the
  `android-v0.9.2` tag produces `versionCode` 902. The APK filename scheme is
  unchanged (`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`).

### Notes

- Settings cells use their own tinted surface in light/dark mode; the theme
  presets, custom Primary/Secondary/Accent builder and per-device
  `localStorage` (`settings:theme-palette`) behaviour are unchanged.

## 0.9.1

### Changed

- **Web-UI theme application reworked.** The 0.9.0 per-device theme feature and
  its storage are unchanged, but the palette now applies across the whole
  frontend in both light and dark mode:
  - **top and bottom banners** use the **primary** colour (banner text/buttons
    adapt to its luminance; the top-bar title and active bottom-bar item use a
    readable accent variant);
  - **all icons** (SVG, `currentColor`) use the **secondary** colour;
  - the library-cover **unread-chapter badge** uses the **secondary** colour
    with contrast-adaptive badge text;
  - the **page background** is the **accent** colour mixed at **30%** opacity
    over the mode's base colour (`color-mix`: 30% accent over `#f4f4f4` in
    light mode, over `#0a0a0a` in dark; the derived background shades
    (`--background-color-100/200`) and the modal background are tinted too);
  - **headers and titles** use the **accent** colour, with readable variants
    for the top-bar title against the primary banner and for manga-cover
    titles against the dark thumbnail gradient;
  - **settings panels** (`.reader-settings` and the settings page content) use
    the **accent** colour as their panel background with adaptive panel text;
  - **all text is contrast-adaptive per mode**: `theme_settings.rs` computes
    separate light/dark text colours from the tinted background
    (`--theme-text-light` / `--theme-text-dark`), readable heading colours per
    mode, and banner/panel/badge foregrounds, applied via a `body.themed`
    class. **Reset to default** removes the class and all theme variables and
    restores the normal Auto/Light/Dark appearance.
- **Web UI rebuilt and `libtanoshi.so` rebuilt** with the reworked theme
  CSS/logic; the vendored dist is `android/native/web-dist/` (fresh hashed
  asset names). No server API, config or storage changes; themes remain
  per-device in `localStorage`.
- **Version 0.9.1:** `versionCode` 901 in the Gradle default; the
  `android-v0.9.1` tag produces `versionCode` 901. The APK filename scheme is
  unchanged (`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`).

### Notes

- Theme presets, the custom Primary/Secondary/Accent builder, the
  **Applied: …** status and per-device `localStorage`
  (`settings:theme-palette`) behaviour from 0.9.0 are unchanged; only how the
  chosen palette is rendered changed.
- The 0.8.0 features (Delete History, catalogue filter sheet, Layout
  Preference presets, Tap Area) remain included.

## 0.9.0

### Added

- **Per-device web-UI themes.** The embedded Tanoshi frontend gains
  **Settings → Appearance → Themes** with **12 preset palettes**
  (Monochromatic Blue, Complementary Orange, Triadic Yellow + Teal + Magenta,
  Analogous Green + Blue + Teal, High Contrast Black + Yellow + White, Google
  Material Blue, Airbnb Coral, Wise Mint, Duolingo Amber, Headspace Warmth,
  Slack Aubergine, Pinterest Cherry; each with primary/secondary/accent
  colours) and a **New theme** builder at the bottom that opens
  Primary/Secondary/Accent colour swatch grids (20 colours each) with
  **Apply** and **Reset to default** (which removes the palette and restores
  the stock Auto/Light/Dark appearance). The palette themes the top bar (with
  luminance-based readable text/buttons), accent borders and hover colours.
- **Per-device storage only.** The selected theme (preset or custom) is saved
  in the browser's **`localStorage`** under `settings:theme-palette` and
  re-applied at boot via CSS variables; it is never stored server-side or
  synced. Other devices keep their own theme, and a new device/browser
  defaults to the stock Auto appearance.
- New web-UI patch set `android/native/patches/0004-web-ui-themes.patch` plus
  the new source file `android/native/patches/theme_settings.rs`
  (`build_tanoshi.sh` copies it into `crates/tanoshi-web/src/common/`).

### Changed

- **Web UI rebuilt and embedded.** The wasm frontend was rebuilt with all
  existing patches (0.8.0 Delete History/filters/layout presets/tap area,
  infinite scroll, app-only extension management) plus the theme support; the
  new dist is vendored in `android/native/web-dist/` and the checked-in
  `libtanoshi.so` was rebuilt with it.
- **Repository renamed to `spainclaro-debug/Yakushi`** (the old
  `tanoshi-termux` URL redirects). The app's in-app updater already targets the
  new repository; GitHub links in these docs use `tanoshi-mobile`. The Termux
  package artifact/tarball name (`tanoshi-termux-aarch64.tar.gz`) is unchanged
  and is **not** a repository URL.
- **Version 0.9.0:** `versionCode` 900 in the Gradle default; the
  `android-v0.9.0` tag produces `versionCode` 900. The APK filename scheme is
  unchanged (`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`).

### Notes

- The 0.9.0 web-UI theme is separate from the Android app's Material 3 themes
  (Dark/Light/Nord/Catppuccin), which continue to affect only the app UI.
- 0.8.0 features (Delete History, catalogue filter sheet, Layout Preference
  presets, Tap Area) are included; see the 0.8.0 section below.

## 0.8.0

### Added

- **Delete History button.** The History page has a **Delete History** button in
  the top right. It asks for an inline **Delete**/**Cancel** confirmation and
  then clears every read-history entry for the signed-in account. Backed by a
  new `deleteAllHistory` GraphQL mutation in the bundled server.
- **Layout Preference presets.** Settings → **Layout Preference** stores
  reader-option presets (Reading mode, Padding, Display mode, Direction,
  Background, Fit) **on the device**. It ships the **default** preset (Paged,
  Padding disabled, Single, Left to Right, White, Fit All) and an **Add New
  Preset** action that snapshots the current reader options. The selected
  preset is remembered per device, so a new device starts at Default even
  against the same server.
- **Tap Area reader option.** Settings → Reader → **Tap Area** chooses
  "bottom right: NEXT, bottom left: PREV" or "bottom left: NEXT, bottom right:
  PREV"; the reader's tap zones follow it.

### Fixed

- **Catalogue filter sheet.** The filter sheet no longer reuses another
  source's cached filters, starts with genre/tag groups **expanded** so all
  choices are visible immediately (Suwayomi/Mihon style), and shows an explicit
  message for sources that expose no filters.

### Changed

- **Web UI rebuilt from source** with the new
  `patches/tanoshi-web-ui.patch` + `patches/tanoshi-web-ui-settings.patch`
  (applied by both the Termux and Android build scripts) on top of the
  infinite-scroll and app-only extension-management patches. The rebuilt dist
  is vendored in `android/native/web-dist/` and embedded in `libtanoshi.so`;
  the Termux package binary in `tanoshi-termux-aarch64.tar.gz` was rebuilt with
  the same changes.
- **Version 0.8.0:** local builds default to `versionCode` 800; the
  `android-v0.8.0` tag produces `versionCode` 800. The APK filename scheme is
  unchanged (`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`).

## 0.7.1

### Fixed

- **Readable action button in the Add-extension catalog rows.** In
  Sources → Add extension, each catalog row now stacks its badges
  vertically — the repository label on top, then **Ported to Rust**, then
  **Disabled**/**Update available** — instead of sharing a horizontal row
  with the action button. The **Install/Update/Reinstall** button (and the
  **Installed** label) now has a minimum width and single-line text, so it is
  no longer squeezed or unreadable on narrow dialogs. No behaviour or API
  change; installed-extension cards are unchanged.
- **Version 0.7.1:** `versionCode` 701; the `android-v0.7.1` tag produces
  `versionCode` 701. The APK filename scheme is unchanged
  (`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`).

## 0.7.0

### Changed

- **Extension management is app-only; the embedded web UI is backend-only.**
  The Tanoshi web UI was rebuilt from source and now hides the extension
  controls that never worked on Android:
  - Settings → Sources no longer renders the **Available** sources list
    (only **Installed** is shown),
  - the source list no longer has **Install/Update** buttons,
  - the per-source settings page no longer has an **Uninstall** button.
  Installed sources and their per-source preferences still work. Adding,
  updating, reinstalling, disabling and removing extensions is done only in
  the app's **Sources** tab. Implemented with a new
  `android/native/patches/0003-web-ui-hide-source-management.patch` applied on
  top of `patches/tanoshi-infinite-scroll.patch`; the rebuilt wasm dist is
  vendored in `android/native/web-dist/` and embedded in `libtanoshi.so`.
  The infinite-scroll catalogue is preserved.
- **Built-in extension repository moved to `tanoshi-extension`.** The app's
  built-in repository now points at
  `https://raw.githubusercontent.com/spainclaro-debug/tanoshi-extension/main/android/extensions/index.json`
  (label `tanoshi-extension (default)`) instead of this repository's catalog.
  The dedicated public repository
  [spainclaro-debug/tanoshi-extension](https://github.com/spainclaro-debug/tanoshi-extension)
  hosts the nine prebuilt `aarch64-linux-android` plugins
  (`android/extensions/plugins/lib<name>.so`), the catalog
  (`android/extensions/index.json` plus a root `tanoshi-extensions.json`), the
  ported extension sources (`sources/`), the patches and
  `tools/build_extension.sh`; its README notes it is Android/Tanoshi-Mobile-
  Server-only. It can also be added manually in **Sources → Add repository**
  with the GitHub link `https://github.com/spainclaro-debug/tanoshi-extension`
  or the raw index URL. This repository keeps its
  `android/extensions/index.json` and exported sources/tooling for
  back-compatibility and tooling; **`tanoshi-extension` is the canonical
  catalog**.
- **Version 0.7.0:** `versionCode` 7 in the Gradle default; the
  `android-v0.7.0` tag produces `versionCode` 700. The APK filename scheme is
  unchanged (`tanoshi-mobile-server-release-<yyyy-MM-dd>.apk`).

### Notes

- Extension compatibility is unchanged: plugins must be built for the bundled
  Tanoshi/`tanoshi-lib` ABI (`0.38.0`) and `arm64-v8a`; the app still cannot
  compile extensions on-device (use the `tanoshi-extension` sources +
  `tools/build_extension.sh` on Termux or a desktop with the Android NDK).
- The web UI keeps its own theme and infinite-scroll behaviour; only the
  extension-management controls were removed.

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
