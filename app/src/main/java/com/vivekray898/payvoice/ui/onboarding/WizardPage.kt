package com.vivekray898.payvoice.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * One wizard page: large heading + supporting paragraph + icon visual in the
 * upper area, the page's single primary action anchored at the bottom (always
 * visible without scrolling), and an optional secondary text link under it.
 * Pure presentation — every step file supplies its own copy and handlers.
 */
@Composable
fun WizardPage(
    icon: ImageVector,
    heading: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    linkLabel: String? = null,
    onLink: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
    ) {
        Spacer(Modifier.size(Spacing.xl))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null)
            }
        }
        Spacer(Modifier.size(Spacing.xl))
        Text(heading, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.size(Spacing.md))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content?.invoke(this)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                ),
        ) {
            Text(primaryLabel)
        }
        if (linkLabel != null && onLink != null) {
            TextButton(
                onClick = onLink,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                    ),
            ) {
                Text(
                    linkLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(Spacing.lg))
    }
}
