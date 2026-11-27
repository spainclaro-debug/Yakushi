package dev.spainclaro.yashi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = AddressStore(this)
        setContent {
            val theme by store.theme.collectAsStateWithLifecycle()
            YashiTheme(theme) {
                YashiRoot(store = store, theme = theme, onThemeChange = store::setTheme)
            }
        }
    }
}

@Composable
private fun YashiRoot(
    store: AddressStore,
    theme: YashiThemeMode,
    onThemeChange: (YashiThemeMode) -> Unit,
) {
    var activeUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val url = activeUrl
    if (url == null) {
        MainScreen(
            store = store,
            theme = theme,
            onThemeChange = onThemeChange,
            onOpenServer = { address ->
                store.addToHistory(address)
                activeUrl = address.url
            },
        )
    } else {
        BrowserScreen(
            url = url,
            onExit = { activeUrl = null },
        )
    }
}
