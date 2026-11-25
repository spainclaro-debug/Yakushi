package dev.spainclaro.tanoshi.ui

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import dev.spainclaro.tanoshi.extensions.ExtensionManager
import dev.spainclaro.tanoshi.server.TanhoshiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Bridge exposed to the embedded Tanoshi web UI as `window.TanoshiApp`.
 *
 * The web UI is served from the loopback server and cannot reach Android APIs
 * on its own. The backup/restore page uses this bridge to save a backup file in
 * the phone's Downloads folder and to install missing sources through the app's
 * extension catalog (which restarts the server so the new plugins load).
 */
class WebAppBridge(
    context: Context,
    private val webViewProvider: () -> WebView?,
) {
    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Writes the backup JSON to the shared Downloads folder (MediaStore on
     * Android 10+, the app's external Downloads folder on older releases) and
     * returns `OK|<location>` or `ERROR|<message>`.
     */
    @JavascriptInterface
    fun saveBackup(fileName: String, json: String): String {
        val safeName = sanitizeFileName(fileName)
        return try {
            val location = writeToDownloads(safeName, json)
            Log.i(TAG, "Saved backup to $location")
            "OK|$location"
        } catch (t: Throwable) {
            Log.w(TAG, "Could not save the backup", t)
            "ERROR|${t.message ?: "unknown error"}"
        }
    }

    /**
     * Queues a reader image (the already-proxied absolute URL) in the system
     * DownloadManager, which writes it to the public Downloads folder. This
     * works on every supported Android version without storage permissions and
     * shows a download notification. Returns `OK|<location>` or `ERROR|<message>`.
     */
    @JavascriptInterface
    fun saveImage(url: String, fileName: String): String {
        val safeName = sanitizeFileName(fileName)
        return try {
            val mime = when (safeName.substringAfterLast('.', "").lowercase(Locale.US)) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(safeName)
                .setDescription("Yakushi reader image")
                .setMimeType(mime)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Log.i(TAG, "Queued image download for $safeName")
            "OK|${Environment.DIRECTORY_DOWNLOADS}/$safeName"
        } catch (t: Throwable) {
            Log.w(TAG, "Could not queue the image download", t)
            "ERROR|${t.message ?: "unknown error"}"
        }
    }

    /**
     * Installs the extensions named in [sourcesJson] (`[{"id":1,"name":"..."}]`).
     * Matching uses the extension catalog's display/name fields. When anything
     * was installed the server is restarted so the plugins are loaded.
     */
    @JavascriptInterface
    fun installExtensions(sourcesJson: String) {
        scope.launch {
            val manager = ExtensionManager(appContext)
            val requested = runCatching { JSONArray(sourcesJson) }.getOrDefault(JSONArray())
            val requestedNames = (0 until requested.length()).mapNotNull { index ->
                requested.optJSONObject(index)
                    ?.optString("name")
                    ?.takeIf { it.isNotBlank() }
            }.distinct()
            if (requestedNames.isEmpty()) {
                return@launch
            }

            val installed = ArrayList<String>()
            val failed = ArrayList<String>()
            val catalog = runCatching { manager.fetchCatalogs() }.getOrNull()
            if (catalog == null) {
                dispatch(installed, requestedNames)
                return@launch
            }

            for (name in requestedNames) {
                val wanted = normalizeSourceName(name)
                val entry = catalog.entries.firstOrNull {
                    normalizeSourceName(it.name) == wanted ||
                        normalizeSourceName(it.displayName) == wanted
                }
                if (entry == null) {
                    failed.add(name)
                    continue
                }
                val result = manager.install(entry) { _, _ -> }
                result.fold(
                    onSuccess = { installed.add(entry.displayName) },
                    onFailure = {
                        Log.w(TAG, "Could not install ${entry.displayName}", it)
                        failed.add(entry.displayName)
                    },
                )
            }

            if (installed.isNotEmpty()) {
                runCatching { TanhoshiService.restart(appContext) }
                    .onFailure { Log.w(TAG, "Could not restart the server after installs", it) }
            }
            dispatch(installed, failed)
        }
    }

    private fun dispatch(installed: List<String>, failed: List<String>) {
        val detail = JSONObject().apply {
            put("installed", JSONArray(installed))
            put("failed", JSONArray(failed))
        }
        val script =
            "window.dispatchEvent(new CustomEvent('tanoshi-extensions-changed',{detail:$detail}));"
        val view = webViewProvider() ?: return
        view.post {
            runCatching { view.evaluateJavascript(script, null) }
        }
    }

    private fun writeToDownloads(name: String, json: String): String =
        writeBytesToDownloads(name, MIME_TYPE, json.toByteArray(Charsets.UTF_8))

    private fun writeBytesToDownloads(name: String, mimeType: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, values)
                ?: error("could not create the download entry")
            try {
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytes)
                } ?: error("could not open the download file")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
            return "${Environment.DIRECTORY_DOWNLOADS}/$name"
        }

        // Android 7-9: no scoped MediaStore.Downloads yet and writing the shared
        // Downloads folder would need the storage permission; the app's external
        // downloads folder is visible from a file manager without it.
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
        if (!directory.isDirectory && !directory.mkdirs()) {
            error("could not create the download folder")
        }
        val file = File(directory, name)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_', '.')
        return if (cleaned.isBlank()) "tanoshi-backup.json" else cleaned
    }

    private fun normalizeSourceName(name: String): String =
        name.lowercase(Locale.US).filter { it.isLetterOrDigit() }

    private companion object {
        const val TAG = "WebAppBridge"
        const val MIME_TYPE = "application/json"
    }
}
