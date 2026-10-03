package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * The outline pill (DESIGN.md `button-secondary`, Phase 2 rebuild). Minimum
 * touch height built from tokens (32+16+8 = 56dp, structural); 1dp hairline
 * border is structural.
 */
@Composable
fun PvSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Optional leading icon, e.g. a destructive `PersonRemove` on a revoke action. */
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.xxl + Spacing.lg + Spacing.sm),
        shape = RoundedCornerShape(percent = 50),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), // structural: 1dp border
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Spacing.xl),
            )
            Spacer(Modifier.size(Spacing.sm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
