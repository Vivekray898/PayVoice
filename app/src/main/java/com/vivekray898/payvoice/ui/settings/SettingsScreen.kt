package com.vivekray898.payvoice.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.SectionCard
import com.vivekray898.payvoice.ui.components.SwitchRow

/**
 * Announcement + detection settings (spec §25, §26, §20). Original layout;
 * the payment-source toggles gate the whitelist at the listener.
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack) { Text("Done") }
        }

        SectionCard(title = "Payment sources") {
            SwitchRow(
                label = "Google Pay notifications",
                checked = settings.gpayEnabled,
                onCheckedChange = viewModel::setGpayEnabled,
                supporting = com.vivekray898.payvoice.core.model.KnownPackages.GOOGLE_PAY,
            )
            Text(
                "Bank payments (Kotak and others) arrive automatically via bank " +
                    "SMS — no bank app notification access is used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Announcement style") {
            AnnouncementStyle.entries.forEach { style ->
                FilterChip(
                    selected = settings.style == style,
                    onClick = { viewModel.setStyle(style) },
                    label = { Text("${style.label} — \"${style.sample}\"") },
                )
            }
        }

        SectionCard(title = "Language") {
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
                "Hinglish uses the Hindi voice with code-mixed wording. " +
                    "If a voice is missing, Android falls back to English.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Voice") {
            Text("Speed: %.1fx".format(settings.speechRate))
            Slider(
                value = settings.speechRate,
                onValueChange = { viewModel.setSpeechRate(it) },
                valueRange = 0.8f..1.5f,
            )
            Text("Volume: %d%%".format((settings.speechVolume * 100).toInt()))
            Slider(
                value = settings.speechVolume,
                onValueChange = { viewModel.setSpeechVolume(it) },
                valueRange = 0f..1f,
            )
            OutlinedButton(onClick = { viewModel.speakTest() }) { Text("🔊 Preview voice") }
        }

        SectionCard(title = "Detection threshold") {
            SwitchRow(
                label = "High confidence only",
                checked = settings.announceHighConfidenceOnly,
                onCheckedChange = viewModel::setHighConfidenceOnly,
                supporting = "Off also announces medium-confidence payments (more false positives)",
            )
        }

        SectionCard(title = "Storage retention") {
            Text("Dedup memory: %d h".format(settings.dedupRetentionHours))
            Slider(
                value = settings.dedupRetentionHours.toFloat(),
                onValueChange = { viewModel.setDedupHours(it.toInt()) },
                valueRange = 1f..72f,
                steps = 70,
            )
            Text("History: %d days".format(settings.historyRetentionDays))
            Slider(
                value = settings.historyRetentionDays.toFloat(),
                onValueChange = { viewModel.setHistoryDays(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28,
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        Spacer(Modifier.height(24.dp))
    }
}
