package com.vivekray898.payvoice.ui.reliability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.service.tts.AnnouncementSpeaker
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.IconTile
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSecondaryButton
import com.vivekray898.payvoice.ui.components.PvTopBar
import com.vivekray898.payvoice.ui.components.StatusIcon
import com.vivekray898.payvoice.ui.components.StatusPill
import com.vivekray898.payvoice.ui.components.StatusTone
import com.vivekray898.payvoice.ui.components.statusToneOf
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Reliability (DESIGN.md rebuild, Phase 3g): a "N of M checks passing"
 * status pill, then check rows — leading status icon circle, title +
 * one-line description, trailing pill Fix button when not satisfied.
 * The button never does nothing; the check semantics are unchanged.
 */
@Composable
fun ReliabilityScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val fcm by viewModel.fcm.collectAsStateWithLifecycle()
    val tts by viewModel.ttsStatus.collectAsStateWithLifecycle()
    val runtime by viewModel.listenerRuntime.collectAsStateWithLifecycle()

    val ttsOk = tts == AnnouncementSpeaker.Status.READY || tts == AnnouncementSpeaker.Status.SPEAKING

    data class Check(val ok: Boolean, val title: String, val description: String, val fix: (() -> Unit)?)

    val checks = listOf(
        Check(
            ok = runtime.connected,
            title = "Notification access",
            description = if (runtime.connected) "Listener connected" else "Grant access so PayVoice can hear payments",
            fix = if (runtime.connected) null else {
                { viewModel.openListenerSettings(context) }
            },
        ),
        Check(
            ok = status?.gpay?.installed == true,
            title = "Google Pay",
            description = "The payment app PayVoice listens to",
            fix = null,
        ),
        Check(
            ok = status?.notificationsEnabled == true,
            title = "App notifications",
            description = "Needed for the wake notification that keeps announcements reliable",
            fix = {
                SetupNotifications.ensureChannels(context)
                viewModel.openAppNotificationSettings(context)
            },
        ),
        Check(
            ok = status?.batteryExempt == true,
            title = "Battery optimization",
            description = if (status?.batteryExempt == true) "Unrestricted" else "Exempt PayVoice so Android never sleeps it",
            fix = if (status?.batteryExempt == true) null else {
                { viewModel.fixBattery(context) }
            },
        ),
        Check(
            ok = ttsOk,
            title = "Text-to-speech",
            description = when (tts) {
                AnnouncementSpeaker.Status.READY -> "Google TTS available"
                AnnouncementSpeaker.Status.SPEAKING -> "Speaking"
                AnnouncementSpeaker.Status.INITIALIZING -> "Initializing…"
                else -> "No TTS engine available on this device"
            },
            fix = if (ttsOk) {
                { viewModel.speakTest() }
            } else null,
        ),
    )
    val passCount = checks.count { it.ok }
    val allPass = passCount == checks.size

    PvScaffold(
        topBar = { PvTopBar(title = "Reliability", onBack = onBack) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = Spacing.xxl + inner.calculateBottomPadding(),
            ),
        ) {
            item(key = "summary") {
                Row(
                    Modifier.padding(top = Spacing.sm, bottom = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(
                        text = if (allPass) "All ${checks.size} checks passing" else "$passCount of ${checks.size} checks passing",
                        tone = if (allPass) StatusTone.Success else StatusTone.Warning,
                    )
                }
            }
            items(checks.size) { index ->
                val check = checks[index]
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = Spacing.xxs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.md),
                ) {
                    Row(
                        Modifier.padding(Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        IconTile(
                            icon = Icons.Filled.Check,
                            container = MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = if (check.ok) 0.5f else 0.25f,
                            ),
                            tile = Spacing.xxl, // 40dp structural
                            iconSize = Spacing.xl - Spacing.xs,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(check.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                check.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!check.ok && check.fix != null) {
                            PvSecondaryButton(
                                text = "Fix",
                                onClick = check.fix,
                                modifier = Modifier.fillMaxWidth(0.32f),
                            )
                        }
                    }
                }
            }
            item(key = "note") {
                val runtimeState = runtime
                if (runtimeState.mismatch) {
                    Column {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "Android has granted access but the listener service is not " +
                                "currently bound. Use Repair below to rebind it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        PvSecondaryButton(
                            text = "Repair connection",
                            onClick = { viewModel.repairListener(context) },
                        )
                    }
                }
                if (fcm.error != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Push error: ${fcm.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Checks reflect live state; they re-run whenever this screen resumes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.lg),
                )
            }
        }
    }
}
