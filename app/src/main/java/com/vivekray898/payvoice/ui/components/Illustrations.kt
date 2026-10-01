package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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

/** Full-bleed storefront scene: sky, ground, shop with striped awning. */
@Composable
fun StorefrontHero(modifier: Modifier = Modifier) {
    // Colors are captured in composition (DrawScope is not composable).
    val sky = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    val skyTop = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val ground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    val shop = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val shopLight = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val awningB = MaterialTheme.colorScheme.primaryContainer
    val accent = MaterialTheme.colorScheme.tertiaryContainer
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Sky (two-band wash, lighter at the top)
        drawRect(color = skyTop, size = Size(w, h * 0.55f))
        drawRect(color = sky, topLeft = Offset(0f, h * 0.55f), size = Size(w, h * 0.45f))

        // Ground band
        drawRect(color = ground, topLeft = Offset(0f, h * 0.78f), size = Size(w, h * 0.22f))

        // Clouds
        listOf(0.16f, 0.62f).forEachIndexed { i, cx ->
            val cy = h * (0.16f + i * 0.06f)
            val r = w * (0.035f + i * 0.012f)
            drawCircle(color = Color.White.copy(alpha = 0.75f), radius = r, center = Offset(w * cx, cy))
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = r * 0.8f,
                center = Offset(w * cx + r, cy + r * 0.25f),
            )
        }

        // Sun
        drawCircle(color = accent, radius = w * 0.045f, center = Offset(w * 0.86f, h * 0.14f))

        // Distant blocks (left skyline)
        drawRect(
            color = shopLight.copy(alpha = 0.4f),
            topLeft = Offset(w * 0.06f, h * 0.52f),
            size = Size(w * 0.07f, h * 0.26f),
        )
        drawRect(
            color = shopLight.copy(alpha = 0.3f),
            topLeft = Offset(w * 0.16f, h * 0.6f),
            size = Size(w * 0.05f, h * 0.18f),
        )

        // Shop body
        val shopLeft = w * 0.38f
        val shopRight = w * 0.78f
        val shopTop = h * 0.34f
        val shopBottom = h * 0.9f
        drawRoundRect(
            color = shop,
            topLeft = Offset(shopLeft, shopTop),
            size = Size(shopRight - shopLeft, shopBottom - shopTop),
            cornerRadius = CornerRadius(w * 0.015f),
        )

        // Roof slab
        drawRoundRect(
            color = shopLight,
            topLeft = Offset(shopLeft - w * 0.02f, shopTop - h * 0.035f),
            size = Size(shopRight - shopLeft + w * 0.04f, h * 0.05f),
            cornerRadius = CornerRadius(w * 0.012f),
        )

        // Striped awning
        val awningTop = shopTop + h * 0.08f
        val awningH = h * 0.09f
        val stripeCount = 6
        val stripeW = (shopRight - shopLeft) / stripeCount
        for (i in 0 until stripeCount) {
            drawRoundRect(
                color = if (i % 2 == 0) shop else awningB,
                topLeft = Offset(shopLeft + i * stripeW, awningTop),
                size = Size(stripeW, awningH),
                cornerRadius = CornerRadius(w * 0.008f),
            )
        }

        // Window + door
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(shopLeft + (shopRight - shopLeft) * 0.12f, awningTop + awningH + h * 0.04f),
            size = Size((shopRight - shopLeft) * 0.35f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.01f),
        )
        drawRoundRect(
            color = shopLight,
            topLeft = Offset(shopLeft + (shopRight - shopLeft) * 0.6f, awningTop + awningH + h * 0.04f),
            size = Size((shopRight - shopLeft) * 0.22f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.01f),
        )

        // Counter figure (simple person: head + body)
        val px = w * 0.58f
        val py = awningTop + awningH + h * 0.02f
        drawCircle(color = Color.White, radius = w * 0.018f, center = Offset(px, py))
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(px - w * 0.018f, py + w * 0.026f),
            size = Size(w * 0.036f, h * 0.1f),
            cornerRadius = CornerRadius(w * 0.012f),
        )
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
