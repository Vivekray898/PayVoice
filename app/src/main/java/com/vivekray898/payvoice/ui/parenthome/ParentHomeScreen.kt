package com.vivekray898.payvoice.ui.parenthome

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.parser.AmountExtractor
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvActionCard
import com.vivekray898.payvoice.ui.components.PvDivider
import com.vivekray898.payvoice.ui.components.PvEmptyState
import com.vivekray898.payvoice.ui.components.PvPaymentRow
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.PvStatusHero
import com.vivekray898.payvoice.ui.components.statusCaution
import com.vivekray898.payvoice.ui.components.statusNegative
import com.vivekray898.payvoice.ui.components.statusPositive
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.theme.Spacing
import java.util.Calendar

/**
 * Home (premium pass, UI overhaul Phase 3): large title + greeting in the
 * shared [PvScaffold] chrome, gradient status hero with icon circle and
 * status chip, and icon-circle action cards. List is inset by the scaffold
 * (status/nav bars handled centrally); only aesthetic padding here.
 * ViewModel wiring untouched.
 */
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

    val listenerOk = status?.listenerEnabled == true
    val notifOk = status?.notificationsEnabled == true
    val ready = listenerOk && notifOk
    val needsAttention = !ready
    val isEmployee = settings.role == DeviceRole.EMPLOYEE

    PvScaffold(
        title = "PayVoice",
        subtitle = greeting(),
        largeTitle = true,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "hero") {
                if (isEmployee) {
                    EmployeeHero(viewModel, onOpen = onOpenEmployeeRemote)
                } else {
                    if (ready) {
                        PvStatusHero(
                            icon = Icons.Filled.CheckCircle,
                            tint = statusPositive(),
                            title = "Payment announcements",
                            headline = "Announcements on",
                            body = "PayVoice speaks every payment as it arrives.",
                            chip = "Ready",
                            chipOk = true,
                        )
                    } else {
                        PvStatusHero(
                            icon = Icons.Filled.Warning,
                            tint = statusNegative(),
                            title = "Payment announcements",
                            headline = "Setup incomplete",
                            body = "Allow notification access so PayVoice can hear your payments.",
                            chip = "Action needed",
                            chipOk = false,
                            actionLabel = "Fix this",
                            onAction = onOpenReliability,
                        )
                    }
                }
            }

            item(key = "payments") {
                PvSection(title = if (history.isEmpty()) null else "Recent payments") {
                    when {
                        history.isEmpty() -> PvEmptyState(
                            title = "No payments yet",
                            body = "Payments will appear here when they are detected.",
                            actionLabel = if (needsAttention) "Check setup" else null,
                            onAction = if (needsAttention) onOpenReliability else null,
                        )
                        else -> {
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
            }

            item(key = "role-entry") {
                when (settings.role) {
                    DeviceRole.UNSET -> PvSection(title = "Announce on more devices") {
                        Text(
                            "Let another phone announce the same payments — for a shop " +
                                "counter or another staff member.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(onClick = { viewModel.setRole(DeviceRole.OWNER, Build.MODEL ?: "Owner") }) {
                                Text("I'm the owner")
                            }
                            OutlinedButton(onClick = { viewModel.setRole(DeviceRole.EMPLOYEE, Build.MODEL ?: "Employee") }) {
                                Text("I'm an employee")
                            }
                        }
                    }
                    else -> Unit // hero covers the role entry point via the cards below
                }
            }

            item(key = "cards") {
                PvSection(title = "Manage") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        when (settings.role) {
                            DeviceRole.OWNER -> PvActionCard(
                                icon = Icons.Filled.Group,
                                title = "Employees",
                                subtitle = "Devices that hear your payment announcements",
                                onClick = onOpenOwnerRemote,
                            )
                            DeviceRole.EMPLOYEE -> PvActionCard(
                                icon = Icons.Filled.Link,
                                title = "Pair this device",
                                subtitle = "Join a business with a pairing code",
                                onClick = onOpenEmployeeRemote,
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
                }
            }
        }
    }
}

@Composable
private fun EmployeeHero(viewModel: MainViewModel, onOpen: () -> Unit) {
    val own by viewModel.ownDevice.collectAsStateWithLifecycle()
    val connected = own?.isActive == true
    if (connected) {
        PvStatusHero(
            icon = Icons.Filled.CheckCircle,
            tint = statusPositive(),
            title = "Remote announcements",
            headline = "Connected to your owner",
            body = "This phone announces your owner's payments.",
            chip = "Live",
            chipOk = true,
            actionLabel = "View connection",
            onAction = onOpen,
        )
    } else {
        PvStatusHero(
            icon = Icons.Filled.Link,
            tint = statusCaution(),
            title = "Remote announcements",
            headline = "Waiting for a pairing code",
            body = "Join with a code from your owner to start announcing.",
            chip = "Not connected",
            chipOk = null,
            actionLabel = "Join",
            onAction = onOpen,
        )
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Hello"
    }
}
