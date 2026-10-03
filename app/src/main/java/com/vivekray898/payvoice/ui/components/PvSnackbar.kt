package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Snackbar host for undo/confirmation feedback.
 *
 * The brief's rule is *"undo via snackbar instead of confirmation dialogs
 * where safe"* — so every reversible mutation reports through here with an
 * action, and [PvDialog] stays reserved for genuinely destructive operations.
 */
@Composable
fun PvSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(hostState = state) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier.padding(Spacing.lg),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Host state for a screen that shows undo/confirmation feedback. */
@Composable
fun rememberPvSnackbarState(): SnackbarHostState = remember { SnackbarHostState() }

/**
 * Show a transient message with an optional action (Undo / Retry).
 * Suspends until the snackbar is dismissed; runs [onAction] if tapped.
 *
 * ```kotlin
 * scope.launch { snack.pvShow("Employee removed", "Undo") { viewModel.undoRevoke() } }
 * ```
 */
suspend fun SnackbarHostState.pvShow(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val result = showSnackbar(
        message = message,
        actionLabel = actionLabel,
        withDismissAction = actionLabel == null,
    )
    if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
}
