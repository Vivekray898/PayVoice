package com.vivekray898.payvoice.ui.reliability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.service.tts.AnnouncementSpeaker
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.SectionCard
import com.vivekray898.payvoice.ui.components.StatusLine

/**
 * Central troubleshooting screen (spec: RELIABILITY CHECK). One glance shows
 * every dependency: listener access, payment apps, notifications, battery,
 * TTS, Firebase/FCM, device/ROM identity. The button never does nothing.
 */
@Composable
fun ReliabilityScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val fcm by viewModel.fcm.collectAsStateWithLifecycle()
    val tts by viewModel.ttsStatus.collectAsStateWithLifecycle()
    val runtime by viewModel.listenerRuntime.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("PayVoice Reliability", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Close") }
            }
        }

        item(key = "listener") {
            SectionCard(title = "Notification Access") {
                // The grant (persisted system setting) and the live binding are
                // different states — both are shown so the post-Clear-Data
                // mismatch is VISIBLE instead of mysterious.
                StatusLine(
                    runtime.systemGrant,
                    if (runtime.systemGrant) "Enabled in Android settings" else "Not enabled",
                )
                StatusLine(
                    runtime.connected,
                    if (runtime.connected) "Listener connected" else "Listener not connected",
                )
                if (runtime.mismatch) {
                    Text(
                        "Android has granted access but the listener service is " +
                            "not currently bound. This happens after clearing app " +
                            "data or force-stopping. Use Repair to ask Android to " +
                            "rebind (no restart needed).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = { viewModel.repairListener(context) }) {
                        Text("Repair connection")
                    }
                }
                OutlinedButton(onClick = { viewModel.openListenerSettings(context) }) { Text("Open settings") }
            }
        }

        item(key = "capture-channels") {
            SectionCard(title = "Capture channels") {
                // Two-channel architecture: GPay is the only app-notification
                // source; bank payments (Kotak and others) arrive via bank SMS.
                StatusLine(status?.gpay?.installed == true, "GPay Notification Access")
                Text(
                    "Package: ${status?.gpay?.packageName ?: "…"} · " +
                        "Installed: ${if (status?.gpay?.installed == true) "YES" else "NO"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                StatusLine(
                    status?.smsPermissionGranted == true,
                    if (status?.smsPermissionGranted == true) "SMS Backup — available" else "SMS Backup — permission required",
                )
                Text(
                    "Bank payments (Kotak and others) arrive via bank SMS. " +
                        "No bank app notification access is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status?.isXiaomiFamily == true) {
                    Text(
                        "Xiaomi family detected — also verify Autostart and " +
                            "Background activity in the Security app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "notifications") {
            SectionCard(title = "PayVoice Notifications") {
                StatusLine(status?.notificationsEnabled == true, if (status?.notificationsEnabled == true) "Enabled" else "Not allowed")
                OutlinedButton(onClick = {
                    SetupNotifications.ensureChannels(context)
                    viewModel.openAppNotificationSettings(context)
                }) { Text("Open settings") }
            }
        }

        item(key = "battery") {
            SectionCard(title = "Battery Optimization") {
                StatusLine(status?.batteryExempt == true, if (status?.batteryExempt == true) "Unrestricted" else "Restricted")
                OutlinedButton(onClick = { viewModel.fixBattery(context) }) { Text("Fix") }
            }
        }

        item(key = "sms") {
            SectionCard(title = "SMS Backup") {
                StatusLine(
                    status?.smsPermissionGranted == true,
                    if (status?.smsPermissionGranted == true) "Enabled" else "Not granted",
                )
                Text(
                    "PayVoice uses incoming bank SMS messages as a backup when " +
                        "Google Pay or banking-app notifications are unavailable. " +
                        "SMS contents are processed locally to identify payment " +
                        "notifications — never uploaded, never read from your inbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status?.smsPermissionGranted != true) {
                    OutlinedButton(onClick = { viewModel.openAppDetailsSettings(context) }) {
                        Text("Grant in settings")
                    }
                }
            }
        }

        item(key = "tts") {
            SectionCard(title = "Text-to-Speech") {
                val ok = tts == AnnouncementSpeaker.Status.READY || tts == AnnouncementSpeaker.Status.SPEAKING
                StatusLine(ok, when (tts) {
                    AnnouncementSpeaker.Status.READY -> "Google TTS available"
                    AnnouncementSpeaker.Status.SPEAKING -> "Speaking"
                    AnnouncementSpeaker.Status.INITIALIZING -> "Initializing…"
                    else -> "Not available"
                })
                OutlinedButton(onClick = { viewModel.speakTest() }) { Text("Test announcement") }
            }
        }

        item(key = "firebase") {
            SectionCard(title = "Push delivery (FCM)") {
                StatusLine(fcm.tokenAvailable, if (fcm.tokenAvailable) "Push transport ready · token available" else "Token unavailable")
                if (fcm.tokenAvailable) {
                    Text(
                        "Token: ${fcm.tokenPreview ?: "registered (hidden)"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                fcm.error?.let {
                    Text(
                        "Error: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // Honest state: FCM registration is live; the Supabase
                // fcm-gateway edge function is the only sender.
                Text(
                    "Push transport only · events are sent by the Supabase fcm-gateway function",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { viewModel.refreshFcmToken() }) { Text("Refresh token") }
            }
        }

        item(key = "device") {
            SectionCard(title = "Device") {
                Text(
                    "${status?.manufacturer ?: "?"} ${status?.model ?: "?"}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Android: ${status?.androidVersion ?: "?"} (SDK ${android.os.Build.VERSION.SDK_INT})",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "ROM: ${status?.romHint?.ifBlank { "stock (${android.os.Build.DISPLAY})" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item(key = "footer") { Spacer(Modifier.height(24.dp)) }
    }
}
