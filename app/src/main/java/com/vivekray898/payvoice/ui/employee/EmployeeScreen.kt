package com.vivekray898.payvoice.ui.employee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.parser.AmountExtractor
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvDivider
import com.vivekray898.payvoice.ui.components.PvEmptyHint
import com.vivekray898.payvoice.ui.components.PvLoadingRow
import com.vivekray898.payvoice.ui.components.PvPaymentRow
import com.vivekray898.payvoice.ui.components.HeroBanner
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSecondaryButton
import com.vivekray898.payvoice.ui.components.ScreenHeader
import com.vivekray898.payvoice.ui.components.SectionHeader
import com.vivekray898.payvoice.ui.components.StatusLine
import com.vivekray898.payvoice.ui.components.StatusPill
import com.vivekray898.payvoice.ui.components.StatusTone
import com.vivekray898.payvoice.ui.components.statusToneOf
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * This device (DESIGN.md rebuild, Phase 3d): PvScaffold + PvTopBar, hero
 * surface with pairing StatusPill, status cards (Connection / Battery /
 * Notifications) with inline Fix actions, last payment, and a destructive
 * leave action. Join/leave stay in ModalBottomSheets; all VM calls
 * unchanged.
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

    PvScaffold(
        topBar = {},
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = Spacing.xxl + inner.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "header") {
                ScreenHeader(title = "This device", onBack = onBack)
                HeroBanner(modifier = Modifier.padding(top = Spacing.md))
            }

            item(key = "hero") {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = Spacing.xxs,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "This device",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(
                                text = if (paired) "Live" else "Not connected",
                                tone = if (paired) StatusTone.Success else StatusTone.Warning,
                            )
                        }
                        Text(
                            if (paired) "This phone announces payments for your owner."
                            else "Join with a code from your owner to start announcing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!paired) {
                            Spacer(Modifier.height(Spacing.xs))
                            Button(
                                onClick = { showJoinSheet = true },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                            ) { Text("Enter code") }
                        }
                    }
                }
            }

            item(key = "connection") {
                StatusCard(title = "Connection") {
                    StatusLine(paired, if (paired) "Connected" else "Degraded — not paired")
                    Text(
                        if (paired) "Payments received by the owner phone are announced here."
                        else "Join a business to start receiving announcements.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "battery") {
                StatusCard(title = "Battery") {
                    val exempt = status?.batteryExempt == true
                    StatusLine(exempt, if (exempt) "Unrestricted" else "Restricted — payments may arrive late")
                    if (!exempt) {
                        OutlinedButton(
                            onClick = { viewModel.fixBattery(context) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                        ) { Text("Fix") }
                    }
                }
            }

            item(key = "notifications") {
                StatusCard(title = "Notifications") {
                    val granted = status?.notificationsEnabled == true
                    StatusLine(granted, if (granted) "Allowed" else "Not allowed")
                    if (!granted) {
                        OutlinedButton(
                            onClick = {
                                SetupNotifications.ensureChannels(context)
                                viewModel.openAppNotificationSettings(context)
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                        ) { Text("Fix") }
                    }
                }
            }

            if (paired) {
                item(key = "test") {
                    StatusCard(title = "Try it out") {
                        OutlinedButton(
                            onClick = { viewModel.speakTest() },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                        ) { Text("Hear a test announcement") }
                    }
                }
                item(key = "last-payment") {
                    StatusCard(title = "Last payment announced") {
                        val history by viewModel.history.collectAsStateWithLifecycle()
                        val last = history.firstOrNull()
                        if (last == null) {
                            PvEmptyHint(text = "Nothing yet — payments announced here appear below.")
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
                OutlinedButton(
                    onClick = { confirmLeave = true },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                ) { Text("Leave pairing") }
            }
        }
    }

    // ---- Join sheet ---------------------------------------------------------
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
                Text("Join an owner", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Enter the code provided by your owner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(10) },
                    label = { Text("Pairing code") },
                    singleLine = true,
                    enabled = joinState !is MainViewModel.JoinState.Joining,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (val s = joinState) {
                    is MainViewModel.JoinState.Joining -> PvLoadingRow("Connecting…")
                    is MainViewModel.JoinState.Failed -> Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Button(
                        onClick = { viewModel.joinOwner(code) },
                        enabled = joinState !is MainViewModel.JoinState.Joining && code.isNotBlank(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                    ) { Text("Connect") }
                    TextButton(onClick = {
                        showJoinSheet = false
                        viewModel.clearJoinResult()
                    }) { Text("Cancel") }
                }
            }
        }
    }

    // ---- Leave confirmation sheet -------------------------------------------
    if (confirmLeave) {
        ModalBottomSheet(onDismissRequest = { confirmLeave = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Leave this business?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "You'll stop hearing payment announcements from your owner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.leaveOwner()
                            confirmLeave = false
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                    ) { Text("Leave", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column {
        SectionHeader(text = title)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Spacing.xxs,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
}
