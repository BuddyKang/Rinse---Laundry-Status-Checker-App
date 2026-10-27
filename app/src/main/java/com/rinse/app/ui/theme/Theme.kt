package com.rinse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF171A21),
    onPrimary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1F2937),
    surface = Color.White,
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF6B7280)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE5E7EB),
    onPrimary = Color(0xFF171A21),
    background = Color(0xFF111318),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF181B22),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF242933),
    onSurfaceVariant = Color(0xFFB6BDC8)
)

@Composable
fun RinseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
