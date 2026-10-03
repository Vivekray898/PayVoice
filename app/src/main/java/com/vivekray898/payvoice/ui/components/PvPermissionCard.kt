package com.vivekray898.payvoice.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.vivekray898.payvoice.R
import com.vivekray898.payvoice.core.health.HealthItem
import com.vivekray898.payvoice.core.health.HealthLevel
import com.vivekray898.payvoice.core.health.HealthSummary
import com.vivekray898.payvoice.ui.theme.PvMotion
import com.vivekray898.payvoice.ui.theme.PvStatusTheme
import com.vivekray898.payvoice.ui.theme.Spacing

private fun HealthLevel.icon(): ImageVector = when (this) {
    HealthLevel.OK -> Icons.Filled.CheckCircle
    HealthLevel.WARN -> Icons.Filled.WarningAmber
    HealthLevel.BLOCKED -> Icons.Filled.ErrorOutline
}

@Composable
private fun HealthLevel.accent(): Color = when (this) {
    HealthLevel.OK -> PvStatusTheme.colors.success
    HealthLevel.WARN -> PvStatusTheme.colors.warning
    HealthLevel.BLOCKED -> PvStatusTheme.colors.error
}

/**
 * One health problem: icon, plain title, one sentence of consequence, and a
 * single unambiguous button.
 *
 * This is the fix for the reported bug where permissions showed as "off" with
 * no way to turn them on. Rows arrive pre-sorted by severity.
 *
 * The card stays on the neutral surface and lets the icon carry the tone — a
 * checklist of five tinted blocks reads as an alarm, and this screen is one
 * owners will see every day.
 */
@Composable
fun PvPermissionCard(
    item: HealthItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PvCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = item.level.icon(),
                contentDescription = null,
                tint = item.level.accent(),
                modifier = Modifier.size(Spacing.iconCircle - Spacing.md),
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(item.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(item.why),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val label = item.fixLabel
        if (label != null) {
            Spacer(Modifier.height(Spacing.xs))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                PvSecondaryButton(
                    text = stringResource(label),
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
            }
        }
    }
}

/**
 * The Home banner: green "All good, ready for payments" when healthy, or one
 * red/amber summary ("2 things need fixing") that expands to the list.
 *
 * One banner rather than one card per problem — Home must stay scannable and
 * a non-technical owner should get a single verdict first.
 */
@Composable
fun PvHealthBanner(
    summary: HealthSummary,
    onFix: (HealthItem) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onOpenDetail: (() -> Unit)? = null,
) {
    if (loading) {
        PvCard(modifier = modifier) {
            PvLoading(label = stringResource(R.string.health_loading))
        }
        return
    }

    var expanded by remember(summary.items) { mutableStateOf(false) }

    if (summary.healthy) {
        PvCard(modifier = modifier) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = PvStatusTheme.colors.success,
                    modifier = Modifier.size(Spacing.icon),
                )
                Text(
                    text = stringResource(R.string.health_banner_all_good),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        return
    }

    val duration = PvMotion.durationMs()
    PvCard(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = summary.level.icon(),
                contentDescription = null,
                tint = summary.level.accent(),
                modifier = Modifier.size(Spacing.icon),
            )
            Text(
                text = stringResource(summary.bannerMessageRes(), summary.attentionCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            PvTextButton(
                text = stringResource(
                    if (expanded) R.string.health_banner_hide else R.string.health_banner_show,
                ),
                onClick = { expanded = !expanded },
                minHeight = Spacing.rowBar,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
            exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Spacer(Modifier.height(Spacing.xs))
                summary.ordered().filter { it.needsAttention }.forEach { item ->
                    PvPermissionCard(item = item, onClick = { onFix(item) })
                }
                if (onOpenDetail != null) {
                    Row(horizontalArrangement = Arrangement.Start) {
                        PvLink(
                            text = stringResource(R.string.health_banner_open),
                            onClick = onOpenDetail,
                        )
                    }
                }
            }
        }
    }
}

/** Headline string, chosen by severity and count (both variants take a count). */
private fun HealthSummary.bannerMessageRes(): Int = when (level) {
    HealthLevel.BLOCKED ->
        if (attentionCount == 1) R.string.health_banner_one_blocked else R.string.health_banner_blocked
    HealthLevel.WARN ->
        if (attentionCount == 1) R.string.health_banner_one_attention else R.string.health_banner_attention
    HealthLevel.OK -> R.string.health_banner_all_good
}
