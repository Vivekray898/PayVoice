package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vivekray898.payvoice.service.status.DeviceStatusMonitor

/**
 * Shared auto-advance engine for permission-gated wizard steps (Bug 1 fix).
 *
 * MainActivity re-reads device state on every ON_RESUME (`refreshStatus()`),
 * so observing a grant predicate over the `status` flow here is equivalent
 * to re-checking the permission on Lifecycle.Event.ON_RESUME — with exactly
 * ONE step-local auto-advance. (A per-step LifecycleEventObserver next to
 * the existing LaunchedEffect pattern would double-fire `onNext`, which is
 * `step += 1` and therefore skips steps.)
 *
 * Semantics:
 *  - Grant transition false→true  → [onNext] fires once (auto-advance).
 *  - Step composed with grant already true → NO auto-advance; the step's
 *    primary button reads "Continue" and taps through. This keeps
 *    back-navigation and restore-from-process-death stable.
 */
@Composable
fun PermissionAutoAdvance(
    status: DeviceStatusMonitor.Snapshot?,
    isGranted: (DeviceStatusMonitor.Snapshot?) -> Boolean,
    onNext: () -> Unit,
) {
    val granted = isGranted(status)
    var seen by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(granted) {
        val was = seen
        seen = granted
        if (granted && was == false) onNext()
    }
}
