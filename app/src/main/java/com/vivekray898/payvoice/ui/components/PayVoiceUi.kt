package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp


// ---------------------------------------------------------------------------
// Screen scaffold
// ---------------------------------------------------------------------------

/**
 * Standard screen chrome: title + optional back navigation, body insets.
 *
 * UI-overhaul Phase 2: a real Material3 [Scaffold] with
 * `contentWindowInsets = WindowInsets.safeDrawing` — content NEVER draws
 * under the status bar, navigation bar, display cutout, or IME. The title
 * row clears the status bar via its own top-inset padding (the topBar slot
 * is placed at the window edge); the body receives [Scaffold] innerPadding
 * (nav bar + IME included), which screens forward to LazyColumn
 * contentPadding so the last item never hides behind the nav bar.
 */
@Composable
fun PvScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        bottomBar = { bottomBar?.invoke() },
        floatingActionButton = { floatingActionButton?.invoke() },
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(start = 4.dp, end = 16.dp)
                    .heightIn(min = 56.dp),
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
                        .padding(start = if (onBack != null) 4.dp else 16.dp),
                ) {
                    Text(
                        title,
                        style = if (subtitle == null) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                actions?.invoke(this)
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Status pill (StatusDot/StatusLine live in Common.kt)
// ---------------------------------------------------------------------------

/**
 * Small rounded status pill, e.g. "Connected" / "Revoked". Semantic color
 * mapping keeps light/dark contrast correct automatically.
 */
@Composable
fun StatusPill(text: String, ok: Boolean?, modifier: Modifier = Modifier) {
    val bg = when (ok) {
        true -> statusPositive().copy(alpha = 0.14f)
        false -> statusNegative().copy(alpha = 0.14f)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (ok) {
        true -> statusPositive()
        false -> statusNegative()
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Sections & rows
// ---------------------------------------------------------------------------

/**
 * A clean section: optional title OUTSIDE any card, content below. Uses a
 * subtle surface only when [tinted] is set; plain sections keep the airy
 * look (spec: reduce cards).
 */
@Composable
fun PvSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        content()
    }
}

/** Simple list row with a 48dp+ minimum height (accessibility). */
@Composable
fun PvRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
    if (onClick != null) {
        Row(
            base.clickable(onClick = onClick).then(modifier),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    } else {
        Row(base.then(modifier), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

// ---------------------------------------------------------------------------
// Empty / loading states
// ---------------------------------------------------------------------------

@Composable
fun PvEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun PvLoadingRow(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------
// Status hero (primary status block on Home)
// ---------------------------------------------------------------------------

/**
 * The big, calm status block at the top of Home: icon, title, ONE human
 * line, and an optional single primary action. No technical terms ever.
 */
@Composable
fun PvStatusHero(
    icon: ImageVector,
    tint: Color,
    title: String,
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        color = tint.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = tint)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(headline, style = MaterialTheme.typography.headlineMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text(actionLabel) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Payment rows
// ---------------------------------------------------------------------------

/**
 * One payment, three levels of information: amount (prominent), source +
 * sender (secondary), time (tertiary). Not a card — a clean list row.
 */
@Composable
fun PvPaymentRow(amountText: String, source: String?, sender: String?, timeText: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            contentColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
        ) {
            Text(
                "₹",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(10.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(amountText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val secondary = buildString {
                if (!sender.isNullOrBlank()) append("from $sender")
                else if (!source.isNullOrBlank()) append(source)
            }
            if (secondary.isNotBlank()) {
                Text(secondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            timeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Vertical hairline divider used between list rows. */
@Composable
fun PvDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

// ---------------------------------------------------------------------------
// Action cards & the single primary action (UI overhaul Phase 4)
// ---------------------------------------------------------------------------

/**
 * Tappable action card (GPay-style): leading icon, title, one-line subtitle,
 * trailing chevron. 48dp+ target via the full-card click surface. The whole
 * card is the tap target — no inner buttons.
 */
@Composable
fun PvActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The ONE primary action per screen: full-width, 56dp tall. Screens place it
 * bottom-anchored (outside scrollable content) so it is always visible
 * without scrolling.
 */
@Composable
fun PvPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(text)
    }
}
