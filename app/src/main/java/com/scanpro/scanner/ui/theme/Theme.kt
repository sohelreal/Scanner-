package com.scanpro.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TealPrimary = Color(0xFF00897B)
val TealDark = Color(0xFF00695C)
val TealLight = Color(0xFF4DB6AC)
val TealContainer = Color(0xFFE0F2F1)
val TealOnContainer = Color(0xFF003D33)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealOnContainer,
    secondary = Color(0xFF26A69A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00251A),
    background = Color(0xFFF5F7F8),
    onBackground = Color(0xFF191C1B),
    surface = Color.White,
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFE8EEF0),
    onSurfaceVariant = Color(0xFF4A5558),
    outline = Color(0xFFCBD5D8),
    error = Color(0xFFD32F2F)
)

private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE1E3E2),
    surface = Color(0xFF181D1D),
    onSurface = Color(0xFFE1E3E2),
    surfaceVariant = Color(0xFF252D2D),
    onSurfaceVariant = Color(0xFFA8B5B5),
    outline = Color(0xFF3E4949),
    error = Color(0xFFEF9A9A)
)

@Composable
fun ScanProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
