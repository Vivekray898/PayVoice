package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Setup wizard (premium UI v2, Phase 2): ONE decision per screen, a progress
 * bar + back arrow, and a final ready screen. Replaces the flat checklist.
 *
 * State: step + local role copy are screen-local [rememberSaveable]; the
 * ROLE SELECTION ITSELF is persisted through the EXISTING ViewModel methods
 * (setRole at the Role step, completeOnboarding at Done) — the same settings
 * store Home reads, so the wizard's choice sticks. Grant detection:
 * MainActivity.refreshStatus() on every ON_RESUME feeds the status flows this
 * screen observes, and the shared PermissionAutoAdvance fires each step's
 * false→true grant transition exactly once.
 */
@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    var step by rememberSaveable { mutableIntStateOf(WizardStep.WELCOME) }
    var role by rememberSaveable { mutableStateOf<DeviceRole?>(null) }

    // Read-only view of the persisted settings: used for defaults (device
    // name) and to steer the wizard when a role already exists.
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val onBack: () -> Unit = {
        step = (step - 1).coerceAtLeast(WizardStep.WELCOME)
    }
    val onNext: () -> Unit = { step += 1 }

    Column(Modifier.padding(vertical = Spacing.md)) {
        if (step != WizardStep.WELCOME && step != WizardStep.READY) {
            WizardHeader(step = step, onBack = onBack)
        }
        when (step) {
            WizardStep.WELCOME -> WelcomeStep(onNext = onNext)
            WizardStep.ROLE -> RoleStep(
                onRole = { chosen ->
                    // Bug 2 fix: persist through the ViewModel (same settings
                    // store Home reads) so the role survives onboarding; the
                    // driver-local copy only steers wizard branching.
                    role = chosen
                    viewModel.setRole(
                        chosen,
                        settings.deviceName.ifBlank {
                            android.os.Build.MODEL ?: if (chosen == DeviceRole.OWNER) "Owner" else "Employee"
                        },
                    )
                    onNext()
                },
            )
            WizardStep.ACCESS -> NotificationAccessStep(
                viewModel = viewModel,
                isEmployee = role == DeviceRole.EMPLOYEE,
                onBack = onBack,
                onNext = onNext,
            )
            WizardStep.NOTIF_PERMISSION -> PermissionsStep(
                viewModel = viewModel,
                onBack = onBack,
                onNext = onNext,
            )
            WizardStep.BATTERY -> BatteryStep(
                viewModel = viewModel,
                onBack = onBack,
                onNext = onNext,
            )
            WizardStep.READY -> ReadyStep(
                viewModel = viewModel,
                onDone = { viewModel.completeOnboarding() },
            )
        }
    }
}

/** Numbered steps for the progress indicator (welcome/ready excluded). */
private object WizardStep {
    const val WELCOME = 0
    const val ROLE = 1
    const val ACCESS = 2
    const val NOTIF_PERMISSION = 3
    const val BATTERY = 4
    const val READY = 5

    /** The highest step number shown as "Step N of 4" (role..battery = 1..4). */
    const val LAST_NUMBERED = 4

    fun label(step: Int): String = when (step) {
        ROLE -> "Step 1 of 4"
        ACCESS -> "Step 2 of 4"
        NOTIF_PERMISSION -> "Step 3 of 4"
        BATTERY -> "Step 4 of 4"
        else -> ""
    }

    fun progress(step: Int): Float = when (step) {
        ROLE -> 0.25f
        ACCESS -> 0.5f
        NOTIF_PERMISSION -> 0.75f
        BATTERY -> 1f
        else -> 0f
    }
}

@Composable
private fun WizardHeader(step: Int, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { WizardStep.progress(step) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            WizardStep.label(step),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
