package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivekray898.payvoice.ui.theme.Spacing

/** Status colors mapped to DESIGN.md roles (Phase 1 bridge; Phase 2 rewrites these). */
@Composable
fun statusPositive(): Color = MaterialTheme.colorScheme.primary

@Composable
fun statusNegative(): Color = MaterialTheme.colorScheme.error

@Composable
fun statusCaution(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** null = caution/unknown. */
@Composable
fun StatusDot(ok: Boolean?, modifier: Modifier = Modifier) {
    val color = when (ok) {
        true -> statusPositive()
        false -> statusNegative()
        null -> statusCaution()
    }
    Box(
        modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * Status icon: check / cross / warning in a tinted circle — the premium
 * replacement for the bare status dot.
 */
@Composable
fun StatusIcon(ok: Boolean?, modifier: Modifier = Modifier) {
    val color = when (ok) {
        true -> statusPositive()
        false -> statusNegative()
        null -> statusCaution()
    }
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (ok) {
                true -> Icons.Filled.Check
                false -> Icons.Filled.Close
                null -> Icons.Filled.Warning
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
fun StatusLine(ok: Boolean?, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatusIcon(ok)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Compact relative time for history rows. */
fun timeAgo(thenMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diff = (nowMs - thenMs).coerceAtLeast(0)
    val minutes = diff / 60_000
    return when {
        diff < 45_000 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1_440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1_440}d ago"
    }
}

// ---------------------------------------------------------------------------
// Shared card + switch row (used by Reliability / Diagnostics / Onboarding)
// ---------------------------------------------------------------------------

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
