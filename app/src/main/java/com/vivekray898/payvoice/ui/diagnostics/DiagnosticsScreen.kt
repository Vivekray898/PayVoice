package com.vivekray898.payvoice.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.SectionCard
import com.vivekray898.payvoice.ui.components.StatusLine
import com.vivekray898.payvoice.ui.components.SwitchRow
import com.vivekray898.payvoice.ui.components.timeAgo

/**
 * Local diagnostics (spec: PAYMENT NOTIFICATION DIAGNOSTICS). Shows the exact
 * notification fields (title/text/bigText/subText/id/posted time) for captured
 * payment-app notifications so parsers can be refined per app version/language.
 * This data is LOCAL ONLY — never uploaded.
 */
@Composable
fun DiagnosticsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val captured by viewModel.captured.collectAsStateWithLifecycle()
    val logs by viewModel.diagnostics.collectAsStateWithLifecycle()

    // UI overhaul Phase 3f: real screen chrome (title + back, safeDrawing
    // insets) — this screen previously rendered its first card UNDER the
    // status bar with only a Close text button.
    PvScaffold(title = "Diagnostics", onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "status") {
            SectionCard(title = "Live status") {
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
                Text(
                    "Role: ${role.role.label} · Auth: ${when (authState) {
                        is com.vivekray898.payvoice.core.remote.PayVoiceAuth.State.READY -> "signed-in"
                        is com.vivekray898.payvoice.core.remote.PayVoiceAuth.State.SIGNING_IN -> "signing-in"
                        is com.vivekray898.payvoice.core.remote.PayVoiceAuth.State.FAILED -> "failed"
                    }}}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "Employees: ${employees.count { it.isActive }} active / ${employees.size} total · " +
                        "Remote send: ${when (sendState) {
                            is com.vivekray898.payvoice.core.remote.RemoteEventSender.SendState.SENT -> "accepted"
                            is com.vivekray898.payvoice.core.remote.RemoteEventSender.SendState.FAILED -> "failed"
                            else -> "idle"
                        }}} · " +
                        "Dup ignored: ${dup == true}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                OutlinedButton(onClick = { viewModel.refreshStatus() }) { Text("Refresh") }
            }
        }

        item(key = "capture-toggle") {
            SectionCard(title = "Unknown-package capture") {
                Text(
                    "Capture non-GPay notifications locally to identify unexpected " +
                        "packages. Captured packages are never treated as payment " +
                        "sources — GPay is the only notification source.",
                    style = MaterialTheme.typography.bodyMedium,
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
                SectionCard(title = "GPay parser test (debug)") {
                    Button(onClick = { viewModel.simulate(com.vivekray898.payvoice.core.model.PaymentSource.GOOGLE_PAY) }) {
                        Text("Simulate GPay ₹500")
                    }
                }
            }
            item(key = "sms-test") {
                SectionCard(title = "SMS parser test (debug)") {
                    Text(
                        "Pushes a sample SMS through the real pipeline " +
                            "(parse → dedup → announce if received).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = {
                            viewModel.simulateSms(
                                "KKBK6789",
                                "Your A/c XX1234 is credited with Rs 500 via UPI Ref 432198765432",
                            )
                        }) { Text("Kotak credit ₹500 (UTR)") }
                        OutlinedButton(onClick = {
                            viewModel.simulateSms(
                                "KKBK6789",
                                "Rs 500 debited from your account for UPI transfer",
                            )
                        }) { Text("Kotak debit (silent)") }
                        OutlinedButton(onClick = {
                            viewModel.simulateSms(
                                "KKBK6789",
                                "Your bill of Rs 500 is due tomorrow",
                            )
                        }) { Text("Bill reminder (silent)") }
                        OutlinedButton(onClick = {
                            viewModel.simulateSms(
                                "KKBK6789",
                                "Your OTP for transaction is 123456",
                            )
                        }) { Text("OTP (silent)") }
                    }
                }
            }
        }

        item(key = "captures") {
            SectionCard(title = "Captured notifications (newest first)") {
                if (captured.isEmpty()) {
                    Text("None yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    captured.take(15).forEach { c ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                "${c.packageName} · ${timeAgo(c.capturedAtMs)}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                "Source: ${c.packageName}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Notification ID: ${c.notificationId} · Posted: ${c.postedTimeMs}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "Title: ${c.title.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Text: ${c.text.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!c.bigText.isNullOrBlank()) {
                                Text(
                                    "BigText: ${c.bigText}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (!c.subText.isNullOrBlank()) {
                                Text(
                                    "SubText: ${c.subText}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "logs") {
            SectionCard(title = "System log (last 200)") {
                if (logs.isEmpty()) {
                    Text("No events yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    logs.forEach { d ->
                        Text(
                            "%tT [%s] %s".format(d.atMs, d.tag, d.message),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

            item(key = "footer") { Spacer(Modifier.height(24.dp)) }
        }
    }
}
