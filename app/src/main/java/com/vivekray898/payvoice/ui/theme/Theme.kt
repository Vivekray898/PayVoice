package com.vivekray898.payvoice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Color(0xFF00201B),
    secondary = SandSecondary,
    onSecondary = Color.White,
    secondaryContainer = SandContainer,
    onSecondaryContainer = Color(0xFF241A00),
    background = SurfaceLight,
    onBackground = InkText,
    surface = SurfaceLight,
    onSurface = InkText,
    surfaceContainer = SurfaceContainerLight,
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF00382F),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealContainer,
    secondary = SandSecondaryDark,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = SandContainerDark,
    onSecondaryContainer = SandContainer,
    background = SurfaceDark,
    onBackground = InkTextDark,
    surface = SurfaceDark,
    onSurface = InkTextDark,
    surfaceContainer = SurfaceContainerDark,
)

/** Original PayVoice identity — dynamic color intentionally off for brand consistency. */
@Composable
fun PayVoiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
