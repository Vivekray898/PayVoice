package com.vivekray898.payvoice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

// Stripe DESIGN.md baseline (see docs/DESIGN_SYSTEM.md): indigo primary,
// navy ink text, white canvas surfaces, cool off-white background, ruby
// errors. Success green is an Android-added semantic (the reference keeps
// success states in product UI only).
private val LightColors = lightColorScheme(
    primary = StripePrimary,
    onPrimary = StripeOnPrimary,
    primaryContainer = StripePrimarySubdued,
    onPrimaryContainer = StripeBrandDark,
    secondary = StripeInkSecondary,
    onSecondary = StripeOnPrimary,
    secondaryContainer = StripeHairline,
    onSecondaryContainer = StripeInk,
    tertiary = Positive,
    onTertiary = Color.White,
    tertiaryContainer = Positive.copy(alpha = 0.14f).compositeOver(StripeCanvas),
    onTertiaryContainer = StripeInk,
    background = StripeCanvasSoft,
    onBackground = StripeInk,
    surface = StripeCanvas,
    onSurface = StripeInk,
    surfaceVariant = StripeCanvasSoft,
    onSurfaceVariant = StripeInkMute,
    surfaceContainer = StripeCanvasSoft,
    surfaceContainerHigh = Color(0xFFEEF2F6), // (derived, hairline-adjacent)
    surfaceContainerHighest = StripeHairline,
    outline = StripeHairlineInput,
    outlineVariant = StripeHairline,
    error = StripeRuby,
    onError = Color.White,
    errorContainer = StripeRuby.copy(alpha = 0.12f).compositeOver(StripeCanvas),
    onErrorContainer = StripeInk,
)

private val DarkColors = darkColorScheme(
    primary = StripePrimarySoft,
    onPrimary = Color(0xFF0D1024),
    primaryContainer = StripePrimaryPress,
    onPrimaryContainer = StripePrimarySubdued,
    secondary = Color(0xFFC3CDDB), // (derived light of ink-secondary)
    onSecondary = StripeDarkBackground,
    secondaryContainer = StripeInkSecondary,
    onSecondaryContainer = Color(0xFFE8EEF5),
    tertiary = PositiveDark,
    onTertiary = StripeDarkBackground,
    tertiaryContainer = PositiveDark.copy(alpha = 0.16f).compositeOver(StripeDarkSurface),
    onTertiaryContainer = StripeDarkOnSurface,
    background = StripeDarkBackground,
    onBackground = StripeDarkOnSurface,
    surface = StripeDarkSurface,
    onSurface = StripeDarkOnSurface,
    surfaceVariant = StripeBrandDark,
    onSurfaceVariant = StripeDarkOnSurfaceVariant,
    surfaceContainer = StripeBrandDark,
    surfaceContainerHigh = Color(0xFF262A5E), // (derived)
    surfaceContainerHighest = Color(0xFF313569), // (derived)
    outline = Color(0xFF8B97A8), // (derived)
    outlineVariant = Color(0xFF3A3E6E), // (derived)
    error = Color(0xFFFF7A9C), // (derived light ruby)
    onError = Color(0xFF3B0A1C),
    errorContainer = Color(0xFF4A1128),
    onErrorContainer = Color(0xFFFFD9E1),
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
