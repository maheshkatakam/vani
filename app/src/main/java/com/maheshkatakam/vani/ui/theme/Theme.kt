package com.maheshkatakam.vani.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Indigo = Color(0xFF4F46E5)
private val Violet = Color(0xFF9333EA)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Violet,
    tertiary = Color(0xFF0EA5E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    secondary = Color(0xFFD8B4FE),
    tertiary = Color(0xFF7DD3FC),
)

/** Material You colors from the wallpaper on Android 12+, Vani's indigo/violet otherwise. */
@Composable
fun VaniTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
