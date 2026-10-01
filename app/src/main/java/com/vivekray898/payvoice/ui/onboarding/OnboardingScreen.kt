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
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Setup wizard (premium UI v2, Phase 2): ONE decision per screen, a progress
 * bar + back arrow, and a final ready screen. Replaces the flat checklist.
 *
 * State is screen-local [rememberSaveable]; no new ViewModel fields. Every
 * action calls the EXISTING ViewModel methods (openListenerSettings,
 * fixBattery, speakTest, setRole, joinOwner, completeOnboarding) — the
 * completion path (onboardingComplete flag → NavHost navigation) is
 * unchanged. Grant detection: MainActivity.refreshStatus() on every ON_RESUME
 * feeds the status flows this screen already observes, so steps auto-advance
 * on return from system settings.
 */
@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    var step by rememberSaveable { mutableIntStateOf(WizardStep.WELCOME) }
    var role by rememberSaveable { mutableStateOf<DeviceRole?>(null) }

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
                    role = chosen
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
