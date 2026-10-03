package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Confirmation dialog — **destructive actions only** (remove employee, leave
 * business, delete account). Everything reversible goes through a snackbar
 * with Undo instead of asking first.
 *
 * The confirm button carries the error colour so the consequence is visible
 * before the tap.
 */
@Composable
fun PvDialog(
    onDismissRequest: () -> Unit,
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "Cancel",
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(title, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            PvTextButton(
                text = confirmText,
                onClick = onConfirm,
                tint = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        },
        dismissButton = {
            PvTextButton(text = dismissText, onClick = onDismissRequest)
        },
    )
}
