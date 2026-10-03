package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Section label above a group of rows or cards.
 *
 * The single implementation — the private duplicate that `SettingsScreen`
 * carried is gone, so section rhythm is identical everywhere.
 *
 * @param uppercase the GPay-Business treatment: uppercase muted label for
 *   screen-level sections. Pass `false` for dense card headers.
 * @param gutter horizontal inset. Screens that pad their own list rows pass
 *   `Spacing.gutter` so the label aligns with the row text; sections inside a
 *   card already have the card's padding and leave this at 0.
 */
@Composable
fun PvSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    uppercase: Boolean = true,
    gutter: Dp = 0.dp,
) {
    Text(
        text = if (uppercase) text.uppercase() else text,
        style = if (uppercase) {
            MaterialTheme.typography.labelLarge
        } else {
            MaterialTheme.typography.labelMedium
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = gutter,
                end = gutter,
                top = Spacing.xl,
                bottom = Spacing.sm,
            ),
    )
}
