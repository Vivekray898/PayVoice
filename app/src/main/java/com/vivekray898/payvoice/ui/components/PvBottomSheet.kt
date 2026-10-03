package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * The one bottom sheet. Three screens had their own `ModalBottomSheet` +
 * `Column(fillMaxWidth).padding(horizontal = …).padding(bottom = …)` copy;
 * this is the single implementation.
 *
 * Mid-action locks are the caller's job: guard `onDismissRequest` rather than
 * adding a scroll-interception layer that costs frames on low-end devices.
 *
 * @param title optional sheet heading; omit for pure confirmations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PvBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.xl, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            content()
        }
    }
}
