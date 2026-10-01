package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vivekray898.payvoice.service.status.DeviceStatusMonitor

/**
 * Shared grant observer for permission-gated wizard steps (the auto-advance
 * contract). MainActivity re-reads device state on every ON_RESUME
 * (`refreshStatus()`), so observing a predicate over the status flow here
 * is equivalent to re-checking on Lifecycle.Event.ON_RESUME — with exactly
 * ONE step-local advance. (A per-step LifecycleEventObserver next to this
 * would double-fire the driver's `step += 1` and skip steps — the exact
 * regression this replaces.)
 *
 * Semantics:
 *  - false→true transition → [onContinue] fires once (auto-advance).
 *  - composed already-granted → NO auto-advance; steps render "Continue"
 *    and tap through. Stable for back-navigation and process restore.
 */
@Composable
fun PermissionStep(
    status: DeviceStatusMonitor.Snapshot?,
    isGranted: (DeviceStatusMonitor.Snapshot?) -> Boolean,
    onContinue: () -> Unit,
) {
    val granted = isGranted(status)
    var seen by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(granted) {
        val was = seen
        seen = granted
        if (granted && was == false) onContinue()
    }
}
