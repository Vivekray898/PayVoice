package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * The FAB for a tab screen's single primary action ("Add employee").
 *
 * It lives in its own file because it is one of the five places DESIGN.md
 * sanctions `{rounded.pill}` — a button. `verifyDesignShapes` allowlists this
 * file by name, so the radius stays correct here and cannot spread to a
 * selection group, a readout or a nav indicator.
 */
@Composable
fun PvFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Spacing.touch),
        shape = RoundedCornerShape(percent = 50),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(Spacing.icon),
                )
            }
        },
        text = { Text(text = text, style = MaterialTheme.typography.labelLarge) },
    )
}