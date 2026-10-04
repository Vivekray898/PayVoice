package com.vivekray898.payvoice.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.core.remote.PayVoiceAuth
import com.vivekray898.payvoice.core.remote.RemoteEventSender
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.TraceExportState
import com.vivekray898.payvoice.ui.components.PvCard
import com.vivekray898.payvoice.ui.components.PvEmptyHint
import com.vivekray898.payvoice.ui.components.PvPrimaryButton
import com.vivekray898.payvoice.ui.components.PvSecondaryButton
import com.vivekray898.payvoice.ui.components.StatusDot
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvTopBar
import com.vivekray898.payvoice.ui.components.PvSectionHeader
import com.vivekray898.payvoice.ui.components.PvSupportingText
import com.vivekray898.payvoice.ui.components.StatusLine
import com.vivekray898.payvoice.ui.components.StatusTone
import com.vivekray898.payvoice.ui.components.PvSwitchRow
import com.vivekray898.payvoice.ui.components.statusToneContainer
import com.vivekray898.payvoice.ui.components.statusToneOf
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Local diagnostics (DESIGN.md rebuild, Phase 3f): System / Listener / FCM /
 * Remote / Recent events cards over the existing flows. Captured-notification
 * fields stay (local-only parser tooling); GPay simulation remains
 * debug-only. This data is LOCAL ONLY — never uploaded.
 */
@Composable
fun DiagnosticsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val captured by viewModel.captured.collectAsStateWithLifecycle()
    val logs by viewModel.diagnostics.collectAsStateWithLifecycle()
    val runtime by viewModel.listenerRuntime.collectAsStateWithLifecycle()
    val fcm by viewModel.fcm.collectAsStateWithLifecycle()

    PvScaffold(
        topBar = { PvTopBar(title = "Diagnostics", onBack = onBack) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = inner.calculateTopPadding() + Spacing.sm,
                bottom = Spacing.xxl + inner.calculateBottomPadding(),
            ),
        ) {
            item(key = "system") {
                PvSectionHeader(text = "System")
                DiagCard {
                    InfoRow("Device", "${status?.manufacturer ?: "?"} ${status?.model ?: "?"}")
                    InfoRow("Android", "${status?.androidVersion ?: "?"} (SDK ${android.os.Build.VERSION.SDK_INT})")
                    InfoRow("App", "1.0 · ${if (viewModel.isDebugBuild) "debug" else "release"}")
                    InfoRow("Package", "com.vivekray898.payvoice")
                    PvSecondaryButton(text = "Refresh", onClick = { viewModel.refreshStatus() })
                }
            }

            // An employee phone never captures payments — the owner detects them
            // and pushes over FCM — so listener state is not a fault here. Two
            // permanent red rows for a capability the role does not use is the
            // same misleading warning the health checklist already avoids via
            // HealthRequest.detectsPayments.
            if (settings.role == DeviceRole.OWNER) {
                item(key = "listener") {
                    PvSectionHeader(text = "Listener")
                    DiagCard {
                        StatusLine(runtime.systemGrant, "Access granted in Android settings")
                        StatusLine(runtime.connected, if (runtime.connected) "Listener connected" else "Listener not connected")
                        if (runtime.mismatch) {
                            Text(
                                "Android granted access but the service is not bound. " +
                                    "Use Reliability → Repair to rebind.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            } else {
                item(key = "listener-na") {
                    PvSectionHeader(text = "Listener")
                    DiagCard {
                        PvSupportingText(
                            text = "Not used on an employee phone. Your owner reads the " +
                                "payment notifications and sends each one to you.",
                        )
                    }
                }
            }

            item(key = "fcm") {
                PvSectionHeader(text = "FCM")
                DiagCard {
                    StatusLine(
                        fcm.tokenAvailable,
                        if (fcm.tokenAvailable) "Push transport ready" else "Token unavailable",
                    )
                    if (fcm.tokenAvailable) {
                        Text(
                            "Token: ${fcm.tokenPreview ?: "registered (hidden)"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    fcm.error?.let {
                        Text(
                            "Error: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item(key = "remote") {
                PvSectionHeader(text = "Remote")
                DiagCard {
                    val settingsState by viewModel.settings.collectAsStateWithLifecycle()
                    val authState by viewModel.authState.collectAsStateWithLifecycle()
                    val employees by viewModel.employees.collectAsStateWithLifecycle()
                    val sendState by viewModel.remoteSendState.collectAsStateWithLifecycle()
                    val dup by viewModel.lastRemoteDuplicate.collectAsStateWithLifecycle()
                    InfoRow("Role", settingsState.role.label.lowercase())
                    InfoRow(
                        "Auth",
                        when (authState) {
                            is PayVoiceAuth.State.READY -> "signed-in"
                            is PayVoiceAuth.State.SIGNING_IN -> "signing-in"
                            is PayVoiceAuth.State.FAILED -> "failed"
                        },
                    )
                    InfoRow("Devices", "${employees.count { it.isActive }} active / ${employees.size} total")
                    InfoRow(
                        "Send",
                        when (sendState) {
                            is RemoteEventSender.SendState.SENT -> "accepted"
                            is RemoteEventSender.SendState.FAILED -> "failed"
                            else -> "idle"
                        } + " · dup-ignored: ${dup == true}",
                    )
                }
            }

            item(key = "capture-toggle") {
                PvSectionHeader(text = "Unknown-package capture")
                DiagCard {
                    Text(
                        "Capture non-GPay notifications locally to identify unexpected " +
                            "packages. Never treated as payment sources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PvSwitchRow(
                        label = "Capture unknown packages (local only)",
                        checked = settings.captureUnknownPackages,
                        onCheckedChange = viewModel::setCaptureUnknownPackages,
                    )
                    PvSecondaryButton(text = "Clear captures", onClick = { viewModel.clearCaptures() })
                }
            }

            if (viewModel.isDebugBuild) {
                item(key = "gpay-test") {
                    PvSectionHeader(text = "GPay parser test (debug)")
                    DiagCard {
                        PvPrimaryButton(
                            text = "Simulate GPay ₹500",
                            onClick = { viewModel.simulate(PaymentSource.GOOGLE_PAY) },
                        )
                    }
                }

                item(key = "trace") {
                    TraceCard(viewModel)
                }
            }

            item(key = "captures") {
                PvSectionHeader(text = "Captured notifications")
                DiagCard {
                    if (captured.isEmpty()) {
                        PvEmptyHint(
                            text = "None yet — captured notifications will appear here.",
                            icon = Icons.Filled.Sms,
                        )
                    } else {
                        captured.take(15).forEachIndexed { index, c ->
                            if (index > 0) {
                                Spacer(Modifier.height(Spacing.md))
                            }
                            Column {
                                Text(
                                    "${c.packageName} · ${timeAgo(c.capturedAtMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    "title: ${c.title.orEmpty()} · text: ${c.text.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "events") {
                PvSectionHeader(text = "Recent events")
                DiagCard {
                    if (logs.isEmpty()) {
                        PvEmptyHint(text = "No events yet — system log entries will appear here.")
                    } else {
                        logs.take(50).forEach { d ->
                            EventRow(
                                tone = when {
                                    d.tag.contains("ERR", ignoreCase = true) ||
                                        d.tag.contains("FAIL", ignoreCase = true) -> StatusTone.Error
                                    d.tag.contains("WARN", ignoreCase = true) -> StatusTone.Warning
                                    else -> StatusTone.Neutral
                                },
                                time = "%tT".format(d.atMs),
                                kind = d.tag,
                                message = d.message,
                            )
                            Spacer(Modifier.height(Spacing.sm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    PvCard(modifier = modifier, contentPadding = PaddingValues(Spacing.lg)) {
        content()
    }
}

/**
 * Debug-only pipeline trace (Step 2 of the reliability work).
 *
 * One line per hop, newest first: the hop name, the stable reason token when
 * there is one, and the offset from the previous row of the same correlation
 * id so a stall is visible without a stopwatch. Nothing here is payment
 * content — reasons are tokens and capture rows carry only a text *shape*.
 */
@Composable
private fun TraceCard(viewModel: MainViewModel) {
    val hops by viewModel.traceEvents.collectAsStateWithLifecycle()
    val export by viewModel.traceExport.collectAsStateWithLifecycle()
    val lastByCid = remember(hops) {
        hops.associate { it.correlationId to it.atMs }
    }

    PvSectionHeader(text = "Pipeline trace (debug)")
    DiagCard {
        PvSupportingText(
            text = "Every stage a payment reaches, newest first. " +
                "A DROPPED line names the hop that lost it.",
        )
        if (!viewModel.traceEnabled) {
            Text(
                "Tracing is off for this build.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (hops.isEmpty()) {
            PvEmptyHint(
                text = "No hops recorded yet — they appear as payments arrive.",
                icon = Icons.Filled.Notifications,
            )
        } else {
            hops.take(40).forEach { hop ->
                TraceRow(
                    time = timeAgo(hop.atMs),
                    outcome = hop.outcome,
                    reason = hop.reason,
                    detail = hop.detail ?: hop.maskedText,
                    sincePrevious = lastByCid[hop.correlationId]
                        ?.takeIf { it != hop.atMs }
                        ?.let { hop.atMs - it },
                )
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        PvSecondaryButton(text = "Export JSONL", onClick = viewModel::exportTrace)
        PvSecondaryButton(text = "Clear trace", onClick = viewModel::clearTrace)
        when (val state = export) {
            is TraceExportState.Written -> Text(
                "Wrote ${state.hops} hops to ${state.path}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TraceExportState.Failed -> Text(
                "Export failed — nothing was written.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            TraceExportState.Running -> Text(
                "Exporting…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TraceExportState.Idle -> Unit
        }
    }
}

/** Tone for a hop: green when it advanced, red when it ended the payment. */
private fun traceTone(outcome: String): StatusTone = when (outcome) {
    "DROPPED" -> StatusTone.Error
    "DEDUPED" -> StatusTone.Warning
    "SPOKEN", "STORED" -> StatusTone.Success
    else -> StatusTone.Neutral
}

@Composable
private fun TraceRow(
    time: String,
    outcome: String,
    reason: String?,
    detail: String?,
    sincePrevious: Long?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatusDot(traceTone(outcome))
        Column {
            Text(
                buildString {
                    append(time)
                    append(" · ")
                    append(outcome)
                    if (reason != null) {
                        append('(')
                        append(reason)
                        append(')')
                    }
                    if (sincePrevious != null) {
                        append(" · +")
                        append(sincePrevious)
                        append("ms")
                    }
                },
                style = MaterialTheme.typography.labelMedium,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EventRow(tone: StatusTone, time: String, kind: String, message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatusDot(tone)
        Column {
            Text(
                "$time · $kind",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
