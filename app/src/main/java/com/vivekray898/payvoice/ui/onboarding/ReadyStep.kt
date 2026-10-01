package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Wizard step 6 — ready. Preview via the EXISTING speakTest action; Done
 * completes onboarding (unchanged completion path → NavHost navigates Home).
 */
@Composable
fun ReadyStep(viewModel: MainViewModel, onDone: () -> Unit) {
    WizardPage(
        icon = Icons.Filled.CheckCircle,
        heading = "You're all set",
        body = "PayVoice will now announce every payment out loud. Tap below " +
            "to hear a preview.",
        primaryLabel = "Hear a preview",
        onPrimary = { viewModel.speakTest() },
        linkLabel = "Done",
        onLink = onDone,
    )
}
