package com.vivekray898.payvoice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealOnContainer,
    secondary = SandSecondary,
    onSecondary = SandOnSecondary,
    secondaryContainer = SandContainer,
    onSecondaryContainer = SandOnContainer,
    tertiary = IndigoTertiary,
    onTertiary = Color.White,
    tertiaryContainer = IndigoContainer,
    onTertiaryContainer = Color(0xFF001E31),
    background = SurfaceLight,
    onBackground = InkText,
    surface = SurfaceLight,
    onSurface = InkText,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = MutedText,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = Negative,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealOnContainerDark,
    secondary = SandSecondaryDark,
    onSecondary = SandOnSecondaryDark,
    secondaryContainer = SandContainerDark,
    onSecondaryContainer = SandOnContainerDark,
    tertiary = IndigoTertiaryDark,
    onTertiary = Color(0xFF10202C),
    tertiaryContainer = IndigoContainerDark,
    onTertiaryContainer = IndigoContainerDark,
    background = SurfaceDark,
    onBackground = InkTextDark,
    surface = SurfaceDark,
    onSurface = InkTextDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = MutedTextDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = NegativeDark,
    onError = Color(0xFF601410),
)

/** PayVoice identity — dynamic color intentionally off for brand consistency. */
@Composable
fun PayVoiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = PayVoiceShapes,
        content = content,
    )
}
