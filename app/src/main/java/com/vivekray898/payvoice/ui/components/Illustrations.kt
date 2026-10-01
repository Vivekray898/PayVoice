package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivekray898.payvoice.ui.theme.Lemon
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * The illustration layer (flat vector shapes drawn on Canvas — the GPay
 * Business approach: no photos, no Material icons in the hero). Colors are
 * scheme roles so the art reads correctly in light and dark. Structural
 * sizes are marked `// structural` per docs/DESIGN_SYSTEM.md.
 */

/**
 * Full-bleed hero: a rounded-square phone silhouette with the rupee mark
 * inside and concentric sound-wave arcs to its right — the product story
 * (PayVoice announces payments out loud) in one consistent scene on a soft
 * primaryContainer wash.
 */
@Composable
fun StorefrontHero(modifier: Modifier = Modifier) {
    // Colors are captured in composition (DrawScope is not composable).
    val wash = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    val washDeep = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    val phoneBody = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val phoneScreen = MaterialTheme.colorScheme.surface
    val waves = MaterialTheme.colorScheme.primary
    val rupee = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Background wash (soft vertical two-step)
        drawRect(color = wash, size = Size(w, h * 0.6f))
        drawRect(color = washDeep, topLeft = Offset(0f, h * 0.6f), size = Size(w, h * 0.4f))

        // Phone silhouette (centered, slightly left to leave room for waves)
        val phoneW = w * 0.2f
        val phoneH = h * 0.52f
        val phoneLeft = w * 0.34f
        val phoneTop = h * 0.22f
        drawRoundRect(
            color = phoneBody,
            topLeft = Offset(phoneLeft, phoneTop),
            size = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(phoneW * 0.22f),
        )
        // Screen inset
        val screenPad = phoneW * 0.09f
        drawRoundRect(
            color = phoneScreen,
            topLeft = Offset(phoneLeft + screenPad, phoneTop + screenPad),
            size = Size(phoneW - screenPad * 2, phoneH - screenPad * 2),
            cornerRadius = CornerRadius(phoneW * 0.16f),
        )

        // Rupee symbol inside the screen (two strokes + the crossbar)
        val cx = phoneLeft + phoneW / 2
        val cy = phoneTop + phoneH * 0.42f
        val s = phoneW * 0.16f
        drawLine(
            color = rupee,
            start = Offset(cx - s, cy - s),
            end = Offset(cx + s, cy - s),
            strokeWidth = s * 0.28f,
        )
        drawLine(
            color = rupee,
            start = Offset(cx - s, cy),
            end = Offset(cx + s * 0.2f, cy),
            strokeWidth = s * 0.28f,
        )
        drawLine(
            color = rupee,
            start = Offset(cx - s, cy + s * 0.15f),
            end = Offset(cx + s, cy + s * 1.4f),
            strokeWidth = s * 0.28f,
        )

        // Sound-wave arcs (three concentric, right of the phone)
        val arcCenter = Offset(phoneLeft + phoneW + w * 0.015f, phoneTop + phoneH * 0.5f)
        for (i in 1..3) {
            drawArc(
                color = waves.copy(alpha = 0.85f - (i - 1) * 0.22f),
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(arcCenter.x - i * w * 0.05f, arcCenter.y - i * h * 0.11f),
                size = Size(i * w * 0.1f, i * h * 0.22f),
                style = Stroke(width = w * 0.012f, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * Sub-screen header: floating back chevron + headlineSmall title (no
 * TopAppBar chrome — the GPay-Business sub-page treatment).
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(
                top = androidx.compose.foundation.layout.WindowInsets.statusBars
                    .asPaddingValues()
                    .calculateTopPadding() + Spacing.xs,
                start = Spacing.xs,
                end = Spacing.xl - Spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

/**
 * Compact storefront banner for sub-screens: the hero art clipped into a
 * rounded.xl block (~120dp) with the role/status content below it.
 */
@Composable
fun HeroBanner(modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Spacing.xxl * 4 + Spacing.lg) // ~136dp structural
                .clip(MaterialTheme.shapes.extraLarge),
        ) {
            StorefrontHero()
        }
    }
}

/**
 * Rounded-square illustration tile behind an icon (quick links, list rows):
 * 56dp tile at rounded.lg (40dp variant for dense rows), icon 28/20dp —
 * structural.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.primary,
    tile: Dp = Spacing.xxl + Spacing.lg + Spacing.sm, // 56dp structural
    iconSize: Dp = Spacing.xl + Spacing.xs, // 28dp structural
) {
    Surface(
        modifier = modifier.size(tile),
        shape = MaterialTheme.shapes.large, // rounded.lg = 12dp
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
        }
    }
}

/**
 * Notification bell illustration with a soft circular backplate and a
 * badge dot (action-card art). 56dp structural.
 */
@Composable
fun BellIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(Spacing.xxl + Spacing.lg + Spacing.sm), // 56dp structural
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Spacing.xxl), // 32dp structural
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = Lemon,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(Spacing.xl + Spacing.sm), // 20dp structural badge
        ) {}
    }
}

/** Centered empty-state art for "no payments yet": coin + awning mark. */
@Composable
fun PaymentsEmptyIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(Spacing.huge + Spacing.lg), // 80dp structural
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxSize(),
        ) {}
        val coin = MaterialTheme.colorScheme.primary
        val coinInner = MaterialTheme.colorScheme.primaryContainer
        val awning = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension * 0.16f
            drawCircle(color = coin, radius = r, center = Offset(c.x, c.y - size.height * 0.12f))
            drawCircle(
                color = coinInner,
                radius = r * 0.6f,
                center = Offset(c.x, c.y - size.height * 0.12f),
            )
            val awningW = size.width * 0.34f
            drawRoundRect(
                color = awning,
                topLeft = Offset(c.x - awningW / 2, c.y + size.height * 0.02f),
                size = Size(awningW, size.height * 0.1f),
                cornerRadius = CornerRadius(awningW * 0.2f),
            )
        }
    }
}
