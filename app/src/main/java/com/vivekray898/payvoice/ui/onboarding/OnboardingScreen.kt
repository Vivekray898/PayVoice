package com.vivekray898.payvoice.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.service.tts.AnnouncementSpeaker
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvDivider
import com.vivekray898.payvoice.ui.components.PvRow
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.StatusDot
import com.vivekray898.payvoice.ui.components.statusNegative
import com.vivekray898.payvoice.ui.components.statusPositive

/**
 * Setup (production redesign): the same four setup mechanisms and statuses,
 * presented as a calm checklist in human language. Statuses refresh on
 * return from system settings (Activity.onResume). All actions call the
 * existing ViewModel methods.
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

    PvScaffold(title = "Welcome to PayVoice") {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "intro") {
                PvSection {
                    Text(
                        "Let's get your phone ready to announce payments out loud.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { done / 4f },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$done of 4",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- 1. Notification listener access (reads GPay only) ----
            item(key = "step-listener") {
                SetupStep(
                    number = 1,
                    title = "Payment notification access",
                    body = "Lets PayVoice hear payment notifications from Google Pay. " +
                        "Bank payments arrive by SMS — no extra bank access needed.",
                    ok = listenerOk,
                    okLabel = "Enabled",
                    pendingLabel = "Not set up",
                ) {
                    Button(onClick = { viewModel.openListenerSettings(context) }) {
                        Text(if (listenerOk) "Review" else "Allow access")
                    }
                }
            }

            // ---- 2. PayVoice's own notifications ----
            item(key = "step-notif") {
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { _ -> viewModel.refreshStatus() }
                SetupStep(
                    number = 2,
                    title = "PayVoice notifications",
                    body = "Shows device status. This is separate from reading payment apps.",
                    ok = notifOk,
                    okLabel = "Enabled",
                    pendingLabel = "Not set up",
                ) {
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
                    }) { Text(if (notifOk) "Review" else "Allow") }
                }
            }

            // ---- 3. Battery optimization ----
            item(key = "step-battery") {
                Column {
                    SetupStep(
                        number = 3,
                        title = "Run in background",
                        body = "Keeps announcements working when the screen is off.",
                        ok = batteryOk,
                        okLabel = "Unrestricted",
                        pendingLabel = "Restricted",
                    ) {
                        Button(onClick = { viewModel.fixBattery(context) }) {
                            Text(if (batteryOk) "Review" else "Allow")
                        }
                    }
                    if (status?.isXiaomiFamily == true) {
                        PvSection {
                            Text(
                                "Xiaomi tip: also allow Autostart in the Security app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ---- 4. Text-to-speech ----
            item(key = "step-tts") {
                SetupStep(
                    number = 4,
                    title = "Voice",
                    body = "The voice that speaks your payments.",
                    ok = ttsOk,
                    okLabel = "Ready",
                    pendingLabel = "Preparing…",
                ) {
                    OutlinedButton(onClick = { viewModel.speakTest() }) {
                        Text("Hear a test")
                    }
                }
            }

            // ---- 5. SMS backup (optional, strongly recommended) ----
            item(key = "step-sms") {
                val smsPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { _ -> viewModel.refreshStatus() }
                SetupStep(
                    number = 5,
                    title = "Offline payment backup (SMS)",
                    body = "When Google Pay notifications don't arrive (no internet, " +
                        "notifications off), the bank's SMS still confirms the payment. " +
                        "Read locally only — never uploaded.",
                    ok = status?.smsPermissionGranted == true,
                    okLabel = "Enabled",
                    pendingLabel = "Recommended",
                ) {
                    if (status?.smsPermissionGranted == true) {
                        OutlinedButton(onClick = { viewModel.refreshStatus() }) {
                            Text("Review")
                        }
                    } else {
                        OutlinedButton(onClick = {
                            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        }) {
                            Text("Allow SMS")
                        }
                    }
                }
            }

            item(key = "finish") {
                PvSection {
                    PvDivider()
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.completeOnboarding() },
                        enabled = done >= 3,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (done == 4) "Finish setup" else "Continue — finish later")
                    }
                }
            }

            item(key = "footer") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** One setup checklist step: number/check marker, copy, status, and action. */
@Composable
private fun SetupStep(
    number: Int,
    title: String,
    body: String,
    ok: Boolean,
    okLabel: String,
    pendingLabel: String,
    action: @Composable () -> Unit,
) {
    PvSection {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (ok) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = statusPositive(),
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                // Pending: a numbered outline circle — NOT a checkmark, which
                // would read as "done" (accessibility/honesty fix).
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$number", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusDot(ok)
                    Text(
                        if (ok) okLabel else pendingLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) statusPositive() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                action()
            }
        }
    }
}
