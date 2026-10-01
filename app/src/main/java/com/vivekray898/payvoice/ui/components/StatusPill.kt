package com.vivekray898.payvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vivekray898.payvoice.ui.theme.Lemon
import com.vivekray898.payvoice.ui.theme.Spacing

/** Status tone for pills and dots. */
enum class StatusTone { Success, Warning, Error, Neutral }

/**
 * Soft status pill (DESIGN.md `pill-tag-soft`, Phase 2 rebuild): dot + label
 * in a fully rounded, tinted track. Warning/Error tracks are DESIGN.md's
 * amber/ruby soft tints expressed from the reference's cream and ruby roles;
 * the 6dp dot is structural.
 */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (tone) {
        StatusTone.Success ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.Warning ->
            MaterialTheme.colorScheme.tertiaryContainer to Lemon
        StatusTone.Error ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        StatusTone.Neutral ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(percent = 50))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Spacing.sm + Spacing.xxs - Spacing.xs)
                .background(fg, CircleShape),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

/** Compat overload: tri-state ok maps through [statusToneOf]. */
@Composable
fun StatusPill(text: String, ok: Boolean?, modifier: Modifier = Modifier) {
    StatusPill(text = text, tone = statusToneOf(ok), modifier = modifier)
}

/** @return the soft track color for a tone (shared by dots and chips). */
@Composable
fun statusToneContainer(tone: StatusTone): Color = when (tone) {
    StatusTone.Success -> MaterialTheme.colorScheme.primaryContainer
    StatusTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
    StatusTone.Error -> MaterialTheme.colorScheme.errorContainer
    StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
}

/** @return the foreground (content) color for a tone. */
@Composable
fun statusToneContent(tone: StatusTone): Color = when (tone) {
    StatusTone.Success -> MaterialTheme.colorScheme.primary
    StatusTone.Warning -> Lemon
    StatusTone.Error -> MaterialTheme.colorScheme.error
    StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}
