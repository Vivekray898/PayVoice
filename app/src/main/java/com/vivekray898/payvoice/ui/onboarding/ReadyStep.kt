package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvPrimaryButton

/** Wizard step: ready. Preview the voice, then Done completes onboarding. */
@Composable
fun ReadyStep(viewModel: MainViewModel, onDone: () -> Unit) {
    StepScaffold(
        step = WizardStep.READY,
        onBack = null,
        icon = Icons.Filled.CheckCircle,
        title = "You're all set",
        body = "PayVoice will now announce every payment out loud. " +
            "Tap below to hear a preview.",
        primaryButton = {
            PvPrimaryButton(text = "Hear a preview", onClick = { viewModel.speakTest() })
        },
        secondaryAction = {
            TextButton(onClick = onDone) {
                Text("Done", color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}
