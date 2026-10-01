package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.runtime.Composable
import com.vivekray898.payvoice.ui.components.PvPrimaryButton

/** Wizard step: welcome (no back, no skip). */
@Composable
fun WelcomeStep(onNext: () -> Unit) {
    StepScaffold(
        step = WizardStep.WELCOME,
        onBack = null,
        icon = Icons.Filled.Savings,
        title = "Welcome to PayVoice",
        body = "Announce every UPI payment out loud — on your phone and your team's.",
        primaryButton = { PvPrimaryButton(text = "Get started", onClick = onNext) },
    )
}
