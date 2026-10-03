package com.vivekray898.payvoice.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvCard
import com.vivekray898.payvoice.ui.components.PvChoiceChip
import com.vivekray898.payvoice.ui.components.PvListItem
import com.vivekray898.payvoice.ui.components.PvSecondaryButton
import com.vivekray898.payvoice.ui.components.PvSectionHeader
import com.vivekray898.payvoice.ui.components.PvSliderRow
import com.vivekray898.payvoice.ui.components.PvSwitchRow
import com.vivekray898.payvoice.ui.components.PvTab
import com.vivekray898.payvoice.ui.components.PvTabScaffold
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Settings tab (both roles): voice, detection, role, retention and the two
 * support screens.
 *
 * Under the tab map in `docs/UI_INVENTORY.md` §6 this is a bottom destination,
 * so there is no back chevron — the previous floating arrow was the only way
 * out of a screen reached from four quick links, and it disappears with them.
 * Diagnostics and Health moved down here as rows rather than being reachable
 * only from Home.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    selected: PvTab,
    onSelectTab: (PvTab) -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var expandedAnnouncements by remember { mutableStateOf(false) }

    PvTabScaffold(
        role = settings.role,
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
                    text = "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            }

            // ---- Announcements -------------------------------------------
            item(key = "announcements") {
                PvSectionHeader(text = "Announcements", gutter = Spacing.lg)
                PvListItem(
                    title = "Payment announcements",
                    subtitle = if (expandedAnnouncements) {
                        "Hide voice options"
                    } else {
                        "Voice style, language, speed & volume"
                    },
                    leadingIcon = Icons.Filled.NotificationsActive,
                    onClick = { expandedAnnouncements = !expandedAnnouncements },
                    minHeight = Spacing.listRow,
                )
                if (expandedAnnouncements) {
                    PvCard(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        Text(
                            text = "Voice style",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            AnnouncementStyle.entries.forEach { style ->
                                PvChoiceChip(
                                    text = style.label,
                                    selected = settings.style == style,
                                    onClick = { viewModel.setStyle(style) },
                                )
                            }
                        }
                        Text(
                            text = "Language",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            AnnouncementLanguage.entries.forEach { lang ->
                                PvChoiceChip(
                                    text = lang.label,
                                    selected = settings.language == lang,
                                    onClick = { viewModel.setLanguage(lang) },
                                )
                            }
                        }
                        PvSliderRow(
                            label = "Speech speed",
                            valueText = "%.1fx".format(settings.speechRate),
                            value = settings.speechRate,
                            onValueChange = viewModel::setSpeechRate,
                            valueRange = 0.8f..1.5f,
                        )
                        PvSliderRow(
                            label = "Speech volume",
                            valueText = "%d%%".format((settings.speechVolume * 100).toInt()),
                            value = settings.speechVolume,
                            onValueChange = viewModel::setSpeechVolume,
                            valueRange = 0f..1f,
                        )
                        PvSecondaryButton(
                            text = "Preview voice",
                            onClick = { viewModel.speakTest() },
                        )
                    }
                }
            }

            // ---- Payment detection ---------------------------------------
            item(key = "detection") {
                PvSectionHeader(text = "Payment detection", gutter = Spacing.lg)
                PvCard(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    PvSwitchRow(
                        label = "Google Pay notifications",
                        supporting = "Announce as GPay notifies you",
                        checked = settings.gpayEnabled,
                        onCheckedChange = viewModel::setGpayEnabled,
                    )
                    PvSwitchRow(
                        label = "Only confident detections",
                        supporting = "Off also announces less certain payments",
                        checked = settings.announceHighConfidenceOnly,
                        onCheckedChange = viewModel::setHighConfidenceOnly,
                    )
                }
            }

            // ---- Device & team -------------------------------------------
            item(key = "device") {
                PvSectionHeader(text = "Your business", gutter = Spacing.lg)
                PvCard(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    PvListItem(
                        // "This device is the / Owner" read as a broken
                        // sentence across two lines.
                        title = "Your role",
                        subtitle = if (settings.role == DeviceRole.OWNER) {
                            "This device is the owner"
                        } else {
                            "This device is an employee"
                        },
                        leadingIcon = Icons.Filled.Storefront,
                        minHeight = Spacing.listRow,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PvChoiceChip(
                            text = "Owner",
                            selected = settings.role == DeviceRole.OWNER,
                            onClick = {
                                viewModel.setRole(
                                    DeviceRole.OWNER,
                                    settings.deviceName.ifBlank { Build.MODEL ?: "Owner" },
                                )
                            },
                        )
                        PvChoiceChip(
                            text = "Employee",
                            selected = settings.role == DeviceRole.EMPLOYEE,
                            onClick = {
                                viewModel.setRole(
                                    DeviceRole.EMPLOYEE,
                                    settings.deviceName.ifBlank { Build.MODEL ?: "Employee" },
                                )
                            },
                        )
                    }
                    PvSwitchRow(
                        label = "Send payments to employee phones",
                        supporting = "Announce on connected employee devices",
                        checked = settings.remoteAnnouncementsEnabled,
                        onCheckedChange = viewModel::setRemoteAnnouncementsEnabled,
                    )
                    PvSwitchRow(
                        label = "Show payment on lock screen",
                        supporting = "Off hides the amount until you unlock",
                        checked = settings.showPaymentOnLockScreen,
                        onCheckedChange = viewModel::setShowPaymentOnLockScreen,
                    )
                }
            }

            // ---- App settings --------------------------------------------
            item(key = "storage") {
                PvSectionHeader(text = "App settings", gutter = Spacing.lg)
                PvCard(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    PvSliderRow(
                        label = "Payment memory",
                        valueText = "%d h".format(settings.dedupRetentionHours),
                        value = settings.dedupRetentionHours.toFloat(),
                        onValueChange = { viewModel.setDedupHours(it.toInt()) },
                        valueRange = 1f..72f,
                        steps = 70,
                    )
                    PvSliderRow(
                        label = "Keep history",
                        valueText = "%d days".format(settings.historyRetentionDays),
                        value = settings.historyRetentionDays.toFloat(),
                        onValueChange = { viewModel.setHistoryDays(it.toInt()) },
                        valueRange = 1f..30f,
                        steps = 28,
                    )
                }
            }

            // ---- Support -------------------------------------------------
            item(key = "support") {
                PvSectionHeader(text = "Support", gutter = Spacing.lg)
                PvListItem(
                    title = "Fix a problem",
                    subtitle = "Permissions, battery and connection repair",
                    leadingIcon = Icons.Filled.VisibilityOff,
                    onClick = onOpenHealth,
                    minHeight = Spacing.listRow,
                    trailing = { PvChevron() },
                )
                PvListItem(
                    title = "Diagnostics",
                    subtitle = "Technical logs and captured notifications",
                    leadingIcon = Icons.Filled.Visibility,
                    onClick = onOpenDiagnostics,
                    minHeight = Spacing.listRow,
                    trailing = { PvChevron() },
                )
            }

            // ---- About ---------------------------------------------------
            item(key = "about") {
                PvSectionHeader(text = "About", gutter = Spacing.lg)
                PvCard(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    AboutRow("Version", "1.0")
                    AboutRow("Android", android.os.Build.VERSION.RELEASE ?: "?")
                    AboutRow("Device", Build.MODEL ?: "?")
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

/** Chevron affordance for a row that navigates. */
@Composable
private fun PvChevron() {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}