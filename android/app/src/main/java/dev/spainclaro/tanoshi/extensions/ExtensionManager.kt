package dev.spainclaro.tanoshi.extensions

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dev.spainclaro.tanoshi.server.AppPaths
import dev.spainclaro.tanoshi.settings.AppSettings
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Extension (plugin) catalog and installer for the bundled Tanoshi server.
 *
 * Two sources of extensions exist on the device:
 *  - **bundled**: `lib<name>.so` files in [Context.getApplicationInfo]'s
 *    `nativeLibraryDir` (shipped in the APK, read-only);
 *  - **user installed**: `<name>.so` files in `filesDir/tanoshi/plugins`, which
 *    the patched server loads with an in-memory loader. A user copy takes
 *    precedence over a bundled copy with the same name.
 *
 * Extensions listed in the server's `.tanoshi-disabled` marker file are not
 * loaded; the app can enable/disable an extension by maintaining that file.
 *
 * Catalogs come from the built-in repository plus up to
 * [MAX_CUSTOM_REPOSITORIES] user-added repositories (persisted in
 * [AppSettings.extensionRepositoriesJson] as catalog URLs). A repository can be
 * added as a direct HTTPS `*.json` catalog URL or as a GitHub repository link,
 * in which case the well-known raw catalog locations are probed.
 *
 * No third-party HTTP library is used; the work mirrors the in-app updater.
 */
class ExtensionManager(context: Context) {

    private val appContext: Context = context.applicationContext

    // -----------------------------------------------------------------------
    // Repositories
    // -----------------------------------------------------------------------

    /** The built-in repository (the catalog shipped in this repository). */
    private fun defaultRepository(): ExtensionRepository =
        ExtensionRepository(CATALOG_URL, DEFAULT_REPO_LABEL, isDefault = true)

    /**
     * The built-in repository first, then the user-added repositories in the
     * order they were added. Labels are derived from the stored catalog URLs.
     */
    fun repositories(): List<ExtensionRepository> {
        val repositories = ArrayList<ExtensionRepository>()
        repositories.add(defaultRepository())
        val seen = HashSet<String>()
        seen.add(CATALOG_URL)
        for (catalogUrl in storedRepositoryUrls()) {
            if (!seen.add(catalogUrl)) continue
            repositories.add(
                ExtensionRepository(
                    catalogUrl = catalogUrl,
                    label = repositoryLabel(catalogUrl),
                    isDefault = false,
                ),
            )
        }
        return repositories
    }

    /**
     * Adds a repository from user input.
     *
     * Accepts either a direct HTTPS `.json` catalog URL (validated by fetching
     * and parsing it) or a GitHub repository link such as
     * `https://github.com/owner/repo` (optionally with `.git` / a trailing
     * slash), for which the usual raw catalog locations are probed on `main`
     * and `master`. Duplicates return the already-known repository as a
     * success.
     *
     * **Blocking**: performs network requests. Call it from a background
     * thread (the UI does this) or use [addRepositoryAsync].
     */
    fun addRepository(input: String): Result<ExtensionRepository> {
        return try {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) {
                throw ExtensionException("Enter a repository link or a catalog .json URL")
            }
            if (!trimmed.startsWith("https://", ignoreCase = true)) {
                throw ExtensionException("Only https:// links are supported")
            }
            val githubMatch = GITHUB_REPO_PATTERN.matchEntire(trimmed)
            if (githubMatch == null) {
                if (!trimmed.endsWith(".json", ignoreCase = true)) {
                    throw ExtensionException(
                        "Enter a direct https://…json catalog URL or a " +
                            "https://github.com/owner/repo link",
                    )
                }
                addDirectRepository(trimmed)
            } else {
                addGitHubRepository(
                    owner = githubMatch.groupValues[1],
                    repo = githubMatch.groupValues[2].removeSuffix(".git"),
                )
            }
        } catch (e: ExtensionException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(ExtensionException("Could not reach the repository: ${e.describe()}"))
        } catch (t: Throwable) {
            Result.failure(ExtensionException("Could not add the repository: ${t.describe()}"))
        }
    }

    /**
     * Coroutine-friendly wrapper around the blocking [addRepository]: the
     * network requests run on [Dispatchers.IO].
     */
    suspend fun addRepositoryAsync(input: String): Result<ExtensionRepository> =
        withContext(Dispatchers.IO) {
            addRepository(input)
        }

    private fun addDirectRepository(catalogUrl: String): Result<ExtensionRepository> {
        val existing = repositories()
        existing.firstOrNull { it.catalogUrl == catalogUrl }?.let { return Result.success(it) }
        ensureRepositoryCapacity(existing)
        val repository = ExtensionRepository(catalogUrl, repositoryLabel(catalogUrl), isDefault = false)
        val entries = try {
            fetchAndParseRepository(repository)
        } catch (e: ExtensionException) {
            throw ExtensionException("Could not read the catalog at $catalogUrl: ${e.message}")
        } catch (e: IOException) {
            throw ExtensionException("Could not reach $catalogUrl (${e.describe()})")
        }
        if (entries.isEmpty()) {
            throw ExtensionException("The catalog at $catalogUrl does not list any extensions")
        }
        persistRepositoryUrls(
            existing.filterNot { it.isDefault }.map { it.catalogUrl } + repository.catalogUrl,
        )
        Log.i(TAG, "Added extension repository ${repository.label} (${repository.catalogUrl})")
        return Result.success(repository)
    }

    private fun addGitHubRepository(owner: String, repo: String): Result<ExtensionRepository> {
        val existing = repositories()
        val label = "$owner/$repo"
        // A repository added earlier from the same GitHub link resolves to the
        // same raw URL; the label check also catches a different branch choice.
        existing.firstOrNull {
            !it.isDefault && it.label.equals(label, ignoreCase = true)
        }?.let { return Result.success(it) }

        val candidates = githubCatalogCandidates(owner, repo)
        for (candidate in candidates) {
            existing.firstOrNull { it.catalogUrl == candidate }?.let { return Result.success(it) }
            val repository = ExtensionRepository(candidate, label, isDefault = false)
            val entries = runCatching { fetchAndParseRepository(repository) }.getOrNull()
            if (entries != null && entries.isNotEmpty()) {
                ensureRepositoryCapacity(existing)
                persistRepositoryUrls(
                    existing.filterNot { it.isDefault }.map { it.catalogUrl } + candidate,
                )
                Log.i(TAG, "Added extension repository $label (resolved to $candidate)")
                return Result.success(repository)
            }
        }
        throw ExtensionException(
            "Could not find a Tanoshi extension catalog for $label. Tried: " +
                candidates.joinToString(", "),
        )
    }

    private fun ensureRepositoryCapacity(existing: List<ExtensionRepository>) {
        val customCount = existing.count { !it.isDefault }
        if (customCount >= MAX_CUSTOM_REPOSITORIES) {
            throw ExtensionException(
                "You can add at most $MAX_CUSTOM_REPOSITORIES custom repositories",
            )
        }
    }

    /**
     * The raw catalog locations probed for a GitHub repository, in the order
     * requested: `{main,master}/android/extensions/index.json` first, then
     * `{main,master}/tanoshi-extensions.json`.
     */
    private fun githubCatalogCandidates(owner: String, repo: String): List<String> {
        val base = "https://$GITHUB_RAW_HOST/$owner/$repo"
        val candidates = ArrayList<String>(4)
        for (branch in GITHUB_BRANCHES) {
            candidates.add("$base/$branch/$DEFAULT_CATALOG_PATH")
        }
        for (branch in GITHUB_BRANCHES) {
            candidates.add("$base/$branch/$ALT_CATALOG_PATH")
        }
        return candidates
    }

    /**
     * Removes a user-added repository. The built-in repository is never
     * removable; [catalogUrl] is the value from [repositories].
     */
    fun removeRepository(catalogUrl: String): Result<Unit> {
        return try {
            val target = catalogUrl.trim()
            if (target.isEmpty()) {
                throw ExtensionException("No repository specified")
            }
            if (target == CATALOG_URL) {
                throw ExtensionException("The default repository cannot be removed")
            }
            val stored = storedRepositoryUrls()
            val remaining = stored.filterNot { it == target }
            if (remaining.size == stored.size) {
                throw ExtensionException("That repository is not in your list")
            }
            persistRepositoryUrls(remaining)
            Log.i(TAG, "Removed extension repository $target")
            Result.success(Unit)
        } catch (e: ExtensionException) {
            Result.failure(e)
        } catch (t: Throwable) {
            Result.failure(ExtensionException("Could not remove the repository: ${t.describe()}"))
        }
    }

    private fun repositoryLabel(catalogUrl: String): String {
        val uri = runCatching { URI.create(catalogUrl) }.getOrNull()
        val host = uri?.host
        val segments = uri?.path?.split('/')?.filter { it.isNotEmpty() }.orEmpty()
        if (host != null &&
            host.equals(GITHUB_RAW_HOST, ignoreCase = true) &&
            segments.size >= 2
        ) {
            return "${segments[0]}/${segments[1]}"
        }
        val fileName = segments.lastOrNull() ?: catalogUrl
        return if (host.isNullOrBlank()) fileName else "$host/$fileName"
    }

    /** Stored custom catalog URLs, in insertion order (malformed data ignored). */
    private fun storedRepositoryUrls(): List<String> {
        val raw = AppSettings.extensionRepositoriesJson
        return runCatching {
            val json = JSONArray(raw)
            val urls = ArrayList<String>(json.length())
            for (index in 0 until json.length()) {
                val url = json.optString(index).trim()
                if (url.isNotEmpty()) urls.add(url)
            }
            urls
        }.getOrElse {
            Log.w(TAG, "Could not read the stored extension repositories", it)
            emptyList()
        }
    }

    private fun persistRepositoryUrls(catalogUrls: List<String>) {
        val json = JSONArray()
        for (url in catalogUrls) json.put(url)
        AppSettings.extensionRepositoriesJson = json.toString()
    }

    // -----------------------------------------------------------------------
    // Catalog fetching
    // -----------------------------------------------------------------------

    /** Fetches the built-in catalog only (kept for backwards compatibility). */
    suspend fun fetchCatalog(): Result<List<CatalogExtension>> = fetchCatalog(defaultRepository())

    /**
     * Fetches and parses one repository's catalog. Every entry is tagged with
     * the repository's URL and label. Malformed entries are skipped; network,
     * HTTP and parse problems are returned as user readable failures.
     */
    suspend fun fetchCatalog(repository: ExtensionRepository): Result<List<CatalogExtension>> =
        withContext(Dispatchers.IO) {
            try {
                validateRepository(repository)
                Result.success(parseCatalog(requestCatalog(repository.catalogUrl), repository))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExtensionException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(
                    ExtensionException("Could not reach ${repository.label}: ${e.describe()}"),
                )
            } catch (t: Throwable) {
                Result.failure(
                    ExtensionException("Could not load ${repository.label}: ${t.describe()}"),
                )
            }
        }

    /**
     * Fetches the built-in repository first, then every user-added repository,
     * and merges the entries by normalized name (earlier repositories win).
     *
     * A failing repository does not abort the fetch: its failure is collected
     * in [CatalogFetchResult.failures]. Cancellation still propagates.
     */
    suspend fun fetchCatalogs(): CatalogFetchResult = withContext(Dispatchers.IO) {
        val entriesByName = LinkedHashMap<String, CatalogExtension>()
        val failures = ArrayList<RepositoryFailure>()
        for (repository in repositories()) {
            val result = fetchCatalog(repository)
            result.fold(
                onSuccess = { entries ->
                    for (entry in entries) {
                        entriesByName.putIfAbsent(normalizeCatalogName(entry.name), entry)
                    }
                },
                onFailure = { error ->
                    failures.add(
                        RepositoryFailure(
                            repository = repository,
                            message = error.message?.takeIf { it.isNotBlank() }
                                ?: "Could not load this repository",
                        ),
                    )
                },
            )
        }
        CatalogFetchResult(
            entries = entriesByName.values.sortedWith(CATALOG_ENTRY_ORDER),
            failures = failures,
        )
    }

    private fun validateRepository(repository: ExtensionRepository) {
        if (repository.catalogUrl.isBlank() || !isAllowedUrl(repository.catalogUrl)) {
            throw ExtensionException("Invalid repository URL \"${repository.catalogUrl}\"")
        }
    }

    private fun requestCatalog(catalogUrl: String): String {
        val connection = openUrl(catalogUrl).openConnection() as HttpURLConnection
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

    /** Blocking catalog fetch+parse, used by [addRepository] validation. */
    private fun fetchAndParseRepository(repository: ExtensionRepository): List<CatalogExtension> {
        validateRepository(repository)
        return parseCatalog(requestCatalog(repository.catalogUrl), repository)
    }

    private fun parseCatalog(body: String, repository: ExtensionRepository): List<CatalogExtension> {
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
            extensions.add(
                extension.copy(
                    repoUrl = repository.catalogUrl,
                    repoLabel = repository.label,
                ),
            )
        }
        return extensions.sortedWith(CATALOG_ENTRY_ORDER)
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

    private fun normalizeCatalogName(name: String): String = name.trim().lowercase(Locale.US)

    // -----------------------------------------------------------------------
    // Installed extensions
    // -----------------------------------------------------------------------

    /**
     * Lists the extensions present on the device and merges them with [catalog].
     *
     * Disabled extensions are included (with [InstalledExtension.disabled])
     * so the UI can offer to enable them again.
     *
     * **Blocking**: it scans two directories, reads the disabled marker and
     * hashes every plugin file to detect updates. Call it from a background
     * thread, or use [listInstalledAsync].
     */
    fun listInstalled(catalog: List<CatalogExtension> = emptyList()): List<InstalledExtension> {
        val catalogByName = catalog.associateBy { it.name }
        val disabledNames = disabledNames()
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
                disabled = disabledNames.matches(name),
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
                disabled = disabledNames.matches(name),
            )
        }
        return installed.values.sortedWith(INSTALLED_EXTENSION_ORDER)
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
        disabled: Boolean,
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
            disabled = disabled,
        )
    }

    // -----------------------------------------------------------------------
    // Enable / disable marker (`.tanoshi-disabled`)
    // -----------------------------------------------------------------------

    /**
     * The extension names currently disabled in the server's marker file,
     * normalized like the server stores them (`.so`/`lib` stripped, lowercase).
     */
    fun disabledNames(): Set<String> {
        val markerFile = disabledMarkerFile()
        val lines = runCatching {
            if (markerFile.isFile) markerFile.readLines() else emptyList()
        }.getOrElse {
            Log.w(TAG, "Could not read ${markerFile.absolutePath}", it)
            return emptySet()
        }
        val names = LinkedHashSet<String>()
        for (line in lines) {
            normalizeMarkerName(line)?.let { names.add(it) }
        }
        return names
    }

    /**
     * Enables or disables an installed extension by updating the server's
     * `.tanoshi-disabled` marker file (rewritten atomically). Takes effect on
     * the next server start; the UI offers a restart.
     */
    fun setExtensionEnabled(name: String, enabled: Boolean): Result<Unit> {
        return try {
            val normalized = normalizeExtensionName(name)
                ?: throw ExtensionException("Invalid extension name \"$name\"")
            val disabled = disabledNames().toMutableSet()
            val changed = if (enabled) disabled.remove(normalized) else disabled.add(normalized)
            if (changed) {
                persistDisabledNames(disabled)
                Log.i(
                    TAG,
                    "Extension \"$normalized\" ${if (enabled) "enabled" else "disabled"} " +
                        "(marker ${if (enabled) "cleared" else "updated"})",
                )
            }
            Result.success(Unit)
        } catch (e: ExtensionException) {
            Result.failure(e)
        } catch (t: Throwable) {
            Result.failure(
                ExtensionException(
                    "Could not ${if (enabled) "enable" else "disable"} $name: ${t.describe()}",
                ),
            )
        }
    }

    private fun disabledMarkerFile(): File = File(AppPaths.plugins(appContext), DISABLED_MARKER_FILE)

    /**
     * Rewrites the marker file: sorted names, one per line. An empty set removes
     * the file. The write goes to a temporary file that is atomically renamed
     * into place, so a crash can never leave a half-written marker.
     */
    private fun persistDisabledNames(names: Set<String>) {
        val pluginsDir = AppPaths.plugins(appContext)
        if (!pluginsDir.isDirectory && !pluginsDir.mkdirs()) {
            throw ExtensionException("Could not create the extensions directory")
        }
        val markerFile = File(pluginsDir, DISABLED_MARKER_FILE)
        if (names.isEmpty()) {
            if (markerFile.exists() && !markerFile.delete()) {
                throw ExtensionException("Could not remove the disabled extension marker")
            }
            return
        }
        val tempFile = File(pluginsDir, "$DISABLED_MARKER_FILE$TEMP_SUFFIX")
        runCatching { tempFile.delete() }
        try {
            FileOutputStream(tempFile).use { output ->
                for (name in names.sorted()) {
                    output.write(name.toByteArray(Charsets.UTF_8))
                    output.write('\n'.code)
                }
                output.flush()
                runCatching { output.fd.sync() }
            }
            moveAtomically(tempFile, markerFile)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    /** Clears the disabled marker for [name] when it is present (idempotent). */
    private fun clearDisabledName(name: String) {
        val normalized = normalizeMarkerName(name) ?: return
        val disabled = disabledNames().toMutableSet()
        if (!disabled.remove(normalized)) return
        persistDisabledNames(disabled)
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Normalizes a marker-file name: strips an optional `lib` prefix and `.so`
     * suffix, drops `#` comments, lowercases and trims. Returns null for blanks.
     */
    private fun normalizeMarkerName(raw: String): String? {
        var line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return null
        line = line.substringBefore('#').trim()
        if (line.isEmpty()) return null
        if (line.endsWith(SO_SUFFIX, ignoreCase = true)) {
            line = line.dropLast(SO_SUFFIX.length)
        }
        if (line.startsWith(LIB_PREFIX)) {
            line = line.removePrefix(LIB_PREFIX)
        }
        line = line.trim().lowercase(Locale.US)
        return line.ifEmpty { null }
    }

    /** Normalizes a user supplied extension name and validates it. */
    private fun normalizeExtensionName(raw: String): String? {
        var name = raw.trim()
        if (name.endsWith(SO_SUFFIX, ignoreCase = true)) {
            name = name.dropLast(SO_SUFFIX.length)
        }
        if (name.startsWith(LIB_PREFIX)) {
            name = name.removePrefix(LIB_PREFIX)
        }
        name = name.trim().lowercase(Locale.US)
        return name.ifEmpty { null }?.takeIf { NAME_PATTERN.matches(it) }
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
     * the same name is replaced and a disabled marker for the name is cleared,
     * so re-installing re-enables the extension. The server is not touched; the
     * caller has to restart it for the extension to be loaded.
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
            // Re-installing must bring a source that was disabled (via the
            // server's uninstall marker) back.
            try {
                clearDisabledName(entry.name)
            } catch (t: Throwable) {
                throw ExtensionException(
                    "The ${entry.name} extension was installed but its disabled marker could " +
                        "not be cleared: ${t.describe()}",
                )
            }
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
     * Removes a user-installed extension (never a bundled one) and clears its
     * disabled marker, so a bundled copy of the same name becomes active again.
     *
     * [name] may be given with or without the `lib` prefix and the `.so`
     * suffix. Returns a failure when no user copy exists or the file cannot be
     * deleted; the bundled copy (if any) stays untouched.
     */
    fun remove(name: String): Result<Unit> {
        return try {
            val normalizedName = normalizeExtensionName(name)
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
            // A user-copy uninstall re-enables a disabled bundled copy.
            clearDisabledName(normalizedName)
            Log.i(TAG, "Removed user-installed extension ${userFile.name}")
            Result.success(Unit)
        } catch (e: ExtensionException) {
            Result.failure(e)
        } catch (t: Throwable) {
            Result.failure(ExtensionException("Could not remove the extension: ${t.describe()}"))
        }
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

    /** Marker names lowercased for case-insensitive lookup. */
    private fun Set<String>.matches(extensionName: String): Boolean =
        contains(extensionName.trim().lowercase(Locale.US))

    companion object {
        /** Catalog of prebuilt extensions hosted in this repository. */
        const val CATALOG_URL =
            "https://raw.githubusercontent.com/spainclaro-debug/tanoshi-termux/main/android/extensions/index.json"

        /** Label shown for the built-in repository. */
        const val DEFAULT_REPO_LABEL = "tanoshi-termux (default)"

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

        /** At most this many user-added repositories. */
        private const val MAX_CUSTOM_REPOSITORIES = 10

        private const val GITHUB_RAW_HOST = "raw.githubusercontent.com"
        private val GITHUB_BRANCHES = listOf("main", "master")
        private const val DEFAULT_CATALOG_PATH = "android/extensions/index.json"
        private const val ALT_CATALOG_PATH = "tanoshi-extensions.json"

        private const val DISABLED_MARKER_FILE = ".tanoshi-disabled"


        private val NAME_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")
        private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")
        private val GITHUB_REPO_PATTERN = Regex(
            "^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$",
            RegexOption.IGNORE_CASE,
        )

        /** Native libraries in `nativeLibraryDir` that are not extensions. */
        private val SUPPORT_LIBRARY_NAMES = setOf("libtanoshi.so", "libarchive.so")
        private val SUPPORT_LIBRARY_PREFIXES = listOf("libandroidx", "libdatastore")

        private val CATALOG_ENTRY_ORDER: Comparator<CatalogExtension> =
            compareBy<CatalogExtension> { it.displayName.lowercase(Locale.US) }.thenBy { it.name }

        private val INSTALLED_EXTENSION_ORDER: Comparator<InstalledExtension> =
            compareBy<InstalledExtension> { it.displayName.lowercase(Locale.US) }
                .thenBy { it.name }
    }
}
