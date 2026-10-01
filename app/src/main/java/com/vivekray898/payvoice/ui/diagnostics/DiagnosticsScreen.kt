package com.vivekray898.payvoice.ui.diagnostics

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.remote.PayVoiceAuth
import com.vivekray898.payvoice.core.remote.RemoteEventSender
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvDivider
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.StatusLine
import com.vivekray898.payvoice.ui.components.SwitchRow
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Local diagnostics (spec: PAYMENT NOTIFICATION DIAGNOSTICS). Shows the exact
 * notification fields (title/text/bigText/subText/id/posted time) for captured
 * payment-app notifications so parsers can be refined per app version/language.
 * This data is LOCAL ONLY — never uploaded.
 *
 * Premium pass (Phase 4b): carded sections, monospace data in quiet code
 * blocks, divider-separated capture rows. Technical by design — this is the
 * one screen where monospace is the point.
 */
@Composable
fun DiagnosticsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val captured by viewModel.captured.collectAsStateWithLifecycle()
    val logs by viewModel.diagnostics.collectAsStateWithLifecycle()

    PvScaffold(title = "Diagnostics", onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "status") {
                PvSection(title = "Live status", carded = true) {
                    StatusLine(status?.listenerEnabled == true, "Notification listener")
                    StatusLine(status?.notificationsEnabled == true, "App notifications")
                    StatusLine(status?.batteryExempt == true, "Battery optimization exempt")
                    Text(
                        "${status?.manufacturer ?: "?"} ${status?.model ?: "?"} · " +
                            "Android ${status?.androidVersion ?: "?"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Remote layer (spec §32) — identifiers masked, never tokens.
                    val role by viewModel.settings.collectAsStateWithLifecycle()
                    val authState by viewModel.authState.collectAsStateWithLifecycle()
                    val employees by viewModel.employees.collectAsStateWithLifecycle()
                    val sendState by viewModel.remoteSendState.collectAsStateWithLifecycle()
                    val dup by viewModel.lastRemoteDuplicate.collectAsStateWithLifecycle()
                    CodeBlock(
                        listOf(
                            "role    : ${role.role.label.lowercase()}",
                            "auth    : ${when (authState) {
                                is PayVoiceAuth.State.READY -> "signed-in"
                                is PayVoiceAuth.State.SIGNING_IN -> "signing-in"
                                is PayVoiceAuth.State.FAILED -> "failed"
                            }}",
                            "devices : ${employees.count { it.isActive }} active / ${employees.size} total",
                            "send    : ${when (sendState) {
                                is RemoteEventSender.SendState.SENT -> "accepted"
                                is RemoteEventSender.SendState.FAILED -> "failed"
                                else -> "idle"
                            }} · dup-ignored: ${dup == true}",
                        ),
                    )
                    OutlinedButton(onClick = { viewModel.refreshStatus() }) { Text("Refresh") }
                }
            }

            item(key = "capture-toggle") {
                PvSection(title = "Unknown-package capture", carded = true) {
                    Text(
                        "Capture non-GPay notifications locally to identify unexpected " +
                            "packages. Captured packages are never treated as payment " +
                            "sources — GPay is the only notification source.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SwitchRow(
                        label = "Capture unknown packages (local only)",
                        checked = settings.captureUnknownPackages,
                        onCheckedChange = viewModel::setCaptureUnknownPackages,
                        supporting = "Stores raw text of non-payment app notifications in the local database",
                    )
                    OutlinedButton(onClick = { viewModel.clearCaptures() }) { Text("Clear captures") }
                }
            }

            if (viewModel.isDebugBuild) {
                item(key = "gpay-test") {
                    PvSection(title = "GPay parser test (debug)", carded = true) {
                        Button(onClick = { viewModel.simulate(PaymentSource.GOOGLE_PAY) }) {
                            Text("Simulate GPay ₹500")
                        }
                    }
                }
            }

            item(key = "captures") {
                PvSection(title = "Captured notifications (newest first)", carded = true) {
                    if (captured.isEmpty()) {
                        Text(
                            "None yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        captured.take(15).forEachIndexed { index, c ->
                            if (index > 0) PvDivider()
                            Column(Modifier.padding(vertical = Spacing.sm)) {
                                Text(
                                    "${c.packageName} · ${timeAgo(c.capturedAtMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                CodeBlock(
                                    listOf(
                                        "notif id : ${c.notificationId} · posted: ${c.postedTimeMs}",
                                        "title    : ${c.title.orEmpty()}",
                                        "text     : ${c.text.orEmpty()}",
                                    ) + listOfNotNull(
                                        c.bigText?.takeIf { it.isNotBlank() }?.let { "bigText  : $it" },
                                        c.subText?.takeIf { it.isNotBlank() }?.let { "subText  : $it" },
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "logs") {
                PvSection(title = "System log (last 200)", carded = true) {
                    if (logs.isEmpty()) {
                        Text(
                            "No events yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        CodeBlock(logs.map { "%tT [%s] %s".format(it.atMs, it.tag, it.message) })
                    }
                }
            }
        }
    }
}

/** Quiet terminal-style block for technical values inside a card. */
@Composable
private fun CodeBlock(lines: List<String>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
