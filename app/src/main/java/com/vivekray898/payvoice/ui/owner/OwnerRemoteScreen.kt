package com.vivekray898.payvoice.ui.owner

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.remote.EmployeeDevice
import com.vivekray898.payvoice.core.remote.RemoteEventSender
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvDivider
import com.vivekray898.payvoice.ui.components.PvEmptyState
import com.vivekray898.payvoice.ui.components.PvLoadingRow
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.StatusPill
import com.vivekray898.payvoice.ui.components.timeAgo

/**
 * Employees (production redesign): a normal consumer feature — people who
 * hear the payments. Pairing via a big shareable code, removal behind a
 * confirmation dialog, honest outcomes via snackbar-style status lines.
 * All backend operations flow through the EXISTING ViewModel calls.
 */
@Composable
fun OwnerRemoteScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val sendState by viewModel.remoteSendState.collectAsStateWithLifecycle()
    val testSendState by viewModel.testSendState.collectAsStateWithLifecycle()
    val revokeState by viewModel.revokeState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<EmployeeDevice?>(null) }

    PvScaffold(title = "Employees", onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "intro") {
                PvSection {
                    Text(
                        "People who hear your payment announcements.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "remove-outcome") {
                when (val r = revokeState) {
                    is MainViewModel.RevokeState.Success -> PvSection {
                        Text(
                            "Employee removed — it will no longer receive announcements.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is MainViewModel.RevokeState.Failed -> PvSection {
                        Text(
                            r.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    MainViewModel.RevokeState.Idle -> Unit
                }
            }

            item(key = "list") {
                PvSection(title = "Connected devices") {
                    when {
                        employees.isEmpty() -> PvEmptyState(
                            title = "No employees yet",
                            body = "Add an employee so another phone can announce your payments.",
                            actionLabel = "Add employee",
                            onAction = {
                                showAddDialog = true
                                viewModel.generatePairingCode()
                            },
                        )
                        else -> Column {
                            employees.forEachIndexed { index, emp ->
                                if (index > 0) PvDivider()
                                EmployeeRow(
                                    emp = emp,
                                    onRemove = { pendingRemoval = emp },
                                )
                            }
                        }
                    }
                }
            }

            if (employees.isNotEmpty()) {
                item(key = "add") {
                    PvSection {
                        OutlinedButton(onClick = {
                            showAddDialog = true
                            viewModel.generatePairingCode()
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.height(0.dp))
                            Text("  Add employee")
                        }
                    }
                }
            }

            item(key = "test") {
                PvSection(title = "Try it out") {
                    Button(
                        onClick = { viewModel.sendTestToEmployees() },
                        enabled = testSendState !is MainViewModel.TestSendState.Sending,
                    ) {
                        Text(
                            if (testSendState is MainViewModel.TestSendState.Sending) "Sending…"
                            else "Send test announcement",
                        )
                    }
                    Text(
                        when (val s = testSendState) {
                            is MainViewModel.TestSendState.Sent ->
                                "Sent — connected devices will announce it shortly."
                            is MainViewModel.TestSendState.Failed ->
                                s.message
                            MainViewModel.TestSendState.Sending ->
                                "Sending…"
                            MainViewModel.TestSendState.Idle ->
                                when (val r = sendState) {
                                    is RemoteEventSender.SendState.SENT ->
                                        "Last event sent ${timeAgo(r.atMs)}."
                                    is RemoteEventSender.SendState.FAILED ->
                                        "Last send failed — your local announcements are unaffected."
                                    else ->
                                        "Connected devices hear: \"PayVoice test announcement.\""
                                }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "footer") { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ---- Add / pairing sheet -----------------------------------------------
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add employee") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val code = pairingCode?.code
                    if (code == null) {
                        PvLoadingRow("Preparing your code…")
                    } else {
                        Text("Ask your employee to enter this code in their PayVoice app.")
                        Text(
                            code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Expires in 10 minutes · works once",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.generatePairingCode() }) { Text("New code") }
            },
        )
    }

    // ---- Remove confirmation (spec §9) --------------------------------------
    pendingRemoval?.let { emp ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove employee?") },
            text = {
                Text(
                    "${emp.name} will no longer receive payment announcements " +
                        "from this device.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeEmployee(emp.uid)
                    pendingRemoval = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmployeeRow(emp: EmployeeDevice, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emp.name, style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(
                    text = if (emp.isActive) "Connected"
                    else emp.status.lowercase().replaceFirstChar { it.uppercase() },
                    ok = emp.isActive,
                )
                if (emp.lastSeenAtMs > 0) {
                    Text(
                        "Active ${timeAgo(emp.lastSeenAtMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(onClick = onRemove) {
            Text("Remove", color = MaterialTheme.colorScheme.error)
        }
    }
}
