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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
 * Full-bleed hero band: a soft two-stop wash from [MaterialTheme.colorScheme]
 * `primaryContainer` down to `surface`, with two very low-alpha arcs bleeding
 * off the right edge.
 *
 * This replaces a hand-drawn phone-and-₹-tile graphic. At 200dp tall the
 * drawn phone read as a cartoon logo floating over the app bar rather than as
 * a header, and it competed with the real launcher icon for attention. A
 * gradient does the only job the band has — separating the header from the
 * greeting below it — without pretending to be an illustration.
 */
@Composable
fun StorefrontHero(modifier: Modifier = Modifier) {
    // Colors are captured in composition (DrawScope is not composable).
    val top = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val bottom = MaterialTheme.colorScheme.surface
    val ripple = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(top, bottom),
            ),
        )
        // Two concentric arcs, anchored off the right edge so they read as a
        // soft sound pulse rather than as an icon.
        val cx = size.width
        val cy = size.height * 0.5f
        for (i in 1..2) {
            val r = size.minDimension * (0.42f + i * 0.22f)
            drawCircle(
                color = ripple,
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = size.minDimension * 0.03f),
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
 * Action-card illustration: the given icon in a soft circular backplate,
 * with an optional attention badge dot. 56dp structural.
 */
@Composable
fun BellIllustration(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.NotificationsActive,
    badge: Boolean = true,
) {
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
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Spacing.xxl), // 32dp structural
                )
            }
        }
        if (badge) {
            Surface(
                shape = CircleShape,
                color = Lemon,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(Spacing.xl + Spacing.sm), // 20dp structural badge
            ) {}
        }
    }
}


