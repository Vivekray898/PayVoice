package com.vivekray898.payvoice.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Motion contract (added by the redesign; see `docs/DESIGN_DIRECTION.md`).
 *
 * The reference `DESIGN.md` has no motion section, and the app targets 1–2 GB
 * devices. The rules:
 *
 *  - every transition is **<= 200ms** with `FastOutSlowInEasing`;
 *  - **no blur, no parallax, no shimmer loops, no infinite spinners** —
 *    loading states use skeletons instead;
 *  - everything reads the platform animator scale, so a user with animations
 *    turned off in system settings gets an **instant** UI with no extra work.
 *
 * Screens never call `tween(...)` directly; they take [PvMotion.durationMs].
 */
@Immutable
object PvMotion {

    /** Press/ripple feedback. */
    const val FAST_MS: Int = 120

    /** The default for state changes, list entry, banner expand. Ceiling. */
    const val STANDARD_MS: Int = 200

    /** Bottom-sheet slide. Same ceiling — nothing is allowed to outlast it. */
    const val SHEET_MS: Int = 200

    /** Treat every transition as instant (reduced motion). */
    const val INSTANT_MS: Int = 0

    /**
     * A [FiniteAnimationSpec] for the given duration, clamped to the ceiling.
     * Type parameter is inferred by the caller, so this works for `Dp`,
     * `Float`, `Color` and `Dp.Offset` alike.
     */
    fun <T> spec(durationMs: Int = STANDARD_MS): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs.coerceAtMost(STANDARD_MS), easing = FastOutSlowInEasing)

    /**
     * True when the platform animator duration scale is 0 — the system-wide
     * "remove animations" setting. Read once per composition context; a change
     * requires the composable to be recreated, which matches how the setting
     * itself behaves.
     */
    @Composable
    fun reducedMotion(): Boolean {
        val context = LocalContext.current
        return remember(context) {
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
            }.getOrDefault(false)
        }
    }

    /**
     * The duration a screen should ask for right now: 0 under reduced motion,
     * otherwise [requestedMs] clamped to [STANDARD_MS].
     */
    @Composable
    fun durationMs(requestedMs: Int = STANDARD_MS): Int =
        if (reducedMotion()) INSTANT_MS else requestedMs.coerceAtMost(STANDARD_MS)
}
