package com.vivekray898.payvoice.ui.owner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.remote.EmployeeDevice
import com.vivekray898.payvoice.core.remote.RemoteEventSender
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvEmptyHint
import com.vivekray898.payvoice.ui.components.PvLoadingRow
import com.vivekray898.payvoice.ui.components.PvPrimaryButton
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.HeroBanner
import com.vivekray898.payvoice.ui.components.PvSecondaryButton
import com.vivekray898.payvoice.ui.components.ScreenHeader
import com.vivekray898.payvoice.ui.components.SectionHeader
import com.vivekray898.payvoice.ui.components.StatusPill
import com.vivekray898.payvoice.ui.components.StatusTone
import com.vivekray898.payvoice.ui.components.MoneyText
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Employees (DESIGN.md rebuild, Phase 3c): PvScaffold + PvTopBar, employee
 * rows as rounded.lg surfaces with StatusPill, bottom-sheet actions, pill
 * FAB, centered empty state with the primary pill.
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
        topBar = {},
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    showAddSheet = true
                    viewModel.generatePairingCode()
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add employee") },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = Spacing.huge + inner.calculateBottomPadding(),
            ),
        ) {
            item(key = "header") {
                ScreenHeader(title = "Employees", onBack = onBack)
                HeroBanner(modifier = Modifier.padding(top = Spacing.md))
            }
            if (employees.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.huge),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Spacing.huge - Spacing.lg),
                        )
                        Text(
                            "No employees yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Add an employee so another phone can announce your payments.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        PvPrimaryButton(
                            text = "Add employee",
                            onClick = {
                                showAddSheet = true
                                viewModel.generatePairingCode()
                            },
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }
                }
            } else {
                item(key = "header") {
                    val active = employees.count { it.isActive }
                    SectionHeader(
                        text = if (active == 1) "1 device connected" else "$active devices connected",
                    )
                }
                items(employees.size, key = { employees[it].uid }) { index ->
                    EmployeeRow(
                        emp = employees[index],
                        onClick = { sheetEmployee = employees[index] },
                    )
                    Spacer(Modifier.height(Spacing.md))
                }
                item(key = "test") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader(text = "Try it out")
                        Button(
                            onClick = { viewModel.sendTestToEmployees() },
                            enabled = testSendState !is MainViewModel.TestSendState.Sending,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
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
                                is MainViewModel.TestSendState.Failed -> s.message
                                MainViewModel.TestSendState.Sending -> "Sending…"
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
                        when (val r = revokeState) {
                            is MainViewModel.RevokeState.Failed -> Text(
                                r.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    // ---- Add / pairing sheet -----------------------------------------------
    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Add employee", style = MaterialTheme.typography.headlineSmall)
                val code = pairingCode?.code
                if (code == null) {
                    PvLoadingRow("Preparing your code…")
                } else {
                    Text(
                        "Ask your employee to enter this code in their PayVoice app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneyText(
                        text = code,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Expires in 10 minutes · works once",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Button(
                            onClick = { viewModel.generatePairingCode() },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                        ) { Text("New code") }
                        PvSecondaryButton(
                            text = "Done",
                            onClick = { showAddSheet = false },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    // ---- Employee detail sheet ----------------------------------------------
    sheetEmployee?.let { emp ->
        ModalBottomSheet(onDismissRequest = { sheetEmployee = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(emp.name, style = MaterialTheme.typography.headlineSmall)
                StatusPill(
                    text = if (emp.isActive) "Connected"
                    else emp.status.lowercase().replaceFirstChar { it.uppercase() },
                    tone = if (emp.isActive) StatusTone.Success else StatusTone.Neutral,
                )
                if (emp.lastSeenAtMs > 0) {
                    Text(
                        "Active ${timeAgo(emp.lastSeenAtMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        viewModel.sendTestToEmployees()
                        sheetEmployee = null
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                ) { Text("Send test announcement") }
                PvSecondaryButton(
                    text = "Remove employee",
                    icon = Icons.Filled.PersonRemove,
                    onClick = {
                        viewModel.revokeEmployee(emp.uid)
                        sheetEmployee = null
                    },
                )
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
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large, // rounded.lg
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Spacing.xxs,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            androidx.compose.material3.Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(Spacing.xxl + Spacing.lg), // 40dp circle
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Spacing.xl + Spacing.xs),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(emp.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xxs))
                StatusPill(
                    text = if (emp.isActive) "Connected"
                    else emp.status.lowercase().replaceFirstChar { it.uppercase() },
                    tone = if (emp.isActive) StatusTone.Success else StatusTone.Neutral,
                )
            }
        }
    }
}
