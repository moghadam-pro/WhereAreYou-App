package com.whereareyou.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SeaGreen = Color(0xFF1B4332)
private val SeaGreenLight = Color(0xFF52796F)
private val Sand = Color(0xFFF1FAEE)
private val Amber = Color(0xFFE9C46A)
private val Rust = Color(0xFFBC4749)

private val LightColors = lightColorScheme(
    primary = SeaGreen,
    secondary = SeaGreenLight,
    tertiary = Amber,
    error = Rust,
    background = Sand,
)

private val DarkColors = darkColorScheme(
    primary = SeaGreenLight,
    secondary = SeaGreen,
    tertiary = Amber,
    error = Rust,
)

@Composable
fun WhereAreYouTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
