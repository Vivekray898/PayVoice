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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.health.HealthSummary
import com.vivekray898.payvoice.core.health.InAppAction
import com.vivekray898.payvoice.core.parser.AmountExtractor
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.service.status.DeviceStatusMonitor
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.IconTile
import com.vivekray898.payvoice.ui.components.MoneyText
import com.vivekray898.payvoice.ui.components.PvHealthBanner
import com.vivekray898.payvoice.ui.components.PvIconButton
import com.vivekray898.payvoice.ui.components.PvTab
import com.vivekray898.payvoice.ui.components.PvTabScaffold

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
    selected: PvTab,
    onSelectTab: (PvTab) -> Unit,
    onOpenHealth: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ownDevice by viewModel.ownDevice.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val healthLoading by viewModel.healthLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val role = settings.role
    val todayMinor = history.filter { it.announcedAtMs >= startOfToday() }.sumOf { it.amountMinor }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // The hero deliberately draws under the status bar, so the top inset is
    // applied to the app-bar row rather than to the list; the bottom inset and
    // the tab bar height come from the scaffold's own padding.
    PvTabScaffold(role = role, selected = selected, onSelect = onSelectTab) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = inner.calculateBottomPadding() + Spacing.xxl,
            ),
        ) {
            // 1. Custom app bar (transparent, over nothing) + 2. bleeding hero
            item(key = "hero") {
                Box(Modifier.fillMaxWidth()) {
                    StorefrontHero(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Spacing.hero),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = statusBarPadding + Spacing.sm,
                                start = Spacing.gutter,
                                end = Spacing.gutter,
                            )
                            .height(Spacing.rowBar),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "PayVoice",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        PvIconButton(
                            icon = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            onClick = { onSelectTab(PvTab.SETTINGS) },
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            // 3. Greeting block
            item(key = "greeting") {
                Column(
                    Modifier.padding(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                        top = Spacing.xl,
                    ),
                ) {
                    Text(
                        text = if (history.isEmpty()) "Hello" else "Welcome back",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    val healthSummary = health
                    Text(
                        text = if (healthSummary == null || healthSummary.healthy) {
                            "You're all set. Payments will appear here."
                        } else if (healthSummary.attentionCount == 1) {
                            "One thing needs your attention below."
                        } else {
                            "A few things need your attention below."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 4. Health banner — one verdict first, expandable to the list.
            //    Replaces the single-boolean ActionCard that could only ever
            //    surface one problem at a time.
            item(key = "health") {
                PvHealthBanner(
                    summary = health ?: HealthSummary(emptyList()),
                    loading = healthLoading,
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.xl,
                    ),
                    onFix = { item ->
                        when (item.inAppAction) {
                            InAppAction.PAIR_DEVICE -> onSelectTab(PvTab.TEAM)
                            else -> viewModel.applyHealthFix(context, item)
                        }
                    },
                    onOpenDetail = onOpenHealth,
                )
            }

            // 5. Numeric data block
            item(key = "today") {
                Column(
                    Modifier.padding(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
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
                    Modifier.padding(horizontal = Spacing.gutter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (history.isEmpty()) {
                        Spacer(Modifier.height(Spacing.xl))
                        Text(
                            "No payments yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        onClick = { onSelectTab(PvTab.LIST) },
                        enabled = history.isNotEmpty(),
                    )
                }
            }

            // 6. Quick links
            item(key = "quick-links") {
                Column(
                    Modifier.padding(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
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
                        QuickLink(icon = Icons.Filled.MonitorHeart, label = "Fix a problem", onClick = onOpenHealth)
                        QuickLink(
                            icon = Icons.Filled.People,
                            label = "Employees",
                            onClick = { onSelectTab(PvTab.TEAM) },
                        )
                        QuickLink(icon = Icons.Filled.Settings, label = "Settings", onClick = { onSelectTab(PvTab.SETTINGS) })
                        QuickLink(icon = Icons.Filled.Build, label = "Payments", onClick = { onSelectTab(PvTab.LIST) })
                    }
                }
            }

            // 7. Bottom clearance comes from the scaffold's inner padding
            //    (tab bar height + navigation-bar inset).
            item(key = "footer") {
                Spacer(Modifier.height(Spacing.sm))
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
