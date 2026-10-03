package com.vivekray898.payvoice.ui.settings

import android.os.Build
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.IconTile
import com.vivekray898.payvoice.ui.components.PvPrimaryButton
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSectionHeader
import com.vivekray898.payvoice.ui.theme.Spacing

private val rowTarget = Spacing.xxl + Spacing.xxl + Spacing.sm // 72dp structural

/**
 * Settings (GPay-Business treatment): displaySmall headline with a floating
 * back chevron in the header row, uppercase muted section headers, 72dp
 * rows with 40dp icon tiles, 32dp section gaps. Content controls (voice,
 * language, toggles) live inline on the row; support entries navigate.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    onOpenReliability: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var expandedAnnouncements by remember { mutableStateOf(false) }

    PvScaffold(topBar = {}) { _ ->
        LazyColumn(Modifier.fillMaxSize()) {
            // Header: floating back + displaySmall headline (no TopAppBar)
            item(key = "header") {
                Column(
                    Modifier.padding(
                        start = Spacing.xl - Spacing.xs,
                        end = Spacing.xl - Spacing.xs,
                        top = statusBarPadding + Spacing.lg,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            // ---- Announcements (expandable: voice + language + sliders) ----
            item(key = "announcements") {
                PvSectionHeader("Announcements", gutter = Spacing.gutter)
                SettingsRow(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Payment announcements",
                    subtitle = when {
                        expandedAnnouncements -> "Hide voice options"
                        else -> "Voice style, language, speed & volume"
                    },
                    onClick = { expandedAnnouncements = !expandedAnnouncements },
                ) {
                    Text(
                        if (settings.gpayEnabled) "On" else "Off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (expandedAnnouncements) {
                    SettingsGroup {
                        Text("Voice style", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            AnnouncementStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = settings.style == style,
                                    onClick = { viewModel.setStyle(style) },
                                    label = { Text(style.label) },
                                )
                            }
                        }
                        Text("Language", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            AnnouncementLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = settings.language == lang,
                                    onClick = { viewModel.setLanguage(lang) },
                                    label = { Text(lang.label) },
                                )
                            }
                        }
                        SliderSetting("Speech speed", "%.1fx".format(settings.speechRate)) {
                            Slider(
                                value = settings.speechRate,
                                onValueChange = { viewModel.setSpeechRate(it) },
                                valueRange = 0.8f..1.5f,
                                colors = SliderDefaults.colors(
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            )
                        }
                        SliderSetting("Speech volume", "%d%%".format((settings.speechVolume * 100).toInt())) {
                            Slider(
                                value = settings.speechVolume,
                                onValueChange = { viewModel.setSpeechVolume(it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            )
                        }
                        Button(
                            onClick = { viewModel.speakTest() },
                            shape = RoundedCornerShape(percent = 50),
                        ) { Text("Preview voice") }
                    }
                }
            }

            // ---- Payment detection ----
            item(key = "detection") {
                PvSectionHeader("Payment detection", gutter = Spacing.gutter)
                SettingsGroup {
                    ToggleRow(
                        title = "Google Pay notifications",
                        subtitle = "Announce as GPay notifies you",
                        checked = settings.gpayEnabled,
                        onChecked = viewModel::setGpayEnabled,
                    )
                    ToggleRow(
                        title = "Only confident detections",
                        subtitle = "Off also announces less certain payments",
                        checked = settings.announceHighConfidenceOnly,
                        onChecked = viewModel::setHighConfidenceOnly,
                    )
                }
            }

            // ---- Device & team (role lives here; Home is read-only) ----
            item(key = "device") {
                PvSectionHeader("Your business", gutter = Spacing.gutter)
                SettingsGroup {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = rowTarget)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(
                            icon = Icons.Filled.Storefront,
                            tile = Spacing.xxl, // 40dp structural
                            iconSize = Spacing.xl - Spacing.xs, // 20dp structural
                            container = MaterialTheme.colorScheme.primaryContainer,
                        )
                        Spacer(Modifier.width(Spacing.lg))
                        Column(Modifier.weight(1f)) {
                            Text("This device is the", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (settings.role == DeviceRole.OWNER) "Owner" else "Employee",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilterChip(
                            selected = settings.role == DeviceRole.OWNER,
                            onClick = {
                                viewModel.setRole(
                                    DeviceRole.OWNER,
                                    settings.deviceName.ifBlank { Build.MODEL ?: "Owner" },
                                )
                            },
                            label = { Text("Owner") },
                        )
                        FilterChip(
                            selected = settings.role == DeviceRole.EMPLOYEE,
                            onClick = {
                                viewModel.setRole(
                                    DeviceRole.EMPLOYEE,
                                    settings.deviceName.ifBlank { Build.MODEL ?: "Employee" },
                                )
                            },
                            label = { Text("Employee") },
                        )
                    }
                    ToggleRow(
                        title = "Send payments to employee phones",
                        subtitle = "Announce on connected employee devices",
                        checked = settings.remoteAnnouncementsEnabled,
                        onChecked = viewModel::setRemoteAnnouncementsEnabled,
                    )
                    ToggleRow(
                        title = "Show payment on lock screen",
                        subtitle = "Off hides the amount until you unlock",
                        checked = settings.showPaymentOnLockScreen,
                        onChecked = viewModel::setShowPaymentOnLockScreen,
                    )
                }
            }

            // ---- Storage ----
            item(key = "storage") {
                PvSectionHeader("App settings", gutter = Spacing.gutter)
                SettingsGroup {
                    SliderSetting("Payment memory", "%d h".format(settings.dedupRetentionHours)) {
                        Slider(
                            value = settings.dedupRetentionHours.toFloat(),
                            onValueChange = { viewModel.setDedupHours(it.toInt()) },
                            valueRange = 1f..72f,
                            steps = 70,
                            colors = SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )
                    }
                    SliderSetting("Keep history", "%d days".format(settings.historyRetentionDays)) {
                        Slider(
                            value = settings.historyRetentionDays.toFloat(),
                            onValueChange = { viewModel.setHistoryDays(it.toInt()) },
                            valueRange = 1f..30f,
                            steps = 28,
                            colors = SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )
                    }
                }
            }

            // ---- Support: Diagnostics + Reliability as 72dp rows ----
            item(key = "support") {
                PvSectionHeader("Support", gutter = Spacing.gutter)
                SettingsRow(
                    icon = Icons.Filled.Visibility,
                    title = "Diagnostics",
                    subtitle = "Technical logs and captured notifications",
                    onClick = onOpenDiagnostics,
                )
                SettingsRow(
                    icon = Icons.Filled.VisibilityOff,
                    title = "Fix a problem",
                    subtitle = "Permissions, battery and connection repair",
                    onClick = onOpenReliability,
                    trailing = {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                PvSecondaryPill(
                    text = "Preview voice",
                    onClick = { viewModel.speakTest() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.lg),
                )
            }

            // ---- About ----
            item(key = "about") {
                PvSectionHeader("About", gutter = Spacing.gutter)
                SettingsGroup {
                    AboutRow("Version", "1.0")
                    AboutRow("Android", android.os.Build.VERSION.RELEASE ?: "?")
                    AboutRow("Device", Build.MODEL ?: "?")
                }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

/** 72dp row: 40dp icon tile, title + subtitle, optional trailing. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = rowTarget)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.xl - Spacing.xs, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = icon,
            tile = Spacing.xxl, // 40dp structural
            iconSize = Spacing.xl - Spacing.xs, // 20dp structural
            container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        )
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/** Carded group for inline controls. */
@Composable
private fun SettingsGroup(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Spacing.xxs,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
    ) {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = rowTarget)
            .padding(horizontal = Spacing.xs, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: String,
    slider: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(percent = 50),
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xxs),
                )
            }
        }
        slider()
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Local alias to keep the preview pill call sites short. */
@Composable
private fun PvSecondaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier,
    ) { Text(text) }
}
