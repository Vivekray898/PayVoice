package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import com.vivekray898.payvoice.ui.MainViewModel

/** Wizard step 1 — welcome. No skip. */
@Composable
fun WelcomeStep(onNext: () -> Unit) {
    WizardPage(
        icon = Icons.Filled.VolumeUp,
        heading = "Welcome to PayVoice",
        body = "Announce every UPI payment instantly — on your phone and your team's.",
        primaryLabel = "Get started",
        onPrimary = onNext,
    )
}
