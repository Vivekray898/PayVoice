package com.vivekray898.payvoice.ui.employee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.parser.AmountExtractor
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvEmptyHint
import com.vivekray898.payvoice.ui.components.PvLoadingRow
import com.vivekray898.payvoice.ui.components.PvPaymentRow
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.PvStatusHero
import com.vivekray898.payvoice.ui.components.StatusLine
import com.vivekray898.payvoice.ui.components.statusCaution
import com.vivekray898.payvoice.ui.components.statusPositive
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * This device (premium pass, Phase 4d): hero with status chip, carded
 * status sections with INLINE fix actions (battery / notifications).
 * Join and leave run in ModalBottomSheets. All backend operations remain
 * the existing ViewModel calls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val ownDevice by viewModel.ownDevice.collectAsStateWithLifecycle()
    val joinState by viewModel.joinState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var showJoinSheet by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }

    val paired = ownDevice?.isActive == true

    PvScaffold(title = "This device", onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "hero") {
                if (paired) {
                    PvStatusHero(
                        icon = Icons.Filled.CheckCircle,
                        tint = statusPositive(),
                        title = "Paired",
                        headline = "Connected",
                        body = buildString {
                            append("This phone announces payments for your owner")
                            ownDevice?.lastSeenAtMs?.takeIf { it > 0 }?.let {
                                append(" · last active ${timeAgo(it)}")
                            }
                            append(".")
                        },
                        chip = "Live",
                        chipOk = true,
                    )
                } else {
                    PvStatusHero(
                        icon = Icons.Filled.Warning,
                        tint = statusCaution(),
                        title = "Not paired",
                        headline = "Not connected",
                        body = "Ask the business owner for a code, then enter it below.",
                        chip = "Not connected",
                        chipOk = null,
                        actionLabel = "Enter code",
                        onAction = { showJoinSheet = true },
                    )
                }
            }

            item(key = "connection") {
                PvSection(title = "Connection", carded = true) {
                    StatusLine(paired, if (paired) "Connected" else "Degraded — not paired")
                    Text(
                        if (paired) {
                            "Payments received by the owner phone are announced here."
                        } else {
                            "Join a business to start receiving announcements."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "battery") {
                PvSection(title = "Battery optimization", carded = true) {
                    val exempt = status?.batteryExempt == true
                    StatusLine(exempt, if (exempt) "Unrestricted" else "Restricted — payments may arrive late")
                    if (!exempt) {
                        OutlinedButton(onClick = { viewModel.fixBattery(context) }) {
                            Text("Fix")
                        }
                    }
                }
            }

            item(key = "notifications") {
                PvSection(title = "Notifications", carded = true) {
                    val granted = status?.notificationsEnabled == true
                    StatusLine(granted, if (granted) "Allowed" else "Not allowed")
                    if (!granted) {
                        OutlinedButton(onClick = {
                            SetupNotifications.ensureChannels(context)
                            viewModel.openAppNotificationSettings(context)
                        }) {
                            Text("Fix")
                        }
                    }
                }
            }

            if (paired) {
                item(key = "test") {
                    PvSection(title = "Try it out", carded = true) {
                        OutlinedButton(onClick = { viewModel.speakTest() }) {
                            Text("Hear a test announcement")
                        }
                        Text(
                            "Plays: \"PayVoice test announcement.\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item(key = "last-payment") {
                    PvSection(title = "Last payment announced", carded = true) {
                        // Observe, don't .value-read: StateFlow.value inside
                        // composition skips recomposition on updates (lint
                        // StateFlowValueCalledInComposition).
                        val history by viewModel.history.collectAsStateWithLifecycle()
                        val last = history.firstOrNull()
                        if (last == null) {
                            PvEmptyHint(
                                text = "Nothing yet — payments announced here appear below.",
                                icon = Icons.Filled.Notifications,
                            )
                        } else {
                            PvPaymentRow(
                                amountText = AmountExtractor.formatMinor(last.amountMinor, last.currency),
                                source = last.sourceName,
                                sender = last.senderName,
                                timeText = timeAgo(last.announcedAtMs),
                            )
                        }
                    }
                }
            }

            item(key = "leave") {
                PvSection(title = if (paired) "Remove pairing" else "Have a code?", carded = true) {
                    if (paired) {
                        OutlinedButton(onClick = { confirmLeave = true }) {
                            Text("Leave this business", color = MaterialTheme.colorScheme.error)
                        }
                        Text(
                            "You'll stop hearing payment announcements from your owner. " +
                                "Payments already announced stay on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Button(onClick = { showJoinSheet = true }) {
                            Text("Join with a code")
                        }
                    }
                }
            }
        }
    }

    // ---- Join sheet (was AlertDialog) ----------------------------------------
    if (showJoinSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                if (joinState !is MainViewModel.JoinState.Joining) {
                    showJoinSheet = false
                    viewModel.clearJoinResult()
                }
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Join an owner", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Enter the code provided by your owner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Code (PAY-XXXXXX)") },
                    singleLine = true,
                    enabled = joinState !is MainViewModel.JoinState.Joining,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (val s = joinState) {
                    is MainViewModel.JoinState.Joining -> PvLoadingRow("Connecting…")
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
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(
                        onClick = { viewModel.joinOwner(code) },
                        enabled = joinState !is MainViewModel.JoinState.Joining && code.isNotBlank(),
                    ) { Text("Connect") }
                    TextButton(
                        onClick = {
                            showJoinSheet = false
                            viewModel.clearJoinResult()
                        },
                        enabled = joinState !is MainViewModel.JoinState.Joining,
                    ) { Text("Cancel") }
                }
            }
        }
    }

    // ---- Leave confirmation sheet (destructive confirm) ----------------------
    if (confirmLeave) {
        ModalBottomSheet(onDismissRequest = { confirmLeave = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Leave this business?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "You'll stop hearing payment announcements from your owner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = {
                        viewModel.leaveOwner()
                        confirmLeave = false
                    }) {
                        Text("Leave", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
                }
            }
        }
    }
}
