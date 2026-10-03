package com.vivekray898.payvoice.ui.parenthome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.parser.AmountExtractor
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.service.status.DeviceStatusMonitor
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.BellIllustration
import com.vivekray898.payvoice.ui.components.IconTile
import com.vivekray898.payvoice.ui.components.MoneyText
import com.vivekray898.payvoice.ui.components.PaymentsEmptyIllustration
import com.vivekray898.payvoice.ui.components.PvDivider
import com.vivekray898.payvoice.ui.components.PvPaymentRow
import com.vivekray898.payvoice.ui.components.PvSecondaryButton
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.StorefrontHero
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Home (GPay-Business structure rebuild): custom header (business name +
 * chevron + avatar) over a full-bleed Canvas storefront hero, then the
 * greeting, the action card with an inline text-link CTA (only when
 * something needs attention), the today-amount numeric block with the
 * payments empty state / recent list, and the quick-links row. The
 * scaffold's topBar slot stays empty so the hero bleeds under the status
 * bar; bottom clearance = nav-bar inset + 24dp.
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
    val ownDevice by viewModel.ownDevice.collectAsStateWithLifecycle()

    val role = settings.role
    val listenerOk = status?.listenerEnabled == true
    val notifOk = status?.notificationsEnabled == true
    val paired = ownDevice?.isActive == true
    val needsAction = when (role) {
        DeviceRole.EMPLOYEE -> !paired
        else -> !(listenerOk && notifOk)
    }
    val todayMinor = history.filter { it.announcedAtMs >= startOfToday() }.sumOf { it.amountMinor }
    val displayName = settings.deviceName.ifBlank {
        when (role) {
            DeviceRole.EMPLOYEE -> "This phone"
            else -> "Your business"
        }
    }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    PvScaffold(topBar = {}) { _ ->
        LazyColumn(Modifier.fillMaxSize()) {
            // 1. Custom app bar (transparent, over nothing) + 2. bleeding hero
            item(key = "hero") {
                Box(Modifier.fillMaxWidth()) {
                    StorefrontHero(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Spacing.huge * 3 + Spacing.sm), // ~200dp structural
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = statusBarPadding + Spacing.sm,
                                start = Spacing.xl - Spacing.xs, // 20dp structural side padding
                                end = Spacing.xl - Spacing.xs,
                            )
                            .height(Spacing.xxl + Spacing.lg), // 48dp structural bar
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "PayVoice",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }

            // 3. Greeting block
            item(key = "greeting") {
                Column(
                    Modifier.padding(
                        start = Spacing.xl - Spacing.xs,
                        end = Spacing.xl - Spacing.xs,
                        top = Spacing.xl,
                    ),
                ) {
                    Text(
                        text = "Hello, $displayName",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = if (needsAction) {
                            "Complete pending actions now"
                        } else {
                            "You're all set. Payments will appear here."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 4. Primary action card (only when action required)
            if (needsAction) {
                item(key = "action") {
                    ActionCard(
                        modifier = Modifier.padding(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            top = Spacing.xl,
                        ),
                        icon = when (role) {
                            DeviceRole.EMPLOYEE -> Icons.Filled.Link
                            else -> Icons.Filled.Notifications
                        },
                        badge = role != DeviceRole.EMPLOYEE,
                        title = when (role) {
                            DeviceRole.EMPLOYEE -> "Connect to your owner"
                            else -> "Turn on all notifications"
                        },
                        description = when (role) {
                            DeviceRole.EMPLOYEE ->
                                "Pair this phone with your owner's code to hear payments live"
                            else ->
                                "Get audio alerts & timely notifications when customers pay you"
                        },
                        cta = when (role) {
                            DeviceRole.EMPLOYEE -> "Enter code now"
                            else -> "Turn on now"
                        },
                        onAction = when (role) {
                            DeviceRole.EMPLOYEE -> onOpenEmployeeRemote
                            else -> onOpenReliability
                        },
                    )
                }
            }

            // 5. Numeric data block
            item(key = "today") {
                Column(
                    Modifier.padding(
                        start = Spacing.xl - Spacing.xs,
                        end = Spacing.xl - Spacing.xs,
                        top = Spacing.xxl,
                    ),
                ) {
                    MoneyText(
                        amountMinor = todayMinor,
                        modifier = Modifier,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        "Received today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Payments: empty state or recent list
            item(key = "payments") {
                Column(
                    Modifier.padding(horizontal = Spacing.xl - Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (history.isEmpty()) {
                        Spacer(Modifier.height(Spacing.xl))
                        PaymentsEmptyIllustration()
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            "Recent payments from customers will be shown here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.xxl),
                        )
                        Spacer(Modifier.height(Spacing.xl))
                    } else {
                        history.take(5).forEachIndexed { index, entry ->
                            if (index == 0) Spacer(Modifier.height(Spacing.lg))
                            if (index > 0) PvDivider()
                            PvPaymentRow(
                                amountText = AmountExtractor.formatMinor(entry.amountMinor, entry.currency),
                                source = entry.sourceName,
                                sender = entry.senderName,
                                timeText = timeAgo(entry.announcedAtMs),
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                    }
                    PvSecondaryButton(
                        text = "Show all payments",
                        onClick = onOpenDiagnostics,
                        enabled = history.isNotEmpty(),
                    )
                }
            }

            // 6. Quick links
            item(key = "quick-links") {
                Column(
                    Modifier.padding(
                        start = Spacing.xl - Spacing.xs,
                        end = Spacing.xl - Spacing.xs,
                        top = Spacing.xxl,
                    ),
                ) {
                    Text(
                        "Quick links",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        QuickLink(icon = Icons.Filled.MonitorHeart, label = "Diagnostics", onClick = onOpenDiagnostics)
                        QuickLink(
                            icon = Icons.Filled.People,
                            label = "Employees",
                            onClick = if (role == DeviceRole.EMPLOYEE) onOpenEmployeeRemote else onOpenOwnerRemote,
                        )
                        QuickLink(icon = Icons.Filled.Settings, label = "Settings", onClick = onOpenSettings)
                        QuickLink(icon = Icons.Filled.Build, label = "Reliability", onClick = onOpenReliability)
                    }
                }
            }

            // 7. Bottom padding = nav-bar inset + 24dp
            item(key = "footer") {
                val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Spacer(Modifier.height(navPad + Spacing.xxl))
            }
        }
    }
}

/** The GPay-Business action card: semantically correct icon art, bold
 *  headline, muted two-line description, inline text-link CTA below. */
@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: Boolean,
    title: String,
    description: String,
    cta: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onAction),
        shape = RoundedCornerShape(Spacing.xl), // rounded.xl = 16dp
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(Spacing.xl - Spacing.xs)) { // 20dp structural padding
            Row(verticalAlignment = Alignment.CenterVertically) {
                BellIllustration(icon = icon, badge = badge)
                Spacer(Modifier.width(Spacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onAction) {
                Text(cta, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Quick link: 56dp icon tile + label, >= 72dp total tap height. */
@Composable
private fun QuickLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(Spacing.xs),
    ) {
        IconTile(icon = icon)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun startOfToday(): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
