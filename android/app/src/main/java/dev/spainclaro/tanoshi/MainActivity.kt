package dev.spainclaro.tanoshi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import dev.spainclaro.tanoshi.settings.AppSettings
import dev.spainclaro.tanoshi.ui.TanoshiRoot
import dev.spainclaro.tanoshi.ui.theme.TanoshiTheme
import dev.spainclaro.tanoshi.ui.theme.ThemeMode
import dev.spainclaro.tanoshi.ui.theme.colorSchemeFor

/**
 * Single-activity host. The Activity only installs the Compose tree; the server
 * itself lives in [dev.spainclaro.tanoshi.server.TanhoshiService] and survives
 * configuration changes and Activity recreation.
 *
 * The selected [ThemeMode] is owned here: it is seeded from
 * [AppSettings.themeId], kept in `rememberSaveable` across configuration
 * changes, persisted whenever the user picks another theme, and drives both the
 * Compose colour scheme and the system-bar icon contrast.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the persisted theme's system-bar style before the first frame so
        // the status bar icons are readable during startup. Safe to call more
        // than once; onCreate's setContent re-applies it on every change.
        val initialMode = ThemeMode.fromId(AppSettings.themeId)
        applySystemBars(initialMode)
        super.onCreate(savedInstanceState)
        requestStorageAccess()
        // Match the window background to the theme so the pre-Compose frame
        // (light theme especially) does not flash the native dark colour.
        window.setBackgroundDrawable(
            ColorDrawable(colorSchemeFor(initialMode).background.toArgb()),
        )
        setContent {
            var themeMode by rememberSaveable {
                mutableStateOf(ThemeMode.fromId(AppSettings.themeId))
            }
            LaunchedEffect(themeMode) {
                applySystemBars(themeMode)
            }
            TanoshiTheme(mode = themeMode) {
                TanoshiRoot(
                    themeMode = themeMode,
                    onThemeChange = { mode ->
                        themeMode = mode
                        // Single owner for persistence: the Activity callback.
                        AppSettings.themeId = mode.id
                    },
                )
            }
        }
    }

    /**
     * Offline chapters are written to public storage under
     * /storage/emulated/0/yakushi/.downloaded. Android 11+ requires the
     * "All files access" special permission for that folder; Android 7-10 uses
     * the legacy storage permission.
     */
    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
        }
    }

    /**
     * Edge-to-edge with icons that contrast with the selected theme: light
     * (near-white) system icons for dark themes, dark icons for the light theme.
     */
    private fun applySystemBars(mode: ThemeMode) {
        val transparent = Color.TRANSPARENT
        if (mode.isDark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(transparent),
                navigationBarStyle = SystemBarStyle.dark(transparent),
            )
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(transparent, transparent),
                navigationBarStyle = SystemBarStyle.light(transparent, transparent),
            )
        }
    }
}
