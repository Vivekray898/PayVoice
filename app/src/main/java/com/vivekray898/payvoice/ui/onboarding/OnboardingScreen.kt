package com.vivekray898.payvoice.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.TextButton
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
import com.vivekray898.payvoice.ui.components.PvPrimaryButton
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.StatusDot
import com.vivekray898.payvoice.ui.components.statusPositive

/**
 * Setup (UI overhaul Phase 3a): ONE primary action, always visible at the
 * bottom (never needs scrolling) + the checklist above it. Statuses refresh
 * on return from system settings (Activity.onResume). All actions call the
 * existing ViewModel methods — no behavior change.
 *
 * Insets: PvScaffold owns the status bar (topBar slot); this screen declares
 * a bottomBar with BOTTOM-only safeDrawing padding so the nav-bar/IME inset
 * is consumed exactly once — the top is already handled by PvScaffold and
 * would double-pad.
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
    val allDone = done == 4
    // The current step is the first incomplete one. Drives "Step X of 4" + highlight.
    val currentStep = when {
        !listenerOk -> 1
        !notifOk -> 2
        !batteryOk -> 3
        else -> 4
    }

    PvScaffold(
        title = "Welcome to PayVoice",
        // Bottom bar = the screen's single primary action + skip. Declared
        // here (not inside PvScaffold) because only some screens have a
        // persistent action row.
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PvPrimaryButton(
                    text = if (allDone) "Finish setup" else "Continue — finish later",
                    onClick = { viewModel.completeOnboarding() },
                )
                if (!allDone) {
                    TextButton(
                        onClick = { viewModel.completeOnboarding() },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            "Skip for now",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier.fillMaxWidth(),
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
                            "Step $currentStep of 4",
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
                        "UPI payments announced the moment Google Pay notifies you.",
                    ok = listenerOk,
                    okLabel = "Enabled",
                    pendingLabel = "Not set up",
                    isCurrent = currentStep == 1,
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
                    isCurrent = currentStep == 2,
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
                        isCurrent = currentStep == 3,
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
                    isCurrent = currentStep == 4,
                ) {
                    OutlinedButton(onClick = { viewModel.speakTest() }) {
                        Text("Hear a test")
                    }
                }
            }


            item(key = "footer") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/**
 * One setup checklist step: number/check marker, copy, status, and action,
 * grouped in a card. The CURRENT step gets a subtle primary tint so "what
 * am I doing now?" is answerable at a glance.
 */
@Composable
private fun SetupStep(
    number: Int,
    title: String,
    body: String,
    ok: Boolean,
    okLabel: String,
    pendingLabel: String,
    isCurrent: Boolean,
    action: @Composable () -> Unit,
) {
    PvSection {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (isCurrent && !ok) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            tonalElevation = if (isCurrent && !ok) 0.dp else 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(16.dp),
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
                    // Pending: a numbered circle — NOT a checkmark, which would
                    // read as "done" (accessibility/honesty fix).
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
                            color = if (ok) {
                                statusPositive()
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    action()
                }
            }
        }
    }
}
