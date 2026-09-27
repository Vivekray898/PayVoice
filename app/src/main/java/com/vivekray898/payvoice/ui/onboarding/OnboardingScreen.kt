package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.service.tts.AnnouncementSpeaker
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.SectionCard
import com.vivekray898.payvoice.ui.components.StatusLine

/**
 * Parent setup wizard (spec: CURRENT APP FLOW). Four separate cards for four
 * separate mechanisms — Notification Listener access, POST_NOTIFICATIONS,
 * battery optimization, TTS — plus an X/4 progress summary. Statuses refresh
 * automatically when returning from system settings (Activity.onResume).
 */
@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val tts by viewModel.ttsStatus.collectAsStateWithLifecycle()

    val listenerOk = status?.listenerEnabled == true
    val notifOk = status?.notificationsEnabled == true
    val batteryOk = status?.batteryExempt == true
    val ttsOk = tts == AnnouncementSpeaker.Status.READY || tts == AnnouncementSpeaker.Status.SPEAKING
    val done = listOf(listenerOk, notifOk, batteryOk, ttsOk).count { it }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("PayVoice", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Payment Announcer",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Let's make your phone ready.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- Card 1: Notification Listener access (READ GPay only) ----
        SectionCard(title = "1. Payment notification access") {
            Text(
                "Lets PayVoice read payment notifications from Google Pay. Bank " +
                    "payments (Kotak and others) arrive via bank SMS instead — this " +
                    "is separate from normal notification permission.",
                style = MaterialTheme.typography.bodyMedium,
            )
            StatusLine(listenerOk, if (listenerOk) "Notification access enabled" else "Not configured")
            Button(onClick = { viewModel.openListenerSettings(context) }) {
                Text(if (listenerOk) "Open settings again" else "Enable")
            }
        }

        // ---- Card 2: PayVoice's own notifications (POST_NOTIFICATIONS) ----
        // Channels are created BEFORE the permission request (spec requirement).
        val notifPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ -> viewModel.refreshStatus() }

        SectionCard(title = "2. PayVoice notifications") {
            Text(
                "Allows PayVoice to show device status and service notifications. " +
                    "This does not control reading payment apps.",
                style = MaterialTheme.typography.bodyMedium,
            )
            StatusLine(notifOk, if (notifOk) "Notifications enabled" else "Not configured")
            Button(onClick = {
                SetupNotifications.ensureChannels(context)
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.openAppNotificationSettings(context)
                }
            }) { Text(if (notifOk) "Open settings again" else "Enable") }
            if (notifOk) {
                OutlinedButton(onClick = { viewModel.postSetupNotification(context) }) {
                    Text("Send setup notification")
                }
            }
        }

        // ---- Card 3: Battery optimization (background reliability) ----
        SectionCard(title = "3. Background reliability") {
            Text(
                "Allows PayVoice to continue working reliably when the screen " +
                    "is off. Opens Android battery settings with safe fallbacks.",
                style = MaterialTheme.typography.bodyMedium,
            )
            StatusLine(batteryOk, if (batteryOk) "Unrestricted" else "Restricted")
            Button(onClick = { viewModel.fixBattery(context) }) {
                Text(if (batteryOk) "Open battery settings" else "Fix")
            }
            if (status?.isXiaomiFamily == true) {
                Text(
                    "Xiaomi device: after the battery step, also allow Autostart " +
                        "and Background activity in Security app settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Card 4: Text-to-Speech ----
        SectionCard(title = "4. Text to speech") {
            val label = when (tts) {
                AnnouncementSpeaker.Status.READY, AnnouncementSpeaker.Status.SPEAKING -> "Google TTS"
                AnnouncementSpeaker.Status.INITIALIZING -> "Initializing…"
                AnnouncementSpeaker.Status.ERROR, AnnouncementSpeaker.Status.UNAVAILABLE -> "Not available"
            }
            Text("Engine: $label")
            StatusLine(ttsOk, if (ttsOk) "Ready" else "Waiting")
            OutlinedButton(onClick = { viewModel.speakTest() }) { Text("Test") }
        }

        SectionCard(title = "Setup status") {
            Text(
                "$done / 4 complete",
                style = MaterialTheme.typography.titleLarge,
            )
            if (done == 4) {
                Text(
                    "Everything is ready.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = { viewModel.simulate(PaymentSource.GOOGLE_PAY) }) {
                    Text("Test Payment Detection")
                }
            }
        }

        OutlinedButton(
            onClick = { viewModel.completeOnboarding() },
            enabled = done >= 3,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (done == 4) "Finish setup" else "Continue anyway (can finish later)")
        }
        Spacer(Modifier.height(24.dp))
    }
}
