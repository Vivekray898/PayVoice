package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vivekray898.payvoice.ui.theme.PvStatusTheme
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * A static skeleton block.
 *
 * Deliberately **not animated**: the motion contract bans shimmer loops, and a
 * pulsing gradient is one of the first things that drops frames on a 1GB phone.
 * A flat `surfaceVariant` block reads as "content is coming" just as clearly.
 */
@Composable
fun PvSkeleton(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small,
) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape),
    )
}

/** Skeleton shaped like a list row: 40dp circle + two text bars. */
@Composable
fun PvSkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        PvSkeleton(Modifier.size(Spacing.iconCircle), CircleShape)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PvSkeleton(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(Spacing.md),
            )
            PvSkeleton(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(Spacing.lg - Spacing.xs),
            )
        }
    }
}

/**
 * N skeleton rows — the loading state for every list in the app. Replaces the
 * "empty list looks like an error" first-fetch flash.
 */
@Composable
fun PvLoadingList(rows: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        repeat(rows) {
            PvSkeletonRow()
            PvDivider()
        }
    }
}

/** Small inline spinner + label, for inside a card or sheet. */
@Composable
fun PvLoading(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircularProgressIndicator(
            Modifier.size(Spacing.icon),
            strokeWidth = Spacing.hairline * 2,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Full-width error state with a Retry action — the third required state for
 * every list (`loading → empty → error`).
 */
@Composable
fun PvErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Filled.CloudOff,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Try again",
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(Spacing.iconCircle + Spacing.lg)
                    .background(
                        PvStatusTheme.colors.errorContainer,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = PvStatusTheme.colors.error,
                    modifier = Modifier.size(Spacing.icon),
                )
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(Spacing.xs))
            PvSecondaryButton(text = retryLabel, onClick = onRetry)
        }
    }
}

/** Thin flat progress line for a determinate refresh at the top of a list. */
@Composable
fun PvProgressLine(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.hairline * 2)
            .background(color),
    )
}
