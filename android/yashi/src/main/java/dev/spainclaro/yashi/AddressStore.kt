package dev.spainclaro.yashi

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

enum class YashiThemeMode(val id: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light"),
    ;

    companion object {
        fun fromId(id: String?): YashiThemeMode =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Small persistent store for Yashi. History records every address the user
 * connected to (newest first); saved servers are the ones they explicitly
 * pinned for quick access. Both live only on this device.
 */
class AddressStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("yashi", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(loadList(KEY_HISTORY))
    val history: StateFlow<List<ServerAddress>> = _history.asStateFlow()

    private val _saved = MutableStateFlow(loadList(KEY_SAVED))
    val saved: StateFlow<List<ServerAddress>> = _saved.asStateFlow()

    private val _theme = MutableStateFlow(
        YashiThemeMode.fromId(prefs.getString(KEY_THEME, null)),
    )
    val theme: StateFlow<YashiThemeMode> = _theme.asStateFlow()

    var cameraAsked: Boolean
        get() = prefs.getBoolean(KEY_CAMERA_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_CAMERA_ASKED, value).apply()

    /** Records a successful/failed connection target; newest first, de-duped. */
    fun addToHistory(address: ServerAddress) {
        _history.value = (listOf(address) + _history.value)
            .distinctBy { it.url }
            .take(MAX_HISTORY)
        persistList(KEY_HISTORY, _history.value)
    }

    fun isSaved(address: ServerAddress): Boolean = _saved.value.any { it.url == address.url }

    /** Saves a history entry; no-op when already saved. Returns true if added. */
    fun save(address: ServerAddress): Boolean {
        if (isSaved(address)) return false
        _saved.value = _saved.value + address
        persistList(KEY_SAVED, _saved.value)
        return true
    }

    fun updateSaved(old: ServerAddress, replacement: ServerAddress): Boolean {
        val index = _saved.value.indexOfFirst { it.url == old.url }
        if (index < 0) return false
        if (_saved.value.any { it.url == replacement.url && it.url != old.url }) return false
        _saved.value = _saved.value.toMutableList().also { it[index] = replacement }
        persistList(KEY_SAVED, _saved.value)
        return true
    }

    fun removeSaved(address: ServerAddress) {
        _saved.value = _saved.value.filterNot { it.url == address.url }
        persistList(KEY_SAVED, _saved.value)
    }

    /** Settings → clear data: forget saved servers and the connection history. */
    fun clearConnectionData() {
        _history.value = emptyList()
        _saved.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).remove(KEY_SAVED).apply()
    }

    fun setTheme(mode: YashiThemeMode) {
        _theme.value = mode
        prefs.edit().putString(KEY_THEME, mode.id).apply()
    }

    private fun loadList(key: String): List<ServerAddress> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                AddressParser.parse(array.optString(index))
            }.distinctBy { it.url }
        }.getOrDefault(emptyList())
    }

    private fun persistList(key: String, list: List<ServerAddress>) {
        val array = JSONArray()
        list.forEach { array.put(it.url) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        const val KEY_HISTORY = "history"
        const val KEY_SAVED = "saved"
        const val KEY_THEME = "theme"
        const val KEY_CAMERA_ASKED = "camera_asked"
        const val MAX_HISTORY = 30
    }
}
