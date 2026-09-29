package com.vivekray898.payvoice.ui.employee

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.vivekray898.payvoice.ui.components.PvDivider
import com.vivekray898.payvoice.ui.components.PvPaymentRow
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.StatusPill
import com.vivekray898.payvoice.ui.components.timeAgo

/**
 * Employee side (production redesign): join with a code, see the connection,
 * hear a test, leave. Backend operations remain the existing ViewModel calls;
 * only presentation changed. No backend terminology anywhere.
 */
@Composable
fun EmployeeScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val ownDevice by viewModel.ownDevice.collectAsStateWithLifecycle()
    val joinState by viewModel.joinState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showJoinDialog by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }

    val paired = ownDevice?.isActive == true

    PvScaffold(title = if (paired) "Connection" else "Join", onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "connection") {
                PvSection {
                    if (paired) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("My Business", style = MaterialTheme.typography.headlineSmall)
                                ownDevice?.lastSeenAtMs?.takeIf { it > 0 }?.let {
                                    Text(
                                        "Last active ${timeAgo(it)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            StatusPill("Connected", true)
                        }
                        Text(
                            "This phone announces payments received by your owner.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Not connected",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "Ask the business owner for a code, then enter it below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { showJoinDialog = true }) { Text("Enter code") }
                    }
                }
            }

            if (paired) {
                // Reliability visibility (reliability fix): Doze delays FCM by
                // seconds-to-minutes on restricted devices. The employee never
                // sees the owner's setup wizard, so the exemption state and its
                // fix must live HERE.
                item(key = "battery") {
                    PvSection(title = "Reliability") {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                com.vivekray898.payvoice.ui.components.StatusDot(status?.batteryExempt == true)
                                Text(
                                    if (status?.batteryExempt == true) "Battery optimization: off"
                                    else "Battery optimization: on — payments may arrive late",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        if (status?.batteryExempt != true) {
                            OutlinedButton(onClick = { viewModel.fixBattery(context) }) {
                                Text("Allow unrestricted battery")
                            }
                        }
                    }
                }

                item(key = "test") {
                    PvSection(title = "Try it out") {
                        OutlinedButton(onClick = { viewModel.speakTest() }) {
                            Text("Hear a test announcement")
                        }
                        Text(
                            "Plays: \"PayVoice test announcement.\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item(key = "last-payment") {
                    PvSection(title = "Last payment announced") {
                        val last = viewModel.history.value.firstOrNull()
                        if (last == null) {
                            Text(
                                "Nothing yet — payments announced here appear below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            PvPaymentRow(
                                amountText = com.vivekray898.payvoice.core.parser.AmountExtractor
                                    .formatMinor(last.amountMinor, last.currency),
                                source = last.sourceName,
                                sender = last.senderName,
                                timeText = timeAgo(last.announcedAtMs),
                            )
                        }
                    }
                }
            }

            item(key = "leave") {
                PvSection {
                    if (paired) {
                        PvDivider()
                        Spacer(Modifier.height(8.dp))
                    }
                    TextButton(onClick = { confirmLeave = true }) {
                        Text(
                            if (paired) "Leave this business"
                            else "I already have a code — join instead",
                            color = if (paired) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (paired) {
                        Text(
                            "You'll stop hearing payment announcements from your owner. " +
                                "Payments already announced stay on this phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "footer") { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ---- Join dialog ---------------------------------------------------------
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = {
                if (joinState !is MainViewModel.JoinState.Joining) {
                    showJoinDialog = false
                    viewModel.clearJoinResult()
                }
            },
            title = { Text("Join an owner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter the code provided by your owner.")
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase() },
                        label = { Text("Code (PAY-XXXXXX)") },
                        singleLine = true,
                        enabled = joinState !is MainViewModel.JoinState.Joining,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when (val s = joinState) {
                        is MainViewModel.JoinState.Joining -> Text(
                            "Connecting…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        is MainViewModel.JoinState.Success -> Text(
                            "Connected!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        is MainViewModel.JoinState.Failed -> Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        MainViewModel.JoinState.Idle -> Unit
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.joinOwner(code) },
                    enabled = joinState !is MainViewModel.JoinState.Joining && code.isNotBlank(),
                ) { Text("Connect") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showJoinDialog = false
                        viewModel.clearJoinResult()
                    },
                    enabled = joinState !is MainViewModel.JoinState.Joining,
                ) { Text("Cancel") }
            },
        )
    }

    // ---- Leave confirmation ---------------------------------------------------
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this business?") },
            text = {
                Text("You'll stop hearing payment announcements from your owner.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.leaveOwner()
                    confirmLeave = false
                }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
            },
        )
    }
}
