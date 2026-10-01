package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vivekray898.payvoice.ui.theme.Spacing

/** Section label: micro ink-mute with generous top space (Phase 2 rebuild). */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xl, bottom = Spacing.sm),
    )
}

/**
 * Minimal settings-style row body: token-built minimum touch height
 * (32+16+8 = 56dp, structural — the 48dp target plus breathing room).
 */
@Composable
fun PvRowBody(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.xxl + Spacing.lg + Spacing.sm)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        content = content,
    )
}
