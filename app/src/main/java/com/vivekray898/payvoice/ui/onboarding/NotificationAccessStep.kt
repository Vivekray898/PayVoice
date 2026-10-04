package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvCodeField
import com.vivekray898.payvoice.ui.components.PvPrimaryButton
import com.vivekray898.payvoice.ui.components.PvSupportingText
import com.vivekray898.payvoice.ui.components.PvTextButton
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Wizard step: split by role.
 *  • Owner — notification-listener access; auto-advances on grant.
 *  • Employee — pairing-code entry using the existing joinOwner action and
 *    its error mapping; advances on JoinState.Success (async one-shot).
 */
@Composable
fun NotificationAccessStep(
    viewModel: MainViewModel,
    isEmployee: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    if (!isEmployee) {
        val status by viewModel.status.collectAsStateWithLifecycle()
        val granted = status?.listenerEnabled == true
        PermissionStep(
            status = status,
            isGranted = { it?.listenerEnabled == true },
            onContinue = onNext,
        )
        val context = androidx.compose.ui.platform.LocalContext.current
        StepScaffold(
            step = WizardStep.ACCESS,
            onBack = onBack,
            icon = Icons.Filled.Hearing,
            title = "Let PayVoice hear your payments",
            body = "PayVoice reads payment notifications from Google Pay and " +
                "announces them. It never reads anything else.",
            primaryButton = {
                PvPrimaryButton(
                    text = if (granted) "Continue" else "Allow access",
                    onClick = {
                        if (granted) onNext() else viewModel.openListenerSettings(context)
                    },
                )
            },
            secondaryAction = {
                PvTextButton(
                    text = "Skip for now",
                    onClick = onNext,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    } else {
        val joinState by viewModel.joinState.collectAsStateWithLifecycle()
        var code by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(joinState) {
            if (joinState is MainViewModel.JoinState.Success) onNext()
        }
        StepScaffold(
            step = WizardStep.ACCESS,
            onBack = onBack,
            icon = Icons.Filled.Link,
            title = "Enter your pairing code",
            body = "Ask your owner for the code from their PayVoice app.",
            primaryButton = {
                PvPrimaryButton(
                    text = "Pair",
                    onClick = { viewModel.joinOwner(code) },
                    enabled = code.isNotBlank() && joinState !is MainViewModel.JoinState.Joining,
                )
            },
            secondaryAction = {
                // onNext, NOT onBack: this used to decrement the wizard step, which
                // sent the employee back to the ROLE picker — and picking the role
                // again just returned here. An employee without a code in hand was
                // trapped in that loop and could never reach the app at all; they
                // pair later from Home ("Enter code"), which is reachable whether
                // or not this step was skipped. The owner branch above already
                // skipped forward for the same reason.
                PvTextButton(
                    text = "Skip for now",
                    onClick = onNext,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            Spacer(Modifier.height(Spacing.xl))
            val failed = joinState as? MainViewModel.JoinState.Failed
            PvCodeField(
                value = code,
                onValueChange = { code = it },
                enabled = joinState !is MainViewModel.JoinState.Joining,
                isError = failed != null,
                supportingText = failed?.message,
            )
            if (joinState is MainViewModel.JoinState.Joining) {
                PvSupportingText(text = "Connecting…")
            }
        }
    }
}
