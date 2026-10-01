package com.vivekray898.payvoice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light scheme: the consumer brand track — indigo on canvas white, navy ink,
// hairline borders, ruby errors.
private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = OnPrimary,
    primaryContainer = IndigoSubdued,
    onPrimaryContainer = IndigoDeep,
    secondary = InkSecondary,
    onSecondary = OnPrimary,
    secondaryContainer = Hairline,
    onSecondaryContainer = Ink,
    tertiary = BrandDark900,
    onTertiary = OnPrimary,
    tertiaryContainer = CanvasCream,
    onTertiaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Canvas,
    onSurface = Ink,
    surfaceVariant = CanvasSoft,
    onSurfaceVariant = InkMute,
    surfaceContainer = CanvasSoft,
    surfaceContainerHigh = Hairline,
    surfaceContainerHighest = Hairline,
    surfaceDim = Hairline,
    surfaceBright = Canvas,
    surfaceTint = Indigo,
    outline = Hairline,
    outlineVariant = HairlineInput,
    error = Ruby,
    onError = OnPrimary,
    errorContainer = CanvasCream,
    onErrorContainer = Ruby,
    inverseSurface = BrandDark900,
    inverseOnSurface = Canvas,
)

// Dark scheme: the dashboard-shell track — deep navy surfaces, canvas text,
// soft indigo for action. InkSecondary/InkMute2 stand in for derived roles
// the reference declares in its navy polarity.
private val DarkColors = darkColorScheme(
    primary = IndigoSoft,
    onPrimary = OnPrimary,
    primaryContainer = IndigoPress,
    onPrimaryContainer = IndigoSubdued,
    secondary = InkMute,
    onSecondary = Canvas,
    secondaryContainer = InkSecondary,
    onSecondaryContainer = Canvas,
    tertiary = IndigoSubdued,
    onTertiary = BrandDark900,
    tertiaryContainer = InkSecondary,
    onTertiaryContainer = Canvas,
    background = BrandDark900,
    onBackground = OnPrimary,
    surface = BrandDark900,
    onSurface = OnPrimary,
    surfaceVariant = InkSecondary,
    onSurfaceVariant = InkMute,
    surfaceContainer = InkSecondary,
    surfaceContainerHigh = InkMute2,
    surfaceContainerHighest = InkMute2,
    surfaceDim = BrandDark900,
    surfaceBright = InkSecondary,
    surfaceTint = IndigoSoft,
    outline = InkMute,
    outlineVariant = InkMute2,
    error = Ruby,
    onError = OnPrimary,
    errorContainer = InkSecondary,
    onErrorContainer = IndigoSubdued,
    inverseSurface = Canvas,
    inverseOnSurface = Ink,
)

/** PayVoice identity — dynamic color intentionally off for brand consistency. */
@Composable
fun PayVoiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PvTypography,
        shapes = PvShapes,
        content = content,
    )
}
