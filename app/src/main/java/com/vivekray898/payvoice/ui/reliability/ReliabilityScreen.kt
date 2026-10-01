package com.vivekray898.payvoice.ui.reliability

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.service.tts.AnnouncementSpeaker
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.StatusLine
import com.vivekray898.payvoice.ui.components.StatusPill
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Central troubleshooting screen (spec: RELIABILITY CHECK). One glance shows
 * every dependency: listener access, payment apps, notifications, battery,
 * TTS, Firebase/FCM, device/ROM identity. The button never does nothing.
 *
 * Premium pass (Phase 4c): an at-a-glance summary pill ("N of 5 checks
 * passing"), status icons instead of dots, carded sections.
 */
@Composable
fun ReliabilityScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val fcm by viewModel.fcm.collectAsStateWithLifecycle()
    val tts by viewModel.ttsStatus.collectAsStateWithLifecycle()
    val runtime by viewModel.listenerRuntime.collectAsStateWithLifecycle()

    val ttsOk = tts == AnnouncementSpeaker.Status.READY || tts == AnnouncementSpeaker.Status.SPEAKING
    val checks = listOf(
        runtime.connected, // listener actually bound (the state that matters)
        status?.gpay?.installed == true,
        status?.notificationsEnabled == true,
        status?.batteryExempt == true,
        ttsOk,
    )
    val passCount = checks.count { it }
    val allPass = passCount == checks.size

    PvScaffold(title = "Reliability", onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "summary") {
                PvSection {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        StatusPill(
                            text = if (allPass) {
                                "All ${checks.size} checks passing"
                            } else {
                                "$passCount of ${checks.size} checks passing"
                            },
                            ok = if (allPass) true else null,
                        )
                    }
                }
            }

            item(key = "listener") {
                PvSection(title = "Notification access", carded = true) {
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
                PvSection(title = "Capture channels", carded = true) {
                    // GPay is the only capture source (UPI apps only).
                    StatusLine(status?.gpay?.installed == true, "GPay Notification Access")
                    Text(
                        "Package: ${status?.gpay?.packageName ?: "…"} · " +
                            "Installed: ${if (status?.gpay?.installed == true) "YES" else "NO"}",
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
                PvSection(title = "PayVoice notifications", carded = true) {
                    StatusLine(
                        status?.notificationsEnabled == true,
                        if (status?.notificationsEnabled == true) "Enabled" else "Not allowed",
                    )
                    OutlinedButton(onClick = {
                        SetupNotifications.ensureChannels(context)
                        viewModel.openAppNotificationSettings(context)
                    }) { Text("Open settings") }
                }
            }

            item(key = "battery") {
                PvSection(title = "Battery optimization", carded = true) {
                    StatusLine(
                        status?.batteryExempt == true,
                        if (status?.batteryExempt == true) "Unrestricted" else "Restricted",
                    )
                    OutlinedButton(onClick = { viewModel.fixBattery(context) }) { Text("Fix") }
                }
            }

            item(key = "tts") {
                PvSection(title = "Text-to-speech", carded = true) {
                    StatusLine(
                        ttsOk,
                        when (tts) {
                            AnnouncementSpeaker.Status.READY -> "Google TTS available"
                            AnnouncementSpeaker.Status.SPEAKING -> "Speaking"
                            AnnouncementSpeaker.Status.INITIALIZING -> "Initializing…"
                            else -> "Not available"
                        },
                    )
                    OutlinedButton(onClick = { viewModel.speakTest() }) { Text("Test announcement") }
                }
            }

            item(key = "firebase") {
                PvSection(title = "Push delivery (FCM)", carded = true) {
                    StatusLine(
                        fcm.tokenAvailable,
                        if (fcm.tokenAvailable) "Push transport ready · token available" else "Token unavailable",
                    )
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
                PvSection(title = "Device", carded = true) {
                    Text(
                        "${status?.manufacturer ?: "?"} ${status?.model ?: "?"}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Android: ${status?.androidVersion ?: "?"} (SDK ${android.os.Build.VERSION.SDK_INT})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "ROM: ${status?.romHint?.ifBlank { "stock (${android.os.Build.DISPLAY})" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
