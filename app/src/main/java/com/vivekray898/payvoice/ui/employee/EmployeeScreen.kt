package com.vivekray898.payvoice.ui.employee

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvPaymentRow
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.PvStatusHero
import com.vivekray898.payvoice.ui.components.SectionCard
import com.vivekray898.payvoice.ui.components.StatusLine
import com.vivekray898.payvoice.ui.components.statusCaution
import com.vivekray898.payvoice.ui.components.statusPositive
import com.vivekray898.payvoice.ui.components.timeAgo

/**
 * This device (UI overhaul Phase 3d): one hero for pairing state, then
 * status cards with INLINE fix actions (battery / notifications / SMS).
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

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        PayVoiceAnalytics.smsPermission(
            if (granted) PayVoiceAnalytics.PermissionResult.GRANTED
            else PayVoiceAnalytics.PermissionResult.DENIED,
        )
        viewModel.refreshStatus()
    }

    PvScaffold(title = "This device", onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    )
                } else {
                    PvStatusHero(
                        icon = Icons.Filled.Warning,
                        tint = statusCaution(),
                        title = "Not paired",
                        headline = "Not connected",
                        body = "Ask the business owner for a code, then enter it below.",
                        actionLabel = "Enter code",
                        onAction = { showJoinSheet = true },
                    )
                }
            }

            item(key = "connection") {
                SectionCard(title = "Connection") {
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
                SectionCard(title = "Battery optimization") {
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
                SectionCard(title = "Notifications") {
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

            item(key = "sms") {
                SectionCard(title = "SMS backup") {
                    val granted = status?.smsPermissionGranted == true
                    StatusLine(granted, if (granted) "Enabled" else "Not granted")
                    Text(
                        "When data is off, the bank's SMS still confirms the payment. " +
                            "Processed locally — never uploaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!granted) {
                        OutlinedButton(onClick = {
                            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        }) {
                            Text("Fix")
                        }
                        TextButton(onClick = { viewModel.openAppDetailsSettings(context) }) {
                            Text("Or open App settings")
                        }
                    }
                }
            }

            if (paired) {
                item(key = "test") {
                    SectionCard(title = "Try it out") {
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
                    SectionCard(title = "Last payment announced") {
                        // Observe, don't .value-read: StateFlow.value inside
                        // composition skips recomposition on updates (lint
                        // StateFlowValueCalledInComposition).
                        val history by viewModel.history.collectAsStateWithLifecycle()
                        val last = history.firstOrNull()
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
                SectionCard(title = if (paired) "Remove pairing" else "Have a code?") {
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

            item(key = "footer") { Spacer(Modifier.height(8.dp)) }
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
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Leave this business?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "You'll stop hearing payment announcements from your owner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
