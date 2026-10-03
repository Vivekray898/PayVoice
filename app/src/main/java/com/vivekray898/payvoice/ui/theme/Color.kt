package com.vivekray898.payvoice.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Brand (DESIGN.md colors.*) --------------------------------------
val Indigo = Color(0xFF533AFD) // colors.primary
val IndigoDeep = Color(0xFF4434D4) // colors.primary-deep
val IndigoPress = Color(0xFF2E2B8C) // colors.primary-press
val IndigoSoft = Color(0xFF665EFD) // colors.primary-soft
val IndigoSubdued = Color(0xFFB9B9F9) // colors.primary-bg-subdued-hover
val BrandDark900 = Color(0xFF1C1E54) // colors.brand-dark-900
val Ruby = Color(0xFFEA2261) // colors.ruby — errors only
val Magenta = Color(0xFFF96BEE) // colors.magenta — accent only
val Lemon = Color(0xFF9B6829) // colors.lemon — warning foreground (light)

// ---- Surfaces (DESIGN.md colors.canvas*) ------------------------------
val Canvas = Color(0xFFFFFFFF) // colors.canvas
val CanvasSoft = Color(0xFFF6F9FC) // colors.canvas-soft
val CanvasCream = Color(0xFFF5E9D4) // colors.canvas-cream
val Hairline = Color(0xFFE3E8EE) // colors.hairline
val HairlineInput = Color(0xFFA8C3DE) // colors.hairline-input

// ---- Text (DESIGN.md colors.ink*) ------------------------------------
val Ink = Color(0xFF0D253D) // colors.ink — body text, never pure black
val InkSecondary = Color(0xFF273951) // colors.ink-secondary
val InkMute = Color(0xFF64748D) // colors.ink-mute
val InkMute2 = Color(0xFF61718A) // colors.ink-mute-2
val OnPrimary = Color(0xFFFFFFFF) // colors.on-primary

// ---- Shadow tokens (DESIGN.md elevation.*) ---------------------------
val ShadowBlue = Color(0xFF003770) // elevation.shadow-blue

// ---- Semantic status palette (added by the redesign; see
//      docs/DESIGN_DIRECTION.md deviation #4) --------------------------
//
// DESIGN.md declares no semantic palette, but the health system needs four
// distinguishable tones. Every value below is checked to >= 4.5:1 against the
// surface it is drawn on:
//
//   light (on Canvas #ffffff):  success 5.4:1 · warning 4.8:1 · error 5.6:1
//   dark  (on BrandDark900):    success 7.0:1 · warning 6.9:1 · error 5.7:1
//
// The reference's raw Ruby is only 3.6:1 on the navy shell, so dark surfaces
// use the lifted RubyDark rather than Ruby itself.

/** Foreground status colors — light track. */
val SuccessLight = Color(0xFF0E7A4A) // 5.4:1 on Canvas
val SuccessContainerLight = Color(0xFFDCEFE5)
val SuccessOnContainerLight = Color(0xFF0E7A4A)

val WarningContainerLight = Color(0xFFF6EADA)
val WarningOnContainerLight = Color(0xFF7A5121) // 5.8:1 on its container

val ErrorContainerLight = Color(0xFFFCE4EA)
val ErrorOnContainerLight = Color(0xFFB01348) // Ruby on cream was 3.6:1

val NeutralContainerLight = Color(0xFFF6F9FC)
val NeutralOnContainerLight = Ink

/** Foreground status colors — dark track (on BrandDark900). */
val SuccessDark = Color(0xFF4CC38A) // 7.0:1
val SuccessContainerDark = Color(0xFF12382A)
val SuccessOnContainerDark = SuccessDark

val WarningDark = Color(0xFFD9A441) // 6.9:1
val WarningContainerDark = Color(0xFF3A2E15)
val WarningOnContainerDark = WarningDark

val RubyDark = Color(0xFFFF6B93) // 5.7:1 (Ruby itself is 3.6:1 on navy)
val ErrorContainerDark = Color(0xFF3D1824)
val ErrorOnContainerDark = RubyDark

val NeutralContainerDark = InkSecondary
val NeutralOnContainerDark = Canvas
