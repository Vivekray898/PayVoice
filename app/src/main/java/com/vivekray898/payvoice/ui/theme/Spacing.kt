package com.vivekray898.payvoice.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale — the ONLY sanctioned distance values in the app. Never write
 * `13.dp` or `7.dp` inline; pick the nearest token.
 *
 * Two groups:
 *
 * 1. **DESIGN.md `spacing.*`** — the base-8 scale with 2/4 sub-tokens
 *    (`xxs…huge`). Use these for ordinary layout gaps and padding.
 * 2. **Structural tokens** — the named sizes screens previously derived with
 *    arithmetic (`Spacing.xl - Spacing.xs` for a 20dp gutter, three different
 *    expressions for the same 56dp touch height). They exist because the base
 *    scale has no 20/24-adjacent/40/48/56/72 step; naming them removes 50
 *    hand-written `// … dp structural` comments and makes the value auditable
 *    in one place.
 *
 * The enforcement gate treats this file as the only place `.dp` may appear.
 */
object Spacing {
    // ---- DESIGN.md spacing.* -------------------------------------------
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val huge: Dp = 64.dp

    // ---- Structural tokens ---------------------------------------------

    /** 1dp — hairline borders and dividers (DESIGN.md `hairline`). */
    val hairline: Dp = 1.dp

    /** 24dp — the standard glyph box for a leading/trailing icon. */
    val icon: Dp = 24.dp

    /** 20dp — screen side gutter (content inset from the edge). */
    val gutter: Dp = 20.dp

    /** 6dp — the status dot inside a pill and the event-log dot. */
    val dot: Dp = 6.dp

    /** 40dp — circular icon tile behind a glyph. */
    val iconCircle: Dp = 40.dp

    /** 48dp — the minimum interactive row/bar height (AGENTS.md floor). */
    val rowBar: Dp = 48.dp

    /** 56dp — the minimum tap target for every primary control. */
    val touch: Dp = 56.dp

    /** 72dp — settings list row height. */
    val listRow: Dp = 72.dp

    /** 72dp — the onboarding step's icon plate. */
    val iconPlate: Dp = 72.dp

    /** 200dp — the Home hero band height. */
    val hero: Dp = 200.dp

    /** Comfortable cap for content width on wide/landscape layouts. */
    val maxContent: Dp = 640.dp
}
