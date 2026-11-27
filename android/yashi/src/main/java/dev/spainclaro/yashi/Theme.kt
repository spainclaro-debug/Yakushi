package dev.spainclaro.yashi

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD3B4),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF145241),
    onPrimaryContainer = Color(0xFFC4F5E4),
    secondary = Color(0xFFE8B84B),
    onSecondary = Color(0xFF2A1F04),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE7EBF0),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE7EBF0),
    surfaceVariant = Color(0xFF2A343E),
    onSurfaceVariant = Color(0xFFA3AFBC),
    outline = Color(0xFF4B5866),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B56),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2F2DE),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF7A5F0E),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7FBF8),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF7FBF8),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975),
    error = Color(0xFFBA1A1A),
)

@Composable
fun YashiTheme(mode: YashiThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        YashiThemeMode.SYSTEM -> isSystemInDarkTheme()
        YashiThemeMode.DARK -> true
        YashiThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
