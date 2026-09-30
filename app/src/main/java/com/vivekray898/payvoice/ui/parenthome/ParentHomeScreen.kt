package com.vivekray898.payvoice.ui.parenthome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Build
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvActionCard
import com.vivekray898.payvoice.ui.components.PvDivider
import com.vivekray898.payvoice.ui.components.PvEmptyState
import com.vivekray898.payvoice.ui.components.PvPaymentRow
import com.vivekray898.payvoice.ui.components.PvRow
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.PvStatusHero
import com.vivekray898.payvoice.ui.components.statusNegative
import com.vivekray898.payvoice.ui.components.statusPositive
import com.vivekray898.payvoice.ui.components.timeAgo
import java.util.Calendar

/**
 * Home (UI overhaul Phase 3b): GPay-Business-style — calm header, one hero
 * status card, recent payments, then tappable action cards. Own Scaffold
 * with safeDrawing insets: the header clears the status bar, the list's
 * bottom contentPadding includes the nav bar (last card never hides under
 * it). ViewModel wiring untouched.
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(start = 20.dp, end = 8.dp)
                    .heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("PayVoice", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        greeting(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            // Bottom padding INCLUDES the nav bar so the last card clears it.
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                            headline = "ON",
                            body = "You're ready to announce payments.",
                        )
                    } else {
                        PvStatusHero(
                            icon = Icons.Filled.Warning,
                            tint = statusNegative(),
                            title = "Payment announcements",
                            headline = "Action needed",
                            body = "Allow notification access so PayVoice can hear your payments.",
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
                                    amountText = com.vivekray898.payvoice.core.parser.AmountExtractor
                                        .formatMinor(entry.amountMinor, entry.currency),
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (settings.role) {
                            DeviceRole.OWNER -> PvActionCard(
                                icon = Icons.Filled.Person,
                                title = "Employees",
                                subtitle = "Devices that hear your payment announcements",
                                onClick = onOpenOwnerRemote,
                            )
                            DeviceRole.EMPLOYEE -> PvActionCard(
                                icon = Icons.Filled.Add,
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

            item(key = "footer") { Spacer(Modifier.height(8.dp)) }
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
            headline = "Connected",
            body = "This phone announces your owner's payments.",
            actionLabel = "View connection",
            onAction = onOpen,
        )
    } else {
        PvStatusHero(
            icon = Icons.Filled.Warning,
            tint = com.vivekray898.payvoice.ui.components.statusCaution(),
            title = "Remote announcements",
            headline = "Not connected",
            body = "Join with a code from your owner to start announcing.",
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
