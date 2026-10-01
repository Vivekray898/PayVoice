package com.vivekray898.payvoice.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvActionCard
import com.vivekray898.payvoice.ui.components.PvPrimaryButton
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSecondaryButton
import com.vivekray898.payvoice.ui.components.PvTopBar
import com.vivekray898.payvoice.ui.components.SectionHeader
import com.vivekray898.payvoice.ui.components.SwitchRow
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Settings (DESIGN.md rebuild, Phase 3e): sectioned Surface groups with
 * 56dp rows, inline slider values, chip selectors, a bottom-anchored
 * "Preview voice" pill, and an About group. Role changes live here (Home
 * is read-only), per the rebuild spec.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    onOpenReliability: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    PvScaffold(
        topBar = { PvTopBar(title = "Settings", onBack = onBack) },
        bottomBar = {
            PvPrimaryButton(
                text = "Preview voice",
                onClick = { viewModel.speakTest() },
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.xl + inner.calculateBottomPadding(),
            ),
        ) {
            item(key = "announcements") {
                SectionHeader(text = "Announcements")
                SettingsGroup {
                    Text(
                        "Payments are spoken automatically as they arrive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Voice style", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        AnnouncementStyle.entries.forEach { style ->
                            FilterChip(
                                selected = settings.style == style,
                                onClick = { viewModel.setStyle(style) },
                                label = { Text(style.label) },
                            )
                        }
                    }
                    Text("Language", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        AnnouncementLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.language == lang,
                                onClick = { viewModel.setLanguage(lang) },
                                label = { Text(lang.label) },
                            )
                        }
                    }
                    SliderSetting(
                        label = "Speech speed",
                        value = "%.1fx".format(settings.speechRate),
                    ) {
                        Slider(
                            value = settings.speechRate,
                            onValueChange = { viewModel.setSpeechRate(it) },
                            valueRange = 0.8f..1.5f,
                            colors = SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )
                    }
                    SliderSetting(
                        label = "Speech volume",
                        value = "%d%%".format((settings.speechVolume * 100).toInt()),
                    ) {
                        Slider(
                            value = settings.speechVolume,
                            onValueChange = { viewModel.setSpeechVolume(it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )
                    }
                }
            }

            item(key = "detection") {
                SectionHeader(text = "Notifications")
                SettingsGroup {
                    SwitchRow(
                        label = "Google Pay notifications",
                        checked = settings.gpayEnabled,
                        onCheckedChange = viewModel::setGpayEnabled,
                        supporting = "Payments announced as Google Pay notifies you",
                    )
                    SwitchRow(
                        label = "Only confident detections",
                        checked = settings.announceHighConfidenceOnly,
                        onCheckedChange = viewModel::setHighConfidenceOnly,
                        supporting = "Off also announces less certain payments",
                    )
                }
            }

            item(key = "employees") {
                SectionHeader(text = "Device & team")
                SettingsGroup {
                    SwitchRow(
                        label = "Send payments to employee phones",
                        checked = settings.remoteAnnouncementsEnabled,
                        onCheckedChange = viewModel::setRemoteAnnouncementsEnabled,
                        supporting = if (settings.role == DeviceRole.OWNER) {
                            "Detected payments are announced on connected employee devices"
                        } else {
                            "Available when this phone is set as the owner"
                        },
                    )
                    Text("Change role", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                }
            }

            item(key = "storage") {
                SectionHeader(text = "Storage")
                SettingsGroup {
                    SliderSetting(
                        label = "Payment memory",
                        value = "%d h".format(settings.dedupRetentionHours),
                    ) {
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
                    SliderSetting(
                        label = "Keep history",
                        value = "%d days".format(settings.historyRetentionDays),
                    ) {
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

            item(key = "support") {
                SectionHeader(text = "Support")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    PvActionCard(
                        icon = Icons.Filled.Build,
                        title = "Fix a problem",
                        subtitle = "Permissions, battery and connection repair",
                        onClick = onOpenReliability,
                    )
                    PvActionCard(
                        icon = Icons.Filled.Info,
                        title = "Diagnostics",
                        subtitle = "Technical logs and captured notifications",
                        onClick = onOpenDiagnostics,
                    )
                }
            }

            item(key = "about") {
                SectionHeader(text = "About")
                SettingsGroup {
                    AboutRow("Version", "1.0")
                    AboutRow("Android", android.os.Build.VERSION.RELEASE ?: "?")
                    AboutRow("Device", Build.MODEL ?: "?")
                }
            }
        }
    }
}

/** A carded settings group (rounded.lg surface). */
@Composable
private fun SettingsGroup(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
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

/** Slider row with the current value inline. */
@Composable
private fun SliderSetting(
    label: String,
    value: String,
    slider: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
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
