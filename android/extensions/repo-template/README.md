# Extension repository template

This directory shows the layout the Android app expects when you add an
**extension repository** in **Sources → Add repository**. You can host it in any
public GitHub repository (or any HTTPS host).

```
<your-repo>/
└── android/
    └── extensions/
        ├── index.json          # catalog the app fetches and parses
        └── plugins/
            ├── libmysource.so  # built for aarch64-linux-android
            └── ...
```

`index.json` format:

```json
{
  "version": 1,
  "updated": "2026-11-17",
  "note": "Prebuilt aarch64-linux-android Tanoshi extensions.",
  "extensions": [
    {
      "name": "mysource",
      "displayName": "My Source",
      "description": "What this source provides.",
      "url": "https://raw.githubusercontent.com/<owner>/<repo>/main/android/extensions/plugins/libmysource.so",
      "sha256": "<64 lowercase hex characters>",
      "size": 1234567,
      "ported": true
    }
  ]
}
```

- `name` must match `^[a-z0-9][a-z0-9_-]{0,63}$`; it is also the installed
  file name (`<name>.so` in `files/tanoshi/plugins/`).
- `url` must be **https** and point at the prebuilt `.so` (byte-for-byte what
  you built; the app downloads and verifies it).
- `sha256` is optional but strongly recommended (the app verifies when present).
- `size` is the exact byte size; the app checks it.
- `ported` is a display badge only.

Build the `.so` from the exported sources with:

```bash
# in this repository (Termux, or desktop with ANDROID_NDK_HOME set)
bash android/native/build_extension.sh --crate android/extensions/sources/ported/mysource
```

The script prints the SHA-256/size and a ready-to-paste catalog entry. Put the
built file at `android/extensions/plugins/lib<name>.so`, update `index.json`,
commit/push, then in the app add the repository:

- **GitHub repo link** — `https://github.com/<owner>/<repo>` (the app looks for
  `android/extensions/index.json` or `tanoshi-extensions.json` on `main` or
  `master`), or
- **direct catalog URL** — e.g. the raw `.../android/extensions/index.json` URL.

Installed extensions are loaded by the server after a restart (**Sources**
screen shows the restart prompt). Only advertise plugins built for the same
Tanoshi version (`tanoshi-lib` 0.38.0 for app 0.6.x) and ABI (`arm64-v8a`).
