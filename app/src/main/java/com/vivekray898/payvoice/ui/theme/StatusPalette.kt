package com.vivekray898.payvoice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Four-tone status palette for `StatusPill`, `StatusIcon`, health banners and
 * error states.
 *
 * Material 3's `ColorScheme` has no success/warning slots, and the brand
 * reference explicitly declares no semantic palette. Rather than overloading
 * `primary`/`tertiary` (which made "healthy" indistinguishable from "action")
 * the redesign adds this palette as a composition local — same lookup ergonomics
 * as `MaterialTheme.colorScheme`, same guarantee that nothing hard-codes a hex.
 *
 * All foreground/background pairs are contrast-checked; see `Color.kt`.
 */
@Immutable
data class PvStatusPalette(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
)

private val LightStatus = PvStatusPalette(
    success = SuccessLight,
    successContainer = SuccessContainerLight,
    onSuccessContainer = SuccessOnContainerLight,
    warning = Lemon,
    warningContainer = WarningContainerLight,
    onWarningContainer = WarningOnContainerLight,
    error = Ruby,
    errorContainer = ErrorContainerLight,
    onErrorContainer = ErrorOnContainerLight,
    neutral = InkMute,
    neutralContainer = NeutralContainerLight,
    onNeutralContainer = NeutralOnContainerLight,
)

private val DarkStatus = PvStatusPalette(
    success = SuccessDark,
    successContainer = SuccessContainerDark,
    onSuccessContainer = SuccessOnContainerDark,
    warning = WarningDark,
    warningContainer = WarningContainerDark,
    onWarningContainer = WarningOnContainerDark,
    error = RubyDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorOnContainerDark,
    neutral = InkMute,
    neutralContainer = NeutralContainerDark,
    onNeutralContainer = NeutralOnContainerDark,
)

/** Current status palette. Same call shape as `MaterialTheme.colorScheme`. */
object PvStatusTheme {
    val colors: PvStatusPalette
        @Composable get() = LocalPvStatus.current
}

internal val LocalPvStatus = staticCompositionLocalOf { LightStatus }

/** Resolve the palette for the active theme. Used by `PayVoiceTheme`. */
internal fun pvStatusPaletteFor(darkTheme: Boolean): PvStatusPalette =
    if (darkTheme) DarkStatus else LightStatus

/** Convenience for previews and tests that are not inside `PayVoiceTheme`. */
@Composable
fun statusPalette(darkTheme: Boolean = isSystemInDarkTheme()): PvStatusPalette =
    pvStatusPaletteFor(darkTheme)
