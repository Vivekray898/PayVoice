package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Low-emphasis text button — inline CTAs inside cards, sheet cancellations,
 * trailing row actions. Never the screen's primary action.
 *
 * [minHeight] defaults to the 56dp touch floor so an inline text button is
 * still tappable with one hand.
 */
@Composable
fun PvTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    minHeight: Dp = Spacing.touch,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(percent = 50),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        colors = ButtonDefaults.textButtonColors(contentColor = tint),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(Spacing.icon))
                Spacer(Modifier.size(Spacing.sm))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Inline text link — no pill, just the label, for use inside body copy. */
@Composable
fun PvLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = Spacing.rowBar),
        contentPadding = PaddingValues(horizontal = Spacing.xs),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
