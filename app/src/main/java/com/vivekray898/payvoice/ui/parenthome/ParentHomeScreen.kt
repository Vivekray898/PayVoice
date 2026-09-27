package com.vivekray898.payvoice.ui.parenthome

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.parser.AmountExtractor
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.SectionCard
import com.vivekray898.payvoice.ui.components.StatusLine
import com.vivekray898.payvoice.ui.components.timeAgo
import java.util.Calendar

/**
 * Parent home (spec §22 Phase-1 subset). Device cards / pairing arrive with
 * Phase 2-3.
 */
@Composable
fun ParentHomeScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenReliability: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") { Spacer(Modifier.height(8.dp)) }

        item(key = "title") {
            Column {
                Text("PayVoice", style = MaterialTheme.typography.headlineMedium)
                Text(
                    greeting(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "status") {
            SectionCard(title = "Detection status") {
                StatusLine(status?.listenerEnabled == true, "Notification listener")
                StatusLine(status?.notificationsEnabled == true, "App notifications")
                StatusLine(status?.batteryExempt == true, "Unrestricted battery")
                status?.romHint?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "ROM: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
                    TextButton(onClick = onOpenReliability) { Text("Reliability") }
                    TextButton(onClick = onOpenDiagnostics) { Text("Diagnostics") }
                }
            }
        }

        item(key = "history-title") {
            SectionCard(title = "Recent payments") {
                if (history.isEmpty()) {
                    Text(
                        "No payments announced yet. When Google Pay or a bank SMS " +
                            "credit arrives, the announcement appears here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        history.take(5).forEach { entry ->
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    AmountExtractor.formatMinor(entry.amountMinor, entry.currency),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    buildString {
                                        append("Received")
                                        append(entry.senderName?.let { " from $it" } ?: "")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "${entry.sourceName} · ${timeAgo(entry.announcedAtMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "test") {
            SectionCard(title = "Test") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.speakTest() }) { Text("🔊 Test announcement") }
                }
                Text(
                    "Debug: simulate a real notification through the full pipeline " +
                        "(parse → dedup → announce).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.simulate(PaymentSource.GOOGLE_PAY) }) {
                        Text("Simulate GPay ₹500")
                    }
                }
            }
        }

        item(key = "footer") { Spacer(Modifier.height(24.dp)) }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Hello"
    }
}
