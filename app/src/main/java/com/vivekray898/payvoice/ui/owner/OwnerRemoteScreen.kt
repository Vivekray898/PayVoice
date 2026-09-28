package com.vivekray898.payvoice.ui.owner

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.remote.RemoteEventSender
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.SectionCard
import com.vivekray898.payvoice.ui.components.StatusLine
import com.vivekray898.payvoice.ui.components.timeAgo

/**
 * Owner remote-announcements screen (spec §3, §4, §16, §22): employee list
 * with live status, short-lived single-use pairing codes, revoke, and the
 * test announcement. Business-utility tone; consistent with existing UI.
 */
@Composable
fun OwnerRemoteScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val sendState by viewModel.remoteSendState.collectAsStateWithLifecycle()
    val testSendState by viewModel.testSendState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

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
                Text("Payment Announcements", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Close") }
            }
        }

        item(key = "your-device") {
            SectionCard(title = "Your device") {
                StatusLine(true, "Receiving payments")
                Text(
                    "GPay notifications and bank SMS announce locally and are " +
                        "forwarded to connected employees.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "employees") {
            val active = employees.count { it.isActive }
            SectionCard(title = "Employees") {
                Text(
                    "$active connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (employees.isEmpty()) {
                    Text(
                        "No employees yet. Add one with a pairing code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    employees.forEach { emp ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(emp.name, style = MaterialTheme.typography.bodyLarge)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        com.vivekray898.payvoice.ui.components.StatusDot(
                                            when {
                                                emp.isActive -> true
                                                else -> false
                                            }
                                        )
                                        Text(
                                            if (emp.isActive) "Connected" else emp.status.lowercase()
                                                .replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (emp.lastSeenAtMs > 0) {
                                        Text(
                                            "Last sync: ${timeAgo(emp.lastSeenAtMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                OutlinedButton(onClick = { viewModel.revokeEmployee(emp.uid) }) {
                                    Text(if (emp.isActive) "Remove" else "Delete")
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    showAddDialog = true
                    viewModel.generatePairingCode()
                }) {
                    Text("+ Add Employee")
                }
            }
        }

        item(key = "test") {
            SectionCard(title = "Test") {
                OutlinedButton(
                    onClick = { viewModel.sendTestToEmployees() },
                    enabled = testSendState !is MainViewModel.TestSendState.Sending,
                ) {
                    Text(
                        if (testSendState is MainViewModel.TestSendState.Sending) "Sending…"
                        else "Send Test Announcement",
                    )
                }
                Text(
                    when (val s = testSendState) {
                        is MainViewModel.TestSendState.Sent ->
                            "Test event accepted by backend ${timeAgo(s.atMs)} — check the employee device."
                        is MainViewModel.TestSendState.Failed ->
                            s.message
                        MainViewModel.TestSendState.Sending ->
                            "Sending test event…"
                        MainViewModel.TestSendState.Idle ->
                            when (val r = sendState) {
                                is RemoteEventSender.SendState.SENT ->
                                    "Last event accepted by backend ${timeAgo(r.atMs)}"
                                is RemoteEventSender.SendState.FAILED ->
                                    "Last send failed (${r.reason}) — local announcements unaffected"
                                else -> "Employees hear: \"PayVoice test announcement.\""
                            }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "footer") { Spacer(Modifier.height(24.dp)) }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Employee") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val code = pairingCode?.code
                    if (code == null) {
                        Text("Generating a secure code…")
                    } else {
                        Text(
                            code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "Valid for 10 minutes · single use.\n" +
                                "On the employee phone: PayVoice → Join Owner → enter this code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    viewModel.generatePairingCode()
                }) { Text("New code") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Done") }
            },
        )
    }
}
