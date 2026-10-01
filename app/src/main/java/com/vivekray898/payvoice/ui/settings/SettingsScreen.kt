package com.vivekray898.payvoice.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Build
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvDivider
import com.vivekray898.payvoice.ui.components.PvRow
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSection
import com.vivekray898.payvoice.ui.components.SwitchRow

/**
 * Settings (production redesign): consumer grouping — Announcements,
 * Detection, Employees, Advanced. No raw package names or role jargon;
 * debug/diagnostic entries live under Advanced.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    onOpenReliability: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    PvScaffold(title = "Settings", onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "announcements") {
                PvSection(title = "Announcements") {
                    Text(
                        "Payments are spoken automatically as they arrive.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Voice style", style = MaterialTheme.typography.titleSmall)
                    AnnouncementStyle.entries.forEach { style ->
                        FilterChip(
                            selected = settings.style == style,
                            onClick = { viewModel.setStyle(style) },
                            label = { Text(style.label) },
                        )
                    }
                    Text("Language", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnnouncementLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.language == lang,
                                onClick = { viewModel.setLanguage(lang) },
                                label = { Text(lang.label) },
                            )
                        }
                    }
                    Text(
                        "Speed: %.1fx".format(settings.speechRate),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = settings.speechRate,
                        onValueChange = { viewModel.setSpeechRate(it) },
                        valueRange = 0.8f..1.5f,
                        colors = SliderDefaults.colors(
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                    Text(
                        "Volume: %d%%".format((settings.speechVolume * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = settings.speechVolume,
                        onValueChange = { viewModel.setSpeechVolume(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                    OutlinedButton(onClick = { viewModel.speakTest() }) {
                        Text("Preview voice")
                    }
                }
            }

            item(key = "detection") {
                PvSection(title = "Payment detection") {
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
                PvSection(title = "Employees") {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.role == DeviceRole.OWNER,
                            onClick = { viewModel.setRole(DeviceRole.OWNER, settings.deviceName.ifBlank { Build.MODEL ?: "Owner" }) },
                            label = { Text("Owner") },
                        )
                        FilterChip(
                            selected = settings.role == DeviceRole.EMPLOYEE,
                            onClick = { viewModel.setRole(DeviceRole.EMPLOYEE, settings.deviceName.ifBlank { Build.MODEL ?: "Employee" }) },
                            label = { Text("Employee") },
                        )
                    }
                }
            }

            item(key = "retention") {
                PvSection(title = "Storage") {
                    Text(
                        "Dedup memory: %d h".format(settings.dedupRetentionHours),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = settings.dedupRetentionHours.toFloat(),
                        onValueChange = { viewModel.setDedupHours(it.toInt()) },
                        valueRange = 1f..72f,
                        steps = 70,
                        colors = SliderDefaults.colors(
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                    Text(
                        "History: %d days".format(settings.historyRetentionDays),
                        style = MaterialTheme.typography.bodyMedium,
                    )
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

            item(key = "advanced") {
                PvSection(title = "Advanced") {
                    PvRow(onClick = onOpenReliability) {
                        Column(Modifier.weight(1f)) {
                            Text("Fix a problem", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Permissions, battery and connection repair",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PvDivider()
                    PvRow(onClick = onOpenDiagnostics) {
                        Column(Modifier.weight(1f)) {
                            Text("Diagnostics", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Technical logs and captured notifications",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "footer") { Spacer(Modifier.height(16.dp)) }
        }
    }
}
