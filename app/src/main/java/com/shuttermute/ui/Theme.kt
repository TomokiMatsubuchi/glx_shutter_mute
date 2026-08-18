package com.shuttermute.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CDEFF),
    onPrimary = Color(0xFF003544),
    primaryContainer = Color(0xFF004E63),
    onPrimaryContainer = Color(0xFFB9EBFF),
    secondary = Color(0xFF8ED9A8),
    onSecondary = Color(0xFF00391F),
    secondaryContainer = Color(0xFF145233),
    onSecondaryContainer = Color(0xFFA9F4C3),
    tertiary = Color(0xFFFFB95F),
    onTertiary = Color(0xFF432C00),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFE1E3E6),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFE1E3E6),
    surfaceVariant = Color(0xFF1C2429),
    onSurfaceVariant = Color(0xFFBFC8CD),
    outline = Color(0xFF899297),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006780),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB6EBFF),
    onPrimaryContainer = Color(0xFF001F28),
    secondary = Color(0xFF216C45),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFA9F4C3),
    onSecondaryContainer = Color(0xFF00210F),
    tertiary = Color(0xFF855400),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6FAFD),
    onBackground = Color(0xFF161C1F),
    surface = Color(0xFFF6FAFD),
    onSurface = Color(0xFF161C1F),
    surfaceVariant = Color(0xFFE6EEF2),
    onSurfaceVariant = Color(0xFF3F484C),
    outline = Color(0xFF6F797D),
    error = Color(0xFFBA1A1A),
)

@Composable
fun ShutterMuteTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
