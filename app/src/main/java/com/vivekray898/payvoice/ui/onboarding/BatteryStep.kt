package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.ui.MainViewModel

/**
 * Wizard step 5 — battery optimization exemption. Auto-advances when the
 * exemption lands (grant re-checked on ON_RESUME).
 */
@Composable
fun BatteryStep(viewModel: MainViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val exempt = status?.batteryExempt == true
    // Bug 1 fix: shared auto-advance (false→true only) — composed-exempt no
    // longer auto-advances (it skipped the step on back-navigation).
    PermissionAutoAdvance(
        status = status,
        isGranted = { it?.batteryExempt == true },
        onNext = onNext,
    )
    val context = LocalContext.current

    WizardPage(
        icon = Icons.Filled.BatterySaver,
        heading = "Keep PayVoice awake",
        body = "Android may put PayVoice to sleep and delay announcements. " +
            "Exempting PayVoice keeps it running.",
        primaryLabel = if (exempt) "Continue" else "Allow background access",
        onPrimary = { viewModel.fixBattery(context) },
        linkLabel = "Skip for now",
        onLink = onNext,
    )
}
