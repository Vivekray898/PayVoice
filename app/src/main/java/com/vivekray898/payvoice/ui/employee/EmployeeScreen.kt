package com.vivekray898.payvoice.ui.employee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.SectionCard
import com.vivekray898.payvoice.ui.components.StatusLine
import com.vivekray898.payvoice.ui.components.timeAgo

/**
 * Employee screen (spec §13, §21, §26): connection state to the owner's
 * business, pairing entry, test announcement, and leave. The employee device
 * never detects payments itself — it only announces remote events.
 */
@Composable
fun EmployeeScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val ownDevice by viewModel.ownDevice.collectAsStateWithLifecycle()
    val joinResult by viewModel.joinResult.collectAsStateWithLifecycle()
    var showJoinDialog by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }

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
                Text("Remote Announcements", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Close") }
            }
        }

        item(key = "connection") {
            val paired = ownDevice?.isActive == true
            SectionCard(title = "Connection") {
                if (paired) {
                    StatusLine(true, "Connected to: My Business")
                    Text(
                        "Ready to announce payments",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    ownDevice?.lastSeenAtMs?.takeIf { it > 0 }?.let {
                        Text(
                            "Last sync: ${timeAgo(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    StatusLine(false, "Not connected")
                    Text(
                        "Ask the business owner for a pairing code, then tap Join Owner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { showJoinDialog = true }) {
                    Text(if (paired) "Re-pair with a new owner" else "Join Owner")
                }
            }
        }

        item(key = "history") {
            SectionCard(title = "Last payment received") {
                val last = viewModel.history.value
                if (last.isEmpty()) {
                    Text(
                        "Nothing yet. Payments announced here also appear on the home screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val entry = last.first()
                    Text(
                        com.vivekray898.payvoice.core.parser.AmountExtractor
                            .formatMinor(entry.amountMinor, entry.currency),
                        style = MaterialTheme.typography.titleLarge,
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

        item(key = "test") {
            SectionCard(title = "Test") {
                OutlinedButton(onClick = { viewModel.speakTest() }) {
                    Text("🔊 Test Announcement")
                }
                Text(
                    "Speaks: \"PayVoice test announcement.\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "leave") {
            SectionCard(title = "Leave") {
                OutlinedButton(onClick = { viewModel.leaveOwner() }) {
                    Text("Leave Business")
                }
                Text(
                    "Stops all future payment announcements from the owner. " +
                        "Payments already announced stay in this device's history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "footer") { Spacer(Modifier.height(24.dp)) }
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = {
                showJoinDialog = false
                viewModel.clearJoinResult()
            },
            title = { Text("Join Owner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase() },
                        label = { Text("Pairing code (PAY-XXXXXX)") },
                        singleLine = true,
                    )
                    when (joinResult) {
                        true -> Text("Connected!", color = MaterialTheme.colorScheme.primary)
                        false -> Text(
                            "Invalid, expired, or already used. Ask for a new code.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        null -> Unit
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.joinOwner(code) }) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJoinDialog = false
                    viewModel.clearJoinResult()
                }) { Text("Cancel") }
            },
        )
    }
}
