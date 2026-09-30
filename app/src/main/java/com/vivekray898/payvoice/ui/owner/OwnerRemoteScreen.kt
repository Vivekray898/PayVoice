package com.vivekray898.payvoice.ui.owner

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.vivekray898.payvoice.ui.components.PvStatusHero
import com.vivekray898.payvoice.ui.components.StatusPill
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.components.statusPositive

/**
 * Employees (UI overhaul Phase 3c): counter hero, employee rows that open a
 * ModalBottomSheet with actions, and an ExtendedFloatingActionButton to add.
 * Pairing-code display and destructive confirms are bottom sheets, not
 * AlertDialogs (design spec). All backend operations stay the EXISTING
 * ViewModel calls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerRemoteScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val sendState by viewModel.remoteSendState.collectAsStateWithLifecycle()
    val testSendState by viewModel.testSendState.collectAsStateWithLifecycle()
    val revokeState by viewModel.revokeState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var sheetEmployee by remember { mutableStateOf<EmployeeDevice?>(null) }

    PvScaffold(
        title = "Employees",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    showAddSheet = true
                    viewModel.generatePairingCode()
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add employee") },
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            // FAB overlaps content: leave room at the bottom.
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "counter") {
                val active = employees.count { it.isActive }
                val revoked = employees.size - active
                if (employees.isEmpty()) {
                    PvSection {
                        PvEmptyState(
                            icon = Icons.Filled.Person,
                            title = "No employees yet",
                            body = "Add an employee so another phone can announce your payments.",
                            actionLabel = "Generate pairing code",
                            onAction = {
                                showAddSheet = true
                                viewModel.generatePairingCode()
                            },
                        )
                    }
                } else {
                    PvStatusHero(
                        icon = Icons.Filled.CheckCircle,
                        tint = statusPositive(),
                        title = "Connected devices",
                        headline = if (active == 1) "1 connected" else "$active connected",
                        body = if (revoked > 0) {
                            "$revoked removed · they no longer receive announcements"
                        } else {
                            "They hear your payment announcements"
                        },
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

            if (employees.isNotEmpty()) {
                item(key = "list") {
                    PvSection(title = "Devices") {
                        Column {
                            employees.forEachIndexed { index, emp ->
                                if (index > 0) PvDivider()
                                EmployeeRow(emp = emp, onClick = { sheetEmployee = emp })
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
            }

            item(key = "footer") { Spacer(Modifier.height(8.dp)) }
        }
    }

    // ---- Add / pairing sheet (bottom sheet, not AlertDialog) ----------------
    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add employee", style = MaterialTheme.typography.titleLarge)
                val code = pairingCode?.code
                if (code == null) {
                    PvLoadingRow("Preparing your code…")
                } else {
                    Text(
                        "Ask your employee to enter this code in their PayVoice app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.generatePairingCode() }) {
                            Text("New code")
                        }
                        OutlinedButton(onClick = { showAddSheet = false }) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    // ---- Employee detail sheet: details + actions ---------------------------
    sheetEmployee?.let { emp ->
        ModalBottomSheet(onDismissRequest = { sheetEmployee = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(emp.name, style = MaterialTheme.typography.titleLarge)
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
                Button(onClick = {
                    viewModel.sendTestToEmployees()
                    sheetEmployee = null
                }) {
                    Text("Send test announcement")
                }
                OutlinedButton(onClick = {
                    viewModel.revokeEmployee(emp.uid)
                    sheetEmployee = null
                }) {
                    Text("Remove employee", color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "Removing stops announcements on that phone. The owner phone is unaffected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmployeeRow(emp: EmployeeDevice, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
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
    }
}
