package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.vivekray898.payvoice.ui.theme.Spacing

// Status color helpers (DESIGN.md roles; Warning uses the lemon accent)
// ---------------------------------------------------------------------------

@Composable
fun statusPositive(): Color = MaterialTheme.colorScheme.primary

@Composable
fun statusNegative(): Color = MaterialTheme.colorScheme.error

@Composable
fun statusCaution(): Color = statusToneContent(StatusTone.Warning)

/** Maps a tri-state boolean to a [StatusTone] (null = caution/unknown). */
fun statusToneOf(ok: Boolean?): StatusTone = when (ok) {
    true -> StatusTone.Success
    false -> StatusTone.Error
    null -> StatusTone.Warning
}

// ---------------------------------------------------------------------------
// Dots, icons, lines
// ---------------------------------------------------------------------------

/** null = caution/unknown. */
@Composable
fun StatusDot(ok: Boolean?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Spacing.sm + Spacing.xxs - Spacing.xs)
            .clip(CircleShape)
            .background(statusToneContainer(statusToneOf(ok))),
    )
}

/** Check / cross / warning glyph in a tinted circle (sizes structural). */
@Composable
fun StatusIcon(ok: Boolean?, modifier: Modifier = Modifier) {
    val container = statusToneContainer(statusToneOf(ok))
    val content = statusToneContent(statusToneOf(ok))
    Box(
        modifier
            .size(Spacing.xl + Spacing.xxs)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (ok) {
                true -> Icons.Filled.Check
                false -> Icons.Filled.Close
                null -> Icons.Filled.Warning
            },
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(Spacing.lg + Spacing.xxs - Spacing.xs),
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
// Rows and toggles
// ---------------------------------------------------------------------------

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.xxl + Spacing.lg + Spacing.sm)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
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

/** Hairline divider. */
@Composable
fun PvDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.xxs - Spacing.xs + Spacing.xs) // 1dp structural hairline
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

// ---------------------------------------------------------------------------
// Empty / loading states
// ---------------------------------------------------------------------------

@Composable
fun PvEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Spacing.xxl + Spacing.lg + Spacing.xs),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xs))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Compact inline empty hint inside a card. */
@Composable
fun PvEmptyHint(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Spacing.lg + Spacing.xxs),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PvLoadingRow(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircularProgressIndicator(Modifier.size(Spacing.lg + Spacing.xxs), strokeWidth = Spacing.xxs)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------
// Payment row
// ---------------------------------------------------------------------------

/** One payment: prominent amount (tabular), source/sender, time. */
@Composable
fun PvPaymentRow(amountText: String, source: String?, sender: String?, timeText: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
        ) {
            Text(
                "₹",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(Spacing.sm),
            )
        }
        Column(Modifier.weight(1f)) {
            MoneyText(
                text = amountText,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
            val secondary = buildString {
                if (!sender.isNullOrBlank()) append("from $sender")
                else if (!source.isNullOrBlank()) append(source)
            }
            if (secondary.isNotBlank()) {
                Text(secondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            timeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

