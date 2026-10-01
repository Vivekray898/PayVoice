package com.vivekray898.payvoice.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.SwitchRow
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Settings (premium pass, UI overhaul Phase 4): carded sections, slider
 * values in pill chips, and the screen's one primary action ("Preview
 * voice") bottom-anchored and always visible. No raw package names or role
 * jargon; debug/diagnostic entries live under Advanced.
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
        title = "Settings",
        onBack = onBack,
        bottomBar = {
            PvPrimaryButton(
                text = "Preview voice",
                onClick = viewModel::speakTest,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(bottom = Spacing.md),
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "announcements") {
                PvSection(title = "Announcements", carded = true) {
                    Text(
                        "Payments are spoken automatically as they arrive.",
                        style = MaterialTheme.typography.bodyMedium,
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
                    SliderRow(
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
                    SliderRow(
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
                PvSection(title = "Payment detection", carded = true) {
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
                PvSection(title = "Employees", carded = true) {
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
                    Text("This device is", style = MaterialTheme.typography.titleSmall)
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
                PvSection(title = "Storage", carded = true) {
                    SliderRow(
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
                    SliderRow(
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

            item(key = "advanced") {
                PvSection(title = "Advanced") {
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
        }
    }
}

/** Slider with the current value shown in a pill chip beside the label. */
@Composable
private fun SliderRow(
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
            ValuePill(text = value)
        }
        slider()
    }
}

@Composable
private fun ValuePill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xxs),
        )
    }
}
