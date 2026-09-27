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
import androidx.compose.foundation.lazy.items
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
import com.vivekray898.payvoice.ui.components.SectionCard
import com.vivekray898.payvoice.ui.components.SwitchRow
import com.vivekray898.payvoice.ui.components.timeAgo

/**
 * Hidden-ish diagnostics screen (spec §32, Phase-1 subset). Includes the
 * on-device Kotak package verification flow: capture an unknown-package
 * notification locally, promote it, and the listener whitelist updates live.
 */
@Composable
fun DiagnosticsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val captured by viewModel.captured.collectAsStateWithLifecycle()
    val logs by viewModel.diagnostics.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Diagnostics", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Close") }
            }
        }

        item {
            SectionCard(title = "Live status") {
                com.vivekray898.payvoice.ui.components.StatusLine(
                    status.listenerEnabled, "Notification listener"
                )
                com.vivekray898.payvoice.ui.components.StatusLine(
                    status.notificationsEnabled, "App notifications"
                )
                com.vivekray898.payvoice.ui.components.StatusLine(
                    status.batteryExempt, "Battery optimization exempt"
                )
                Text(
                    "${status.manufacturer} ${status.model} · Android ${status.androidVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { viewModel.refreshStatus() }) { Text("Refresh") }
            }
        }

        item {
            SectionCard(title = "Kotak package verification") {
                Text(
                    if (settings.kotakPackageId.isBlank())
                        "Not verified yet. Enable capture, then open Kotak and trigger any "
                            + "notification (a balance refresh works)."
                    else
                        "Verified: " + settings.kotakPackageId,
                    style = MaterialTheme.typography.bodyMedium,
                )
                SwitchRow(
                    label = "Capture unknown packages (local only)",
                    checked = settings.captureUnknownPackages,
                    onCheckedChange = viewModel::setCaptureUnknownPackages,
                    supporting = "Stores raw text of non-payment app notifications in the local database",
                )
                if (captured.isNotEmpty()) {
                    Text(
                        "Captured notifications (newest first):",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    captured.take(10).forEach { c ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${c.packageName} · ${timeAgo(c.capturedAtMs)}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                (c.title.orEmpty() + " — " + c.text.orEmpty())
                                    .take(120),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = { viewModel.setKotakPackage(c.packageName) },
                            enabled = c.packageName.contains("kotak", ignoreCase = true),
                        ) {
                            Text("Set as Kotak")
                        }
                    }
                }
                OutlinedButton(onClick = { viewModel.clearCaptures() }) { Text("Clear captures") }
            }
        }

        item {
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

        item { Spacer(Modifier.height(24.dp)) }
    }
}
