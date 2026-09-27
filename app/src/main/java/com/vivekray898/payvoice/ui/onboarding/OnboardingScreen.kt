package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.R
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.SectionCard
import com.vivekray898.payvoice.ui.components.StatusLine

/**
 * Parent installation wizard (spec §35). Progress-gated, honest copy, no
 * fake completions: each step reflects real system state.
 */
@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val allGranted = status.listenerEnabled && status.notificationsEnabled && status.batteryExempt

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Welcome to PayVoice", style = MaterialTheme.typography.headlineMedium)
        Text(
            "This phone will listen for payment notifications and announce them.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(title = "Step 1 · Notification access") {
            Text(
                "Android requires explicit permission to read payment app " +
                    "notifications. PayVoice reads only Google Pay and Kotak, " +
                    "and keeps everything on this phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            StatusLine(status.listenerEnabled, "Listener access granted")
            Button(onClick = { viewModel.launchIntent(context, viewModel.listenerSettingsIntent()) }) {
                Text(if (status.listenerEnabled) "Open settings again" else "Open Settings")
            }
        }

        SectionCard(title = "Step 2 · App notifications") {
            StatusLine(status.notificationsEnabled, "PayVoice can post notifications")
            OutlinedButton(
                onClick = { viewModel.launchIntent(context, viewModel.appNotificationIntent()) }
            ) { Text("Check") }
        }

        SectionCard(title = "Step 3 · Unrestricted battery") {
            Text(
                "So announcements are never delayed, allow PayVoice to run " +
                    "without battery optimization.",
                style = MaterialTheme.typography.bodyMedium,
            )
            StatusLine(status.batteryExempt, "Battery optimization disabled")
            OutlinedButton(
                onClick = { viewModel.launchIntent(context, viewModel.batteryIntent()) }
            ) { Text("Allow") }
        }

        SectionCard(title = "Step 4 · Payment sources") {
            Text(
                "Google Pay is detected automatically. Kotak is verified later " +
                    "with a real notification (Diagnostics → Capture).",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (settings.gpayEnabled) "Google Pay · enabled" else "Google Pay · off",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard(title = "Step 5 · Test announcement") {
            Text(
                "Play a sample announcement to confirm sound works on this phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { viewModel.speakTest() }) {
                Text("🔊 Play test announcement")
            }
        }

        val ready = allGranted
        Button(
            onClick = { viewModel.completeOnboarding() },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (ready) "Finish setup" else "Grant the steps above to continue")
        }
        Spacer(Modifier.height(24.dp))
    }
}
