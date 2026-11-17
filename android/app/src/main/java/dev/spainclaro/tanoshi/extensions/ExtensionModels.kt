package dev.spainclaro.tanoshi.extensions

import java.io.File

/**
 * One extension listed in the online extension catalog
 * (`android/extensions/index.json` of this repository).
 *
 * Extensions are native `aarch64-linux-android` shared objects built for the
 * bundled Tanoshi server version; the catalog only hosts prebuilt `.so` files.
 * Tanoshi loads them from `nativeLibraryDir` (bundled, `lib<name>.so`) and from
 * the app's `filesDir/tanoshi/plugins` (user installed, `<name>.so`), so
 * installing one is a download plus a server restart.
 *
 * @property name bare extension name, e.g. `mangadex`.
 * @property displayName human readable name shown in the UI.
 * @property description optional short description.
 * @property url HTTPS download URL of the prebuilt `.so`.
 * @property sha256 optional lowercase hex SHA-256 of the file; `null` when the
 *   catalog does not publish a checksum.
 * @property sizeBytes expected size of the `.so` file in bytes.
 * @property ported whether the extension was ported to Rust in this repository
 *   (as opposed to an upstream WASM/plugin binary).
 */
data class CatalogExtension(
    val name: String,
    val displayName: String,
    val description: String?,
    val url: String,
    val sha256: String?,
    val sizeBytes: Long,
    val ported: Boolean,
)

/**
 * One extension that is present on the device right now.
 *
 * [bundled] and [userInstalled] can both be true for the same [name]; in that
 * case [file] points at the user copy, because the server loads that one first
 * (user plugins take precedence over the bundled copy).
 *
 * @property file the `.so` file that is actually used.
 * @property catalog catalog entry for this name, if the catalog lists it.
 * @property updateAvailable true when [file]'s SHA-256 differs from the
 *   catalog's checksum, i.e. the catalog has a newer/different build.
 */
data class InstalledExtension(
    val name: String,
    val displayName: String,
    val file: File,
    val bundled: Boolean,
    val userInstalled: Boolean,
    val catalog: CatalogExtension?,
    val updateAvailable: Boolean,
)

/**
 * Failure with a message that can be shown to the user as-is.
 *
 * Used for the [Result] failures returned by
 * [ExtensionManager.fetchCatalog], [ExtensionManager.install] and
 * [ExtensionManager.remove].
 */
class ExtensionException(message: String) : Exception(message)
