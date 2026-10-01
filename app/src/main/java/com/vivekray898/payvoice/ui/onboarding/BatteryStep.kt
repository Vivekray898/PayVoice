package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvPrimaryButton

/**
 * Wizard step: battery optimization exemption. Auto-advances when the
 * exemption lands; skippable.
 */
@Composable
fun BatteryStep(viewModel: MainViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val exempt = status?.batteryExempt == true
    val context = LocalContext.current

    PermissionStep(
        status = status,
        isGranted = { it?.batteryExempt == true },
        onContinue = onNext,
    )

    StepScaffold(
        step = WizardStep.BATTERY,
        onBack = onBack,
        icon = Icons.Filled.BatteryChargingFull,
        title = "Keep PayVoice awake",
        body = "Android may put PayVoice to sleep and delay announcements. " +
            "Exempting PayVoice keeps it running.",
        primaryButton = {
            PvPrimaryButton(
                text = if (exempt) "Continue" else "Allow background access",
                onClick = { if (exempt) onNext() else viewModel.fixBattery(context) },
            )
        },
        secondaryAction = {
            TextButton(onClick = onNext) {
                Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
