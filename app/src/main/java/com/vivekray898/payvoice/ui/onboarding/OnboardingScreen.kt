package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvIconButton
import com.vivekray898.payvoice.ui.components.PvIconPlate
import com.vivekray898.payvoice.ui.components.PvProgressBar
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Setup wizard (DESIGN.md rebuild, Phase 3a): one decision per screen, a
 * 4dp progress bar, bottom-anchored pill actions, and grant auto-advance.
 *
 * State: step + local role copy are screen-local [rememberSaveable]; the
 * ROLE SELECTION is persisted through the existing setRole at the Role
 * step, completion through completeOnboarding at Done. Permission steps
 * auto-advance via [PermissionStep] (the false-to-true grant transition,
 * fed by MainActivity.onResume refreshStatus) — never by Skip.
 */
@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    var step by rememberSaveable { mutableIntStateOf(WizardStep.WELCOME) }
    var role by rememberSaveable { mutableStateOf<DeviceRole?>(null) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val onBack: () -> Unit = { step = (step - 1).coerceAtLeast(WizardStep.WELCOME) }
    val onNext: () -> Unit = { step += 1 }

    when (step) {
        WizardStep.WELCOME -> WelcomeStep(onNext = onNext)
        WizardStep.ROLE -> RoleStep(
            onRole = { chosen ->
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

/** Numbered steps for the progress indicator (welcome/ready excluded). */
internal object WizardStep {
    const val WELCOME = 0
    const val ROLE = 1
    const val ACCESS = 2
    const val NOTIF_PERMISSION = 3
    const val BATTERY = 4
    const val READY = 5

    /** The highest step number shown as "Step N of 4" (role..battery = 1..4). */
    const val LAST_NUMBERED = 4
}

/**
 * The wizard page shell: back arrow, 4dp progress bar + "Step N of M"
 * label, icon circle, title, body, and the step's actions bottom-anchored
 * (primary pill + optional secondary), always visible without scrolling.
 */
@Composable
internal fun StepScaffold(
    step: Int,
    onBack: (() -> Unit)?,
    icon: ImageVector,
    title: String,
    body: String,
    primaryButton: @Composable () -> Unit,
    secondaryAction: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    PvScaffold(
        topBar = {
            if (onBack != null) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth(),
                ) {
                    PvIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
            ) {
                primaryButton()
                if (secondaryAction != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    secondaryAction()
                }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = Spacing.xl),
        ) {
            if (step in WizardStep.ROLE..WizardStep.BATTERY) {
                PvProgressBar(progress = step.toFloat() / WizardStep.LAST_NUMBERED)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Step $step of ${WizardStep.LAST_NUMBERED}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.xxl))

            PvIconPlate(icon = icon)
            Spacer(Modifier.height(Spacing.xl))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content?.invoke()
        }
    }
}
