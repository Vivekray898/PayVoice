package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Base chrome for every screen (DESIGN.md rebuild, Phase 2). The ONLY
 * Scaffold in the app: safeDrawing insets are applied here, centrally —
 * screens never pad for status/nav bars themselves and never use
 * systemBarsPadding()/safeDrawingPadding().
 */
@Composable
fun PvScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

/**
 * Compat overload matching the pre-rebuild call signature (title/onBack/
 * subtitle/actions + ColumnScope body). Removed as screens adopt
 * PvScaffold + PvTopBar directly in Phase 3. The title row clears the
 * status bar via its own top inset (the slot sits at the window edge).
 */
@Composable
fun PvScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    largeTitle: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    PvScaffold(
        floatingActionButton = { floatingActionButton?.invoke() },
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(start = Spacing.xs, end = Spacing.lg)
                    .heightIn(min = Spacing.xxl + Spacing.xl + Spacing.lg), // 56dp structural
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(
                            start = if (onBack != null) Spacing.xs else Spacing.lg,
                            top = if (largeTitle) Spacing.sm else Spacing.xxs,
                            bottom = if (largeTitle) Spacing.xs else Spacing.xxs,
                        ),
                ) {
                    Text(
                        title,
                        style = if (largeTitle) {
                            MaterialTheme.typography.headlineLarge
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                actions()
            }
        },
        bottomBar = { bottomBar?.invoke() },
        snackbarHostState = snackbarHostState,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
            content = content,
        )
    }
}

/**
 * Compat chrome for screens mid-migration: a PvScaffold + PvTopBar exposing
 * the legacy title/onBack/subtitle surface with a bodyPadding hook so
 * content always clears the app bar and navigation bar.
 */
@Composable
fun PvScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    topBarActions: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    bodyPadding: (PaddingValues) -> Modifier = { Modifier },
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    PvScaffold(
        topBar = { PvTopBar(title = title, onBack = onBack, subtitle = subtitle, actions = topBarActions) },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHostState = snackbarHostState,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .then(bodyPadding(inner)),
            content = content,
        )
    }
}
