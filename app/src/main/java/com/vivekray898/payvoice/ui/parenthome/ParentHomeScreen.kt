package com.vivekray898.payvoice.ui.parenthome

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.parser.AmountExtractor
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.service.status.DeviceStatusMonitor
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.MoneyText
import com.vivekray898.payvoice.ui.components.PvActionCard
import com.vivekray898.payvoice.ui.components.PvDivider
import com.vivekray898.payvoice.ui.components.PvEmptyHint
import com.vivekray898.payvoice.ui.components.PvPaymentRow
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.StatusPill
import com.vivekray898.payvoice.ui.components.StatusTone
import com.vivekray898.payvoice.ui.components.SectionHeader
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Home (DESIGN.md rebuild, Phase 3b): LargeTopAppBar "PayVoice", the hero
 * card (role + status pill + one human line), then the Manage action cards.
 * No role selector — role is set during onboarding and read-only here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentHomeScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenReliability: () -> Unit,
    onOpenOwnerRemote: () -> Unit = {},
    onOpenEmployeeRemote: () -> Unit = {},
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ownDevice by viewModel.ownDevice.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val role = settings.role
    val tone = homeTone(status, role, ownDevice?.isActive == true)
    val statusText = homeStatusText(role, status, ownDevice?.isActive == true)

    PvScaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("PayVoice", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = Spacing.xxl + inner.calculateBottomPadding()),
        ) {
            item(key = "hero") {
                HeroCard(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    role = role,
                    statusText = statusText,
                    tone = tone,
                )
            }
            item(key = "history") {
                Column(Modifier.padding(horizontal = Spacing.lg)) {
                    SectionHeader(text = "Recent payments")
                    if (history.isEmpty()) {
                        PvEmptyHint(
                            text = "Payments will appear here when they are detected.",
                        )
                    } else {
                        history.take(6).forEachIndexed { index, entry ->
                            if (index > 0) PvDivider()
                            PvPaymentRow(
                                amountText = AmountExtractor.formatMinor(
                                    entry.amountMinor,
                                    entry.currency,
                                ),
                                source = entry.sourceName,
                                sender = entry.senderName,
                                timeText = timeAgo(entry.announcedAtMs),
                            )
                        }
                    }
                }
            }
            item(key = "manage") {
                Column(Modifier.padding(horizontal = Spacing.lg)) {
                    SectionHeader(text = if (role == DeviceRole.EMPLOYEE) "This device" else "Manage")
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        when (role) {
                            DeviceRole.EMPLOYEE -> PvActionCard(
                                icon = Icons.Filled.Link,
                                title = "Pair status",
                                subtitle = statusText,
                                onClick = onOpenEmployeeRemote,
                            )
                            DeviceRole.OWNER -> PvActionCard(
                                icon = Icons.Filled.Group,
                                title = "Employees",
                                subtitle = "Devices that hear your payment announcements",
                                onClick = onOpenOwnerRemote,
                            )
                            DeviceRole.UNSET -> Unit
                        }
                        PvActionCard(
                            icon = Icons.Filled.Info,
                            title = "Diagnostics",
                            subtitle = "Technical logs and captured notifications",
                            onClick = onOpenDiagnostics,
                        )
                        PvActionCard(
                            icon = Icons.Filled.Build,
                            title = "Reliability",
                            subtitle = "Permissions, battery and connection repair",
                            onClick = onOpenReliability,
                        )
                        PvActionCard(
                            icon = Icons.Filled.Settings,
                            title = "Settings",
                            subtitle = "Voice, announcements & more",
                            onClick = onOpenSettings,
                        )
                    }
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    role: DeviceRole,
    statusText: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge, // rounded.xl
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Spacing.xxs,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (role) {
                        DeviceRole.OWNER -> "Owner"
                        DeviceRole.EMPLOYEE -> "Employee"
                        DeviceRole.UNSET -> "Welcome"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    text = when (tone) {
                        StatusTone.Success -> "Ready"
                        StatusTone.Error -> "Action needed"
                        StatusTone.Warning -> "Waiting"
                        StatusTone.Neutral -> "Idle"
                    },
                    tone = tone,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun homeTone(
    status: DeviceStatusMonitor.Snapshot?,
    role: DeviceRole,
    paired: Boolean,
): StatusTone = when (role) {
    DeviceRole.EMPLOYEE -> if (paired) StatusTone.Success else StatusTone.Warning
    else -> when {
        status == null -> StatusTone.Neutral
        status.listenerEnabled && status.notificationsEnabled -> StatusTone.Success
        else -> StatusTone.Error
    }
}

private fun homeStatusText(
    role: DeviceRole,
    status: DeviceStatusMonitor.Snapshot?,
    paired: Boolean,
): String = when (role) {
    DeviceRole.EMPLOYEE ->
        if (paired) "This phone announces your owner's payments."
        else "Join with a code from your owner to start announcing."
    else -> when {
        status == null -> "Checking this device…"
        status.listenerEnabled && status.notificationsEnabled ->
            "PayVoice speaks every payment as it arrives."
        else -> "Allow notification access so PayVoice can hear your payments."
    }
}
