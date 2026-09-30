package com.vivekray898.payvoice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

// GPay-style baseline: off-white background, white surfaces, Google Blue
// primary, grey secondary, green only for success states.
private val LightColors = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = GoogleOnBlue,
    primaryContainer = GoogleBlueContainer,
    onPrimaryContainer = GoogleOnBlueContainer,
    secondary = GoogleGrey,
    onSecondary = GoogleOnGrey,
    secondaryContainer = GoogleGreyContainer,
    onSecondaryContainer = GoogleOnGreyContainer,
    tertiary = GoogleGreen,
    onTertiary = Color.White,
    tertiaryContainer = GoogleGreen.copy(alpha = 0.14f).compositeOver(GoogleSurfaceLight),
    onTertiaryContainer = GoogleOnBlueContainer,
    background = GoogleBackgroundLight,
    onBackground = Color(0xFF202124),
    surface = GoogleSurfaceLight,
    onSurface = Color(0xFF202124),
    surfaceVariant = GoogleSurfaceVariantLight,
    onSurfaceVariant = GoogleGrey,
    surfaceContainer = GoogleSurfaceVariantLight,
    surfaceContainerHigh = Color(0xFFE8EAED),
    surfaceContainerHighest = Color(0xFFDADCE0),
    outline = Color(0xFF80868B),
    outlineVariant = GoogleOutlineVariantLight,
    error = GoogleError,
    onError = GoogleOnError,
    errorContainer = GoogleErrorContainer,
    onErrorContainer = Color(0xFF202124),
)

private val DarkColors = darkColorScheme(
    primary = GoogleBlueDark,
    onPrimary = GoogleOnBlueDark,
    primaryContainer = GoogleBlueContainerDark,
    onPrimaryContainer = GoogleOnBlueContainerDark,
    secondary = GoogleGreyDark,
    onSecondary = GoogleOnGreyDark,
    secondaryContainer = GoogleGreyContainerDark,
    onSecondaryContainer = GoogleOnGreyContainerDark,
    tertiary = GoogleGreenDark,
    onTertiary = GoogleOnBlueDark,
    tertiaryContainer = GoogleGreenDark.copy(alpha = 0.16f).compositeOver(GoogleSurfaceDark),
    onTertiaryContainer = GoogleOnBlueContainerDark,
    background = GoogleBackgroundDark,
    onBackground = Color(0xFFE8EAED),
    surface = GoogleSurfaceDark,
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = GoogleSurfaceVariantDark,
    onSurfaceVariant = GoogleGreyDark,
    surfaceContainer = GoogleSurfaceVariantDark,
    surfaceContainerHigh = Color(0xFF333537),
    surfaceContainerHighest = Color(0xFF3C4043),
    outline = Color(0xFF9AA0A6),
    outlineVariant = GoogleOutlineVariantDark,
    error = GoogleErrorDark,
    onError = GoogleOnErrorDark,
    errorContainer = GoogleErrorContainerDark,
    onErrorContainer = Color(0xFFFCE8E6),
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
