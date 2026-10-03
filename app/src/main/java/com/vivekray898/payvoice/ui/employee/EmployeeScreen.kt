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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvBottomSheet
import com.vivekray898.payvoice.ui.components.PvCard
import com.vivekray898.payvoice.ui.components.PvCodeField
import com.vivekray898.payvoice.ui.components.PvDialog
import com.vivekray898.payvoice.ui.components.PvEmptyHint
import com.vivekray898.payvoice.ui.components.PvHealthBanner
import com.vivekray898.payvoice.ui.components.PvListItem
import com.vivekray898.payvoice.ui.components.PvLoadingRow
import com.vivekray898.payvoice.ui.components.PvPaymentRow
import com.vivekray898.payvoice.ui.components.PvPrimaryButton
import com.vivekray898.payvoice.ui.components.PvSecondaryButton
import com.vivekray898.payvoice.ui.components.PvSecureWindow
import com.vivekray898.payvoice.ui.components.PvSliderRow
import com.vivekray898.payvoice.ui.components.PvSupportingText
import com.vivekray898.payvoice.ui.components.PvTab
import com.vivekray898.payvoice.ui.components.PvTabScaffold
import com.vivekray898.payvoice.ui.components.StatusPill
import com.vivekray898.payvoice.ui.components.StatusTone
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Pair tab (employee role): what this phone is connected to, how to join an
 * owner, and the one control an employee actually needs on a phone they do
 * not control — the announcement volume.
 *
 * Health lives here as the same [PvHealthBanner] Home shows, because for an
 * employee "is this phone ready" and "am I paired" are the same question: a
 * phone that has lost notification access loses payments silently.
 *
 * [PvSecureWindow] stays while the pairing sheet is on screen — the code
 * being typed is a live single-use credential.
 */
@Composable
fun EmployeeScreen(
    viewModel: MainViewModel,
    selected: PvTab,
    onSelectTab: (PvTab) -> Unit,
    onOpenHealth: () -> Unit = {},
) {
    val context = LocalContext.current
    val ownDevice by viewModel.ownDevice.collectAsStateWithLifecycle()
    val joinState by viewModel.joinState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val healthLoading by viewModel.healthLoading.collectAsStateWithLifecycle()
    var showJoinSheet by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }

    val paired = ownDevice?.isActive == true

    PvSecureWindow()

    PvTabScaffold(
        role = DeviceRole.EMPLOYEE,
        selected = selected,
        onSelect = onSelectTab,
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
            item(key = "title") {
                Text(
                    text = "This device",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            }

            item(key = "health") {
                PvHealthBanner(
                    summary = health ?: com.vivekray898.payvoice.core.health.HealthSummary(
                        emptyList(),
                    ),
                    loading = healthLoading,
                    onFix = { item ->
                        if (item.inAppAction ==
                            com.vivekray898.payvoice.core.health.InAppAction.PAIR_DEVICE
                        ) {
                            // An employee never mints a code — they enter one.
                            viewModel.clearJoinResult()
                            showJoinSheet = true
                        } else {
                            viewModel.applyHealthFix(context, item)
                        }
                    },
                    onOpenDetail = onOpenHealth,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }

            item(key = "status") {
                PvCard(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (paired) {
                                "This phone announces payments for your owner."
                            } else {
                                "Join with a code from your owner to start announcing."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(Spacing.md))
                        StatusPill(
                            text = if (paired) "Live" else "Not connected",
                            tone = if (paired) StatusTone.Success else StatusTone.Warning,
                        )
                    }
                    if (!paired) {
                        PvPrimaryButton(
                            text = "Enter code",
                            onClick = {
                                showJoinSheet = true
                                viewModel.clearJoinResult()
                            },
                        )
                    }
                }
            }

            item(key = "voice") {
                PvCard(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    Text(text = "Announcement volume", style = MaterialTheme.typography.titleMedium)
                    PvSliderRow(
                        label = "How loud",
                        valueText = "%d%%".format((settings.speechVolume * 100).toInt()),
                        value = settings.speechVolume,
                        onValueChange = viewModel::setSpeechVolume,
                        valueRange = 0f..1f,
                    )
                    PvSecondaryButton(text = "Test voice", onClick = viewModel::speakTest)
                }
            }

            item(key = "last") {
                PvCard(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    val history by viewModel.history.collectAsStateWithLifecycle()
                    val last = history.firstOrNull()
                    Text(
                        text = "Last payment announced",
                        style = MaterialTheme.typography.titleMedium,
                    )
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

            item(key = "settings") {
                PvListItem(
                    title = "Notifications",
                    subtitle = "Without these, payments arrive late",
                    leadingIcon = Icons.Filled.Link,
                    onClick = { onSelectTab(PvTab.SETTINGS) },
                    minHeight = Spacing.listRow,
                )
            }

            if (paired) {
                item(key = "leave") {
                    PvSecondaryButton(
                        text = "Leave pairing",
                        onClick = { confirmLeave = true },
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }
            }
        }
    }

    // ---- Join sheet ---------------------------------------------------------
    if (showJoinSheet) {
        PvBottomSheet(
            onDismissRequest = {
                if (joinState !is MainViewModel.JoinState.Joining) {
                    showJoinSheet = false
                    viewModel.clearJoinResult()
                }
            },
            title = "Join an owner",
        ) {
            PvSupportingText(text = "Enter the code provided by your owner.")
            val failed = joinState as? MainViewModel.JoinState.Failed
            PvCodeField(
                value = code,
                onValueChange = { code = it },
                enabled = joinState !is MainViewModel.JoinState.Joining,
                isError = failed != null,
                supportingText = failed?.message,
            )
            when (joinState) {
                is MainViewModel.JoinState.Joining -> PvLoadingRow("Connecting…")
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PvPrimaryButton(
                    text = "Connect",
                    onClick = { viewModel.joinOwner(code) },
                    enabled = joinState !is MainViewModel.JoinState.Joining && code.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
                PvSecondaryButton(
                    text = "Cancel",
                    onClick = {
                        showJoinSheet = false
                        viewModel.clearJoinResult()
                    },
                )
            }
            Spacer(Modifier.height(Spacing.xs))
        }
    }

    // ---- Leave confirmation ------------------------------------------------
    if (confirmLeave) {
        PvDialog(
            onDismissRequest = { confirmLeave = false },
            title = "Leave this business?",
            body = "You'll stop hearing payment announcements from your owner.",
            confirmText = "Leave",
            onConfirm = {
                viewModel.leaveOwner()
                confirmLeave = false
            },
        )
    }
}