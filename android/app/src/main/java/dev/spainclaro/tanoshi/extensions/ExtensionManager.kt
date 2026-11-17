package dev.spainclaro.tanoshi.extensions

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dev.spainclaro.tanoshi.server.AppPaths
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Extension (plugin) catalog and installer for the bundled Tanoshi server.
 *
 * Two sources of extensions exist on the device:
 *  - **bundled**: `lib<name>.so` files in [Context.getApplicationInfo]'s
 *    `nativeLibraryDir` (shipped in the APK, read-only);
 *  - **user installed**: `<name>.so` files in `filesDir/tanoshi/plugins`,
 *    which the patched server loads with an in-memory loader. A user copy
 *    takes precedence over a bundled copy with the same name.
 *
 * The catalog (`android/extensions/index.json` of this repository) lists
 * prebuilt `aarch64-linux-android` `.so` files for the exact Tanoshi version
 * the app bundles. Installing an extension is therefore just a verified HTTPS
 * download into the plugins directory; the UI restarts the server afterwards.
 *
 * No third-party HTTP library is used; the work mirrors the in-app updater.
 */
class ExtensionManager(context: Context) {

    private val appContext: Context = context.applicationContext

    // -----------------------------------------------------------------------
    // Catalog
    // -----------------------------------------------------------------------

    /**
     * Downloads and parses the extension catalog.
     *
     * Malformed catalog entries are skipped; network, HTTP and parse problems
     * are returned as [ExtensionException] failures with user readable
     * messages. Runs on [Dispatchers.IO].
     */
    suspend fun fetchCatalog(): Result<List<CatalogExtension>> = withContext(Dispatchers.IO) {
        try {
            Result.success(parseCatalog(requestCatalog()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExtensionException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(
                ExtensionException("$NETWORK_FAILURE_MESSAGE (${e.describe()})"),
            )
        } catch (t: Throwable) {
            Result.failure(ExtensionException("Could not load the extension catalog: ${t.describe()}"))
        }
    }

    private fun requestCatalog(): String {
        val connection = openUrl(CATALOG_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = NETWORK_TIMEOUT_MS
            connection.readTimeout = NETWORK_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val statusCode = connection.responseCode
            val bodyStream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = bodyStream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (statusCode !in 200..299) {
                throw ExtensionException("The extension catalog is unavailable (HTTP $statusCode)")
            }
            return body
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun parseCatalog(body: String): List<CatalogExtension> {
        val catalog = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw ExtensionException("The extension catalog could not be parsed (${e.describe()})")
        }
        val jsonExtensions = catalog.optJSONArray("extensions")
            ?: throw ExtensionException("The extension catalog is malformed: it has no 'extensions' list")
        val extensions = ArrayList<CatalogExtension>(jsonExtensions.length())
        for (index in 0 until jsonExtensions.length()) {
            val jsonExtension = jsonExtensions.optJSONObject(index) ?: continue
            // A single broken entry must not hide the rest of the catalog.
            val extension = runCatching { parseCatalogEntry(jsonExtension) }.getOrNull() ?: continue
            extensions.add(extension)
        }
        return extensions.sortedWith(
            compareBy<CatalogExtension> { it.displayName.lowercase(Locale.US) }.thenBy { it.name },
        )
    }

    /** Returns `null` for entries that violate the catalog contract. */
    private fun parseCatalogEntry(json: JSONObject): CatalogExtension? {
        val name = json.stringOrNull("name")?.trim().orEmpty()
        if (!NAME_PATTERN.matches(name)) return null
        val url = json.stringOrNull("url")?.trim().orEmpty()
        if (!isAllowedUrl(url)) return null
        val sizeBytes = json.optLong("size", 0L)
        if (sizeBytes <= 0L) return null
        val rawSha256 = json.stringOrNull("sha256")?.trim()
        val sha256 = when {
            rawSha256 == null || rawSha256.isEmpty() -> null
            SHA256_PATTERN.matches(rawSha256) -> rawSha256.lowercase(Locale.US)
            else -> return null
        }
        val displayName = json.stringOrNull("displayName")?.trim()?.takeIf { it.isNotEmpty() }
            ?: prettifyName(name)
        return CatalogExtension(
            name = name,
            displayName = displayName,
            description = json.stringOrNull("description")?.trim()?.takeIf { it.isNotEmpty() },
            url = url,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            ported = json.optBoolean("ported", false),
        )
    }

    // -----------------------------------------------------------------------
    // Installed extensions
    // -----------------------------------------------------------------------

    /**
     * Lists the extensions present on the device and merges them with [catalog].
     *
     * **Blocking**: it scans two directories and hashes every plugin file to
     * detect updates. Call it from a background thread, or use
     * [listInstalledAsync] which switches to [Dispatchers.IO] for you.
     */
    fun listInstalled(catalog: List<CatalogExtension> = emptyList()): List<InstalledExtension> {
        val catalogByName = catalog.associateBy { it.name }
        val bundledPlugins = collectPlugins(AppPaths.nativeLibraryDir(appContext))
        val userPlugins = collectPlugins(AppPaths.plugins(appContext))

        val installed = LinkedHashMap<String, InstalledExtension>()
        for ((name, file) in bundledPlugins) {
            installed[name] = buildInstalledExtension(
                name = name,
                file = file,
                bundled = true,
                userInstalled = false,
                catalogByName = catalogByName,
            )
        }
        for ((name, file) in userPlugins) {
            // A user copy takes precedence over the bundled copy, but the
            // extension still counts as bundled (it can fall back to it).
            val previous = installed[name]
            installed[name] = buildInstalledExtension(
                name = name,
                file = file,
                bundled = previous?.bundled ?: false,
                userInstalled = true,
                catalogByName = catalogByName,
            )
        }
        return installed.values.sortedWith(
            compareBy<InstalledExtension> { it.displayName.lowercase(Locale.US) }.thenBy { it.name },
        )
    }

    /**
     * Coroutine-friendly variant of the blocking [listInstalled]: directory
     * scanning and hashing happen on [Dispatchers.IO].
     */
    suspend fun listInstalledAsync(
        catalog: List<CatalogExtension> = emptyList(),
    ): List<InstalledExtension> = withContext(Dispatchers.IO) {
        listInstalled(catalog)
    }

    private fun collectPlugins(directory: File): Map<String, File> {
        val plugins = LinkedHashMap<String, File>()
        val files = runCatching { directory.listFiles() }.getOrNull() ?: return plugins
        for (file in files) {
            if (!file.isFile) continue
            val fileName = file.name
            if (!fileName.endsWith(SO_SUFFIX, ignoreCase = true)) continue
            if (file.length() <= MIN_PLUGIN_BYTES) continue
            val name = normalizePluginName(fileName) ?: continue
            if (isIgnoredPluginFile(fileName)) continue
            plugins.putIfAbsent(name, file)
        }
        return plugins
    }

    /**
     * Server binary / support libraries that live next to the bundled plugins
     * but are not extensions. User plugins are additionally filtered by the
     * server's own `.tanoshi-*` staging naming, which is hidden (dot) anyway.
     */
    private fun isIgnoredPluginFile(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.US)
        if (lower.startsWith(".")) return true
        if (lower in SUPPORT_LIBRARY_NAMES) return true
        return SUPPORT_LIBRARY_PREFIXES.any { lower.startsWith(it) }
    }

    private fun normalizePluginName(fileName: String): String? {
        var name = fileName.dropLast(SO_SUFFIX.length)
        if (name.startsWith(LIB_PREFIX)) name = name.removePrefix(LIB_PREFIX)
        name = name.trim()
        return name.ifEmpty { null }
    }

    private fun buildInstalledExtension(
        name: String,
        file: File,
        bundled: Boolean,
        userInstalled: Boolean,
        catalogByName: Map<String, CatalogExtension>,
    ): InstalledExtension {
        val catalog = catalogByName[name]
        val expectedSha256 = catalog?.sha256
        val updateAvailable = if (expectedSha256 == null) {
            false
        } else {
            val actualSha256 = sha256Of(file)
            actualSha256 != null && !actualSha256.equals(expectedSha256, ignoreCase = true)
        }
        return InstalledExtension(
            name = name,
            displayName = catalog?.displayName ?: prettifyName(name),
            file = file,
            bundled = bundled,
            userInstalled = userInstalled,
            catalog = catalog,
            updateAvailable = updateAvailable,
        )
    }

    // -----------------------------------------------------------------------
    // Install
    // -----------------------------------------------------------------------

    /**
     * Downloads and installs [entry] as `filesDir/tanoshi/plugins/<name>.so`.
     *
     * The download is streamed to a `.part` file next to the target (progress
     * callback throttled to ~100 ms, invoked on [Dispatchers.IO]), the size and
     * the SHA-256 from the catalog are verified when available, and only then
     * is the file atomically renamed into place. Any previous user copy with
     * the same name is replaced. The server is not touched; the caller has to
     * restart it for the extension to be loaded.
     */
    suspend fun install(
        entry: CatalogExtension,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var partFile: File? = null
        try {
            validateEntryForInstall(entry)
            val pluginsDir = AppPaths.plugins(appContext)
            if (!pluginsDir.isDirectory && !pluginsDir.mkdirs()) {
                throw ExtensionException("Could not create the extensions directory")
            }
            val targetFile = File(pluginsDir, "${entry.name}$SO_SUFFIX")
            val part = File(pluginsDir, "${entry.name}$SO_SUFFIX$PART_SUFFIX")
            partFile = part
            // Clean up leftovers of an interrupted previous attempt.
            runCatching { part.delete() }
            runCatching { File(pluginsDir, "${entry.name}$SO_SUFFIX$TEMP_SUFFIX").delete() }

            downloadToFile(entry, part, onProgress)

            if (targetFile.exists() && !targetFile.delete()) {
                throw ExtensionException("Could not replace the existing ${entry.name} extension file")
            }
            if (!part.renameTo(targetFile)) {
                throw ExtensionException("Could not store the ${entry.name} extension file")
            }
            partFile = null
            Log.i(TAG, "Installed extension ${entry.name} (${targetFile.length()} bytes)")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExtensionException) {
            partFile?.let { runCatching { it.delete() } }
            Result.failure(e)
        } catch (e: IOException) {
            partFile?.let { runCatching { it.delete() } }
            Result.failure(ExtensionException("Could not download ${entry.name}: ${e.describe()}"))
        } catch (t: Throwable) {
            partFile?.let { runCatching { it.delete() } }
            Result.failure(ExtensionException("Could not install ${entry.name}: ${t.describe()}"))
        }
    }

    private fun validateEntryForInstall(entry: CatalogExtension) {
        if (!NAME_PATTERN.matches(entry.name)) {
            throw ExtensionException("Invalid extension name \"${entry.name}\"")
        }
        if (!isAllowedUrl(entry.url)) {
            throw ExtensionException("The ${entry.name} extension has an invalid download URL")
        }
        if (entry.sizeBytes <= MIN_PLUGIN_BYTES) {
            throw ExtensionException(
                "The ${entry.name} extension is unexpectedly small (${entry.sizeBytes} bytes)",
            )
        }
        entry.sha256?.let { expectedSha256 ->
            if (!SHA256_PATTERN.matches(expectedSha256)) {
                throw ExtensionException("The ${entry.name} extension has an invalid checksum")
            }
        }
    }

    private fun downloadToFile(
        entry: CatalogExtension,
        partFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val connection = openUrl(entry.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = NETWORK_TIMEOUT_MS
            connection.readTimeout = NETWORK_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/octet-stream")
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw ExtensionException("Could not download ${entry.name} (HTTP $statusCode)")
            }
            val contentLength = connection.contentLengthLong
            val expectedTotal = when {
                contentLength > 0L -> contentLength
                entry.sizeBytes > 0L -> entry.sizeBytes
                else -> 0L
            }
            if (expectedTotal > 0L && expectedTotal <= MIN_PLUGIN_BYTES) {
                throw ExtensionException(
                    "The ${entry.name} download is unexpectedly small ($expectedTotal bytes)",
                )
            }
            val digest = entry.sha256?.let { MessageDigest.getInstance("SHA-256") }
            var downloaded = 0L
            var lastProgressAt = 0L
            onProgress(0L, expectedTotal)
            connection.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest?.update(buffer, 0, read)
                        downloaded += read.toLong()
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastProgressAt >= PROGRESS_THROTTLE_MS) {
                            lastProgressAt = now
                            onProgress(downloaded, expectedTotal)
                        }
                    }
                    output.flush()
                    runCatching { output.fd.sync() }
                }
            }
            onProgress(downloaded, expectedTotal)
            verifyDownload(entry, downloaded, contentLength, digest)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun verifyDownload(
        entry: CatalogExtension,
        downloaded: Long,
        contentLength: Long,
        digest: MessageDigest?,
    ) {
        if (downloaded <= MIN_PLUGIN_BYTES) {
            throw ExtensionException(
                "The downloaded ${entry.name} extension is unexpectedly small ($downloaded bytes)",
            )
        }
        if (contentLength > 0L && downloaded != contentLength) {
            throw ExtensionException(
                "The ${entry.name} download was incomplete ($downloaded of $contentLength bytes); try again",
            )
        }
        if (entry.sizeBytes > 0L && downloaded != entry.sizeBytes) {
            throw ExtensionException(
                "The ${entry.name} download is $downloaded bytes but the catalog lists " +
                    "${entry.sizeBytes} bytes; try again",
            )
        }
        val expectedSha256 = entry.sha256 ?: return
        val computedSha256 = digest?.digest()?.toHexString()
            ?: throw ExtensionException("Could not verify the checksum of ${entry.name}")
        if (!computedSha256.equals(expectedSha256, ignoreCase = true)) {
            throw ExtensionException(
                "The downloaded ${entry.name} extension failed its checksum check; try again",
            )
        }
    }

    // -----------------------------------------------------------------------
    // Remove
    // -----------------------------------------------------------------------

    /**
     * Removes a user-installed extension (never a bundled one).
     *
     * [name] may be given with or without the `lib` prefix and the `.so`
     * suffix. Returns a failure when no user copy exists or the file cannot be
     * deleted; the bundled copy (if any) stays untouched.
     */
    fun remove(name: String): Result<Unit> {
        return try {
            val normalizedName = normalizeRemovalName(name)
                ?: return Result.failure(
                    ExtensionException("Invalid extension name \"$name\""),
                )
            val pluginsDir = AppPaths.plugins(appContext)
            val userFile = listOf(
                File(pluginsDir, "$normalizedName$SO_SUFFIX"),
                File(pluginsDir, "$LIB_PREFIX$normalizedName$SO_SUFFIX"),
            ).firstOrNull { it.isFile }
                ?: return Result.failure(
                    ExtensionException(
                        "The extension \"$normalizedName\" was not installed by the user",
                    ),
                )
            if (!userFile.delete()) {
                throw ExtensionException("Could not remove the extension file ${userFile.name}")
            }
            Log.i(TAG, "Removed user-installed extension ${userFile.name}")
            Result.success(Unit)
        } catch (e: ExtensionException) {
            Result.failure(e)
        } catch (t: Throwable) {
            Result.failure(ExtensionException("Could not remove the extension: ${t.describe()}"))
        }
    }

    private fun normalizeRemovalName(raw: String): String? {
        var name = raw.trim()
        if (name.endsWith(SO_SUFFIX, ignoreCase = true)) {
            name = name.dropLast(SO_SUFFIX.length)
        }
        if (name.startsWith(LIB_PREFIX)) {
            name = name.removePrefix(LIB_PREFIX)
        }
        name = name.trim()
        return name.ifEmpty { null }?.takeIf { NAME_PATTERN.matches(it) }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun isAllowedUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank() || !rawUrl.startsWith("https://", ignoreCase = true)) return false
        return runCatching {
            "https".equals(URI.create(rawUrl).scheme, ignoreCase = true)
        }.getOrDefault(false)
    }

    private fun openUrl(rawUrl: String): URL = URI.create(rawUrl).toURL()

    /**
     * `weebcentral` -> "Weebcentral", `manhwa18cc` -> "Manhwa18cc",
     * `madaradex` -> "Madaradex"; used when the catalog has no display name.
     */
    private fun prettifyName(name: String): String =
        name.split('-', '_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { part ->
                if (part.isEmpty()) part else part[0].uppercaseChar() + part.substring(1)
            }
            .ifEmpty { name }

    private fun sha256Of(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().toHexString()
    }.onFailure {
        Log.w(TAG, "Could not compute the SHA-256 of ${file.absolutePath}", it)
    }.getOrNull()

    private fun ByteArray.toHexString(): String {
        val builder = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[value ushr 4]).append(HEX_DIGITS[value and 0x0F])
        }
        return builder.toString()
    }

    private fun JSONObject.stringOrNull(name: String): String? = opt(name) as? String

    private fun Throwable.describe(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    companion object {
        /** Catalog of prebuilt extensions hosted in this repository. */
        const val CATALOG_URL =
            "https://raw.githubusercontent.com/spainclaro-debug/tanoshi-termux/main/android/extensions/index.json"

        private const val TAG = "ExtensionManager"
        private const val USER_AGENT = "Tanoshi-Android"
        private const val NETWORK_TIMEOUT_MS = 15_000
        private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_THROTTLE_MS = 100L

        /** Plugins smaller than this are not extensions (and are rejected). */
        private const val MIN_PLUGIN_BYTES = 256L * 1024L

        private const val SO_SUFFIX = ".so"
        private const val LIB_PREFIX = "lib"
        private const val PART_SUFFIX = ".part"
        private const val TEMP_SUFFIX = ".tmp"
        private const val HEX_DIGITS = "0123456789abcdef"

        private const val NETWORK_FAILURE_MESSAGE =
            "Could not reach the extension catalog; check your internet connection and try again"

        private val NAME_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")
        private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")

        /** Native libraries in `nativeLibraryDir` that are not extensions. */
        private val SUPPORT_LIBRARY_NAMES = setOf("libtanoshi.so", "libarchive.so")
        private val SUPPORT_LIBRARY_PREFIXES = listOf("libandroidx", "libdatastore")
    }
}
