package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Wizard step 3 — split by role:
 *  • Owner: notification-listener access (auto-advances when the grant
 *    lands — the driver re-checks on ON_RESUME via refreshStatus()).
 *  • Employee: pairing-code entry using the EXISTING joinOwner action and
 *    its error mapping; auto-advances on JoinState.Success.
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
        // Bug 1 fix: shared auto-advance (false→true only) instead of a raw
        // LaunchedEffect that also fires when the step is composed already-granted
        // (which skipped steps on back-navigation).
        PermissionAutoAdvance(
            status = status,
            isGranted = { it?.listenerEnabled == true },
            onNext = onNext,
        )
        var showWhy by rememberSaveable { mutableStateOf(false) }
        val context = LocalContext.current
        WizardPage(
            icon = Icons.Filled.Hearing,
            heading = "Let PayVoice hear your payments",
            body = "PayVoice listens for payment notifications from Google Pay " +
                "and announces them out loud.",
            primaryLabel = if (granted) "Continue" else "Allow access",
            onPrimary = {
                if (granted) onNext() else viewModel.openListenerSettings(context)
            },
            linkLabel = "Why do I need this?",
            onLink = { showWhy = !showWhy },
        ) {
            AnimatedVisibility(visible = showWhy) {
                Text(
                    "Android only allows ONE app per phone to read payment " +
                        "notifications. Granting this access is how PayVoice can " +
                        "detect a UPI payment the moment Google Pay shows it — " +
                        "nothing is uploaded; the announcement is generated on-device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }
    } else {
        val joinState by viewModel.joinState.collectAsStateWithLifecycle()
        var code by rememberSaveable { mutableStateOf("") }
        // Join is an async one-shot (not a resumed system setting): keep the
        // LaunchedEffect on the JoinState itself.
        LaunchedEffect(joinState) {
            if (joinState is MainViewModel.JoinState.Success) onNext()
        }
        WizardPage(
            icon = Icons.Filled.Hearing,
            heading = "Enter your pairing code",
            body = "Ask your owner for the code shown in their PayVoice app.",
            primaryLabel = "Pair",
            primaryEnabled = code.isNotBlank() &&
                joinState !is MainViewModel.JoinState.Joining,
            onPrimary = { viewModel.joinOwner(code) },
            linkLabel = "Skip for now",
            onLink = onBack,
        ) {
            Spacer(Modifier.size(Spacing.lg))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("Pairing code") },
                singleLine = true,
                enabled = joinState !is MainViewModel.JoinState.Joining,
                modifier = Modifier.fillMaxWidth(),
            )
            when (val s = joinState) {
                is MainViewModel.JoinState.Joining -> Text(
                    "Connecting…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is MainViewModel.JoinState.Failed -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Unit
            }
        }
    }
}
