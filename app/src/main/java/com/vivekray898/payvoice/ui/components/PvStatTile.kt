package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * A single headline number with its label — Home's "received today", a
 * payments period total, a device count.
 *
 * Money goes through [MoneyText] so figures stay tabular and aligned; the
 * default style is the money micro-scale's hero step (`displaySmall`).
 */
@Composable
fun PvStatTile(
    label: String,
    value: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    valueStyle: TextStyle = MaterialTheme.typography.displaySmall,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Spacing.icon),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        value()
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Convenience overload for plain text values (counts, non-currency figures).
 * Amounts should use the `value` slot with [MoneyText].
 */
@Composable
fun PvStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    valueStyle: TextStyle = MaterialTheme.typography.displaySmall,
    tabular: Boolean = false,
) {
    PvStatTile(
        label = label,
        modifier = modifier,
        supporting = supporting,
        icon = icon,
        valueStyle = valueStyle,
        value = {
            Text(
                text = value,
                style = if (tabular) {
                    valueStyle.copy(fontFeatureSettings = "tnum")
                } else {
                    valueStyle
                },
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}
