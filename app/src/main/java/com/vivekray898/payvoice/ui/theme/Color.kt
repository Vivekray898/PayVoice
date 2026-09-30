package com.vivekray898.payvoice.ui.theme

import androidx.compose.ui.graphics.Color

// PayVoice identity: deep teal (trust, money) + warm amber accent, restrained
// supporting colors. Full Material 3 roles so surfaces/containers/outlines
// behave correctly in light and dark without ad-hoc colors.

// ---- Primary (teal) ----
val TealPrimary = Color(0xFF00695C)
val TealOnPrimary = Color(0xFFFFFFFF)
val TealContainer = Color(0xFFB7EFE4)
val TealOnContainer = Color(0xFF00201B)
val TealPrimaryDark = Color(0xFF53DBC4)
val TealOnPrimaryDark = Color(0xFF00382F)
val TealContainerDark = Color(0xFF005048)
val TealOnContainerDark = Color(0xFFB7EFE4)

// ---- Secondary (warm sand) ----
val SandSecondary = Color(0xFF7A5900)
val SandOnSecondary = Color(0xFFFFFFFF)
val SandContainer = Color(0xFFFFE08C)
val SandOnContainer = Color(0xFF241A00)
val SandSecondaryDark = Color(0xFFF2BF4E)
val SandOnSecondaryDark = Color(0xFF3F2E00)
val SandContainerDark = Color(0xFF5C4300)
val SandOnContainerDark = Color(0xFFFFE08C)

// ---- Tertiary (calm indigo — used sparingly for informational accents) ----
val IndigoTertiary = Color(0xFF456179)
val IndigoContainer = Color(0xFFCDE5FF)
val IndigoTertiaryDark = Color(0xFFACCEE8)
val IndigoContainerDark = Color(0xFF2D4960)

// ---- Text / surfaces ----
val InkText = Color(0xFF191C1B)
val InkTextDark = Color(0xFFE0E3E1)
val MutedText = Color(0xFF3F4946)
val MutedTextDark = Color(0xFFBEC9C5)

val SurfaceLight = Color(0xFFFAFDFB)
val SurfaceDimLight = Color(0xFFDAE5E0)
val SurfaceContainerLight = Color(0xFFEFF1EF)
val SurfaceContainerHighLight = Color(0xFFE9EBEA)
val OutlineLight = Color(0xFF6F7976)
val OutlineVariantLight = Color(0xFFDBE1DE)

val SurfaceDark = Color(0xFF101413)
val SurfaceContainerDark = Color(0xFF1C211F)
val SurfaceContainerHighDark = Color(0xFF262B29)
val OutlineDark = Color(0xFF89938F)
val OutlineVariantDark = Color(0xFF3F4946)

// ---- Stripe reference (DESIGN.md, adopted 2026-09-30 — visual source of truth) ----
// Hex values come verbatim from DESIGN.md; dark-scheme values marked (derived)
// follow the reference's "dark-app dashboard track" navy polarity.
val StripePrimary = Color(0xFF533AFD) // colors.primary
val StripePrimaryDeep = Color(0xFF4434D4) // colors.primary-deep
val StripePrimaryPress = Color(0xFF2E2B8C) // colors.primary-press
val StripePrimarySoft = Color(0xFF665EFD) // colors.primary-soft
val StripePrimarySubdued = Color(0xFFB9B9F9) // colors.primary-bg-subdued-hover
val StripeBrandDark = Color(0xFF1C1E54) // colors.brand-dark-900
val StripeInk = Color(0xFF0D253D) // colors.ink — body text, never pure black
val StripeInkSecondary = Color(0xFF273951) // colors.ink-secondary
val StripeInkMute = Color(0xFF64748D) // colors.ink-mute
val StripeOnPrimary = Color(0xFFFFFFFF) // colors.on-primary
val StripeCanvas = Color(0xFFFFFFFF) // colors.canvas
val StripeCanvasSoft = Color(0xFFF6F9FC) // colors.canvas-soft
val StripeHairline = Color(0xFFE3E8EE) // colors.hairline
val StripeHairlineInput = Color(0xFFA8C3DE) // colors.hairline-input
val StripeRuby = Color(0xFFEA2261) // colors.ruby — errors only
// Dark scheme (derived from the reference's navy polarity):
val StripeDarkBackground = Color(0xFF0D1024) // (derived)
val StripeDarkSurface = Color(0xFF14173A) // (derived)
val StripeDarkOnSurface = Color(0xFFE8EEF5) // (derived)
val StripeDarkOnSurfaceVariant = Color(0xFFA5AEC6) // (derived)

// ---- Google baseline (superseded palette, kept for status semantics) ----
// One strong primary (Google Blue), restrained grey secondary, green used
// only for success, red only for error. Off-white background keeps cards
// visible without heavy elevation.
val GoogleBlue = Color(0xFF1A73E8)
val GoogleOnBlue = Color(0xFFFFFFFF)
val GoogleBlueContainer = Color(0xFFD2E3FC)
val GoogleOnBlueContainer = Color(0xFF041E49)
val GoogleBlueDark = Color(0xFF8AB4F8)
val GoogleOnBlueDark = Color(0xFF041E49)
val GoogleBlueContainerDark = Color(0xFF0842A0)
val GoogleOnBlueContainerDark = Color(0xFFD2E3FC)

val GoogleGrey = Color(0xFF5F6368)
val GoogleOnGrey = Color(0xFFFFFFFF)
val GoogleGreyContainer = Color(0xFFE8EAED)
val GoogleOnGreyContainer = Color(0xFF202124)
val GoogleGreyDark = Color(0xFFC4C7C5)
val GoogleOnGreyDark = Color(0xFF202124)
val GoogleGreyContainerDark = Color(0xFF2D2F31)
val GoogleOnGreyContainerDark = Color(0xFFE8EAED)

val GoogleGreen = Color(0xFF1E8E3E)
val GoogleGreenDark = Color(0xFF81C995)

val GoogleError = Color(0xFFD93025)
val GoogleOnError = Color(0xFFFFFFFF)
val GoogleErrorContainer = Color(0xFFFCE8E6)
val GoogleErrorDark = Color(0xFFF28B82)
val GoogleOnErrorDark = Color(0xFF601410)
val GoogleErrorContainerDark = Color(0xFF3B1D1A)

val GoogleSurfaceLight = Color(0xFFFFFFFF)
val GoogleBackgroundLight = Color(0xFFF8F9FA)
val GoogleSurfaceVariantLight = Color(0xFFEEF1F4)
val GoogleOutlineLight = Color(0xFFDADCE0)
val GoogleOutlineVariantLight = Color(0xFFE8EAED)

val GoogleSurfaceDark = Color(0xFF1F1F1F)
val GoogleBackgroundDark = Color(0xFF121212)
val GoogleSurfaceVariantDark = Color(0xFF2A2B2E)
val GoogleOutlineDark = Color(0xFF3C4043)
val GoogleOutlineVariantDark = Color(0xFF2D2F31)

// ---- Semantic status (restrained) — now on Google's green/red ----
val Positive = Color(0xFF1E8E3E)
val PositiveDark = Color(0xFF81C995)
val Caution = Color(0xFF9A6B00)
val CautionDark = Color(0xFFF2BF4E)
val Negative = Color(0xFFD93025)
val NegativeDark = Color(0xFFF28B82)
