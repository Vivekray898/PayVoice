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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.MaterialTheme
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
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.core.remote.EmployeeDevice
import com.vivekray898.payvoice.core.remote.RemoteEventSender
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.MoneyText
import com.vivekray898.payvoice.ui.components.PvBottomSheet
import com.vivekray898.payvoice.ui.components.PvCard
import com.vivekray898.payvoice.ui.components.PvEmptyState
import com.vivekray898.payvoice.ui.components.PvFab
import com.vivekray898.payvoice.ui.components.PvIconCircle
import com.vivekray898.payvoice.ui.components.PvLoadingRow
import com.vivekray898.payvoice.ui.components.PvPrimaryButton
import com.vivekray898.payvoice.ui.components.PvSecondaryButton
import com.vivekray898.payvoice.ui.components.PvSectionHeader
import com.vivekray898.payvoice.ui.components.PvSecureWindow
import com.vivekray898.payvoice.ui.components.PvSupportingText
import com.vivekray898.payvoice.ui.components.PvTab
import com.vivekray898.payvoice.ui.components.PvTabScaffold
import com.vivekray898.payvoice.ui.components.PvTextButton
import com.vivekray898.payvoice.ui.components.StatusPill
import com.vivekray898.payvoice.ui.components.StatusTone
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Team tab (owner role): the devices that announce this business's payments,
 * plus pairing and a real end-to-end test announcement.
 *
 * Rewritten for the tab map in `docs/UI_INVENTORY.md` §6: this is one of the
 * four bottom destinations rather than a push off Home, so the screen no
 * longer carries a back chevron and its primary action ("Add employee") sits
 * in the scaffold's FAB slot — permanently visible, as the layout rules in
 * AGENTS.md require.
 *
 * [PvSecureWindow] stays: this screen displays a live single-use pairing code,
 * which would otherwise end up in the Recents thumbnail.
 */
@Composable
fun OwnerRemoteScreen(
    viewModel: MainViewModel,
    selected: PvTab,
    onSelectTab: (PvTab) -> Unit,
) {
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val sendState by viewModel.remoteSendState.collectAsStateWithLifecycle()
    val testSendState by viewModel.testSendState.collectAsStateWithLifecycle()
    val revokeState by viewModel.revokeState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var sheetEmployee by remember { mutableStateOf<EmployeeDevice?>(null) }

    PvSecureWindow()

    PvTabScaffold(
        role = DeviceRole.OWNER,
        selected = selected,
        onSelect = onSelectTab,
        floatingActionButton = {
            PvFab(
                text = "Add employee",
                icon = Icons.Filled.Add,
                onClick = {
                    showAddSheet = true
                    viewModel.generatePairingCode()
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                // PvScaffold hands the safe-drawing insets to the caller; ignoring the
                // top one puts the title under the status bar.
                top = inner.calculateTopPadding(),
                bottom = Spacing.xxl + inner.calculateBottomPadding(),
            ),
        ) {
            if (employees.isEmpty()) {
                item(key = "empty") {
                    PvEmptyState(
                        title = "No employees yet",
                        body = "Add an employee so another phone can announce your payments.",
                        icon = Icons.Filled.Person,
                    )
                }
            } else {
                item(key = "count") {
                    val active = employees.count { it.isActive }
                    PvSectionHeader(
                        text = if (active == 1) {
                            "1 device connected"
                        } else {
                            "$active devices connected"
                        },
                    )
                }
                item(key = "header") {
                    Text(
                        text = "Employees",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                items(employees.size, key = { employees[it].uid }) { index ->
                    EmployeeRow(
                        emp = employees[index],
                        onClick = { sheetEmployee = employees[index] },
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            item(key = "test") {
                PvCard(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                    Text(
                        text = "Try it out",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = when (val s = testSendState) {
                            is MainViewModel.TestSendState.Sent ->
                                "Sent — connected devices will announce it shortly."
                            is MainViewModel.TestSendState.Failed -> s.message
                            MainViewModel.TestSendState.Sending -> "Sending…"
                            MainViewModel.TestSendState.Idle -> when (val r = sendState) {
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
                    (revokeState as? MainViewModel.RevokeState.Failed)?.let { failed ->
                        PvSupportingText(text = failed.message, error = true)
                    }
                    PvSecondaryButton(
                        text = if (testSendState is MainViewModel.TestSendState.Sending) {
                            "Sending…"
                        } else {
                            "Send test announcement"
                        },
                        onClick = { viewModel.sendTestToEmployees() },
                        enabled = testSendState !is MainViewModel.TestSendState.Sending,
                    )
                }
            }
        }
    }

    // ---- Add / pairing sheet -----------------------------------------------
    if (showAddSheet) {
        PvBottomSheet(
            onDismissRequest = { showAddSheet = false },
            title = "Add employee",
        ) {
            val code = pairingCode?.code
            if (code == null) {
                PvLoadingRow("Preparing your code…")
            } else {
                PvSupportingText(
                    text = "Ask your employee to enter this code in their PayVoice app.",
                )
                MoneyText(
                    text = code,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                )
                PvSupportingText(text = "Expires in 10 minutes · works once")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PvTextButton(text = "New code", onClick = { viewModel.generatePairingCode() })
                    Spacer(Modifier.width(Spacing.sm))
                    PvPrimaryButton(
                        text = "Done",
                        onClick = { showAddSheet = false },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // ---- Employee detail sheet ----------------------------------------------
    sheetEmployee?.let { emp ->
        PvBottomSheet(
            onDismissRequest = { sheetEmployee = null },
            title = emp.name,
        ) {
            StatusPill(
                text = if (emp.isActive) "Connected"
                else emp.status.lowercase().replaceFirstChar { it.uppercase() },
                tone = if (emp.isActive) StatusTone.Success else StatusTone.Neutral,
            )
            if (emp.lastSeenAtMs > 0) {
                PvSupportingText(text = "Active ${timeAgo(emp.lastSeenAtMs)}")
            }
            PvPrimaryButton(
                text = "Send test announcement",
                onClick = {
                    viewModel.sendTestToEmployees()
                    sheetEmployee = null
                },
            )
            PvSecondaryButton(
                text = "Remove employee",
                icon = Icons.Filled.PersonRemove,
                onClick = {
                    viewModel.revokeEmployee(emp.uid)
                    sheetEmployee = null
                },
            )
            PvSupportingText(
                text = "Removing stops announcements on that phone. " +
                    "The owner phone is unaffected.",
            )
        }
    }
}

@Composable
private fun EmployeeRow(emp: EmployeeDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        PvIconCircle(icon = Icons.Filled.Person)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = emp.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusPill(
                text = if (emp.isActive) "Connected"
                else emp.status.lowercase().replaceFirstChar { it.uppercase() },
                tone = if (emp.isActive) StatusTone.Success else StatusTone.Neutral,
            )
        }
    }
}