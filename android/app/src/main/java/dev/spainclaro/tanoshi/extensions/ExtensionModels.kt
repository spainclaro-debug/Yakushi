package dev.spainclaro.tanoshi.extensions

import java.io.File

/**
 * One extension listed in an extension catalog (a `tanoshi-extensions.json` /
 * `android/extensions/index.json`-shaped file hosted on any repository the
 * user added, or the built-in one).
 *
 * Extensions are native `aarch64-linux-android` shared objects built for the
 * bundled Tanoshi server version; a catalog only hosts prebuilt `.so` files.
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
 * @property ported whether the extension was ported to Rust (as opposed to an
 *   upstream WASM/plugin binary).
 * @property repoUrl catalog index URL this entry came from.
 * @property repoLabel human label of that repository.
 */
data class CatalogExtension(
    val name: String,
    val displayName: String,
    val description: String?,
    val url: String,
    val sha256: String?,
    val sizeBytes: Long,
    val ported: Boolean,
    val repoUrl: String = "",
    val repoLabel: String = "",
)

/**
 * An extension catalog repository.
 *
 * @property catalogUrl normalized HTTPS URL of the catalog JSON document.
 * @property label human label: `owner/repo` for GitHub-derived repositories,
 *   `host/file.json` for direct catalog URLs, and
 *   [ExtensionManager.DEFAULT_REPO_LABEL] for the built-in repository.
 * @property isDefault true for the built-in repository (never removable).
 */
data class ExtensionRepository(
    val catalogUrl: String,
    val label: String,
    val isDefault: Boolean,
)

/**
 * One repository that could not be loaded by [ExtensionManager.fetchCatalogs].
 *
 * @property message user readable reason for the failure of that repository.
 */
data class RepositoryFailure(
    val repository: ExtensionRepository,
    val message: String,
)

/**
 * Result of [ExtensionManager.fetchCatalogs]: entries merged from every
 * reachable repository (deduplicated by name) plus the per-repository failures.
 */
data class CatalogFetchResult(
    val entries: List<CatalogExtension>,
    val failures: List<RepositoryFailure>,
)

/**
 * One extension that is present on the device right now.
 *
 * [bundled] and [userInstalled] can both be true for the same [name]; in that
 * case [file] points at the user copy, because the server loads that one first
 * (user plugins take precedence over the bundled copy).
 *
 * @property file the `.so` file that is actually used.
 * @property catalog catalog entry for this name, if any loaded catalog lists it.
 * @property updateAvailable true when [file]'s SHA-256 differs from the
 *   catalog's checksum, i.e. the catalog has a newer/different build.
 * @property disabled true when the server's `.tanoshi-disabled` marker lists
 *   this extension, i.e. it will NOT be loaded on the next server start.
 */
data class InstalledExtension(
    val name: String,
    val displayName: String,
    val file: File,
    val bundled: Boolean,
    val userInstalled: Boolean,
    val catalog: CatalogExtension?,
    val updateAvailable: Boolean,
    val disabled: Boolean = false,
)

/**
 * Failure with a message that can be shown to the user as-is.
 *
 * Used for the [Result] failures returned by [ExtensionManager] operations.
 */
class ExtensionException(message: String) : Exception(message)
