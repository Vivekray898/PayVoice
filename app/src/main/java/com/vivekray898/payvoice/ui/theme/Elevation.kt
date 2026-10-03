package com.vivekray898.payvoice.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Flat elevation scale.
 *
 * The design direction bans `shadowElevation` outright: shadows render as a
 * grey smudge on low-end panels and cost GPU on every frame they are drawn.
 * Depth is carried by a 1dp hairline plus a tonal step instead
 * (DESIGN.md elevation level 0/1).
 *
 * Use [PvElevation.surface] wherever a card would otherwise float, and
 * [PvElevation.raised] for transient layers (bottom sheet, FAB).
 */
object PvElevation {
    /** No elevation — flat surface against the background. */
    val flat: Dp = 0.dp

    /** Default card: surface reads as distinct purely tonally. */
    val surface: Dp = Spacing.xxs // 2dp tonal

    /** Transient layer — bottom sheet, FAB. Still no shadow. */
    val raised: Dp = Spacing.sm // 8dp tonal

    /** Always 0. Present so callers cannot accidentally invent a shadow. */
    val shadow: Dp = 0.dp
}
