package com.vivekray898.payvoice.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

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
    errorContainer = ErrorContainerLight,
    onErrorContainer = ErrorOnContainerLight,
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
    // RubyDark, not Ruby: Ruby is 3.6:1 on the navy shell and fails AA.
    error = RubyDark,
    onError = BrandDark900,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorOnContainerDark,
    inverseSurface = Canvas,
    inverseOnSurface = BrandDark900,
)

/**
 * PayVoice identity.
 *
 * @param darkTheme follow the system, or force one track (previews/tests).
 * @param dynamicColor Material You. **Off by default** so the indigo brand and
 *   the status palette stay consistent; offered as a Settings toggle. The
 *   status palette is never dynamic — health colours must be learnable.
 */
@Composable
fun PayVoiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = PvTypography,
        shapes = PvShapes,
    ) {
        // Provided inside MaterialTheme so both are available to every screen.
        CompositionLocalProvider(LocalPvStatus provides pvStatusPaletteFor(darkTheme)) {
            content()
        }
    }
}

/** Brand default for the "dynamic colour" Settings toggle. */
val PvBrandDynamicColorDefault: Boolean = false
