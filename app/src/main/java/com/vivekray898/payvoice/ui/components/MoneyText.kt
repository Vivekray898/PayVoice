package com.vivekray898.payvoice.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vivekray898.payvoice.core.parser.AmountExtractor

/**
 * Money/numeric text (DESIGN.md `body-tabular`, Phase 2 rebuild): every
 * amount renders through this — tabular figures via fontFeatureSettings.
 * Formats minor units through the app's single currency formatter so the
 * display and the announcement never disagree.
 */
@Composable
fun MoneyText(
    amountMinor: Long,
    currency: String = "INR",
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
) {
    Text(
        text = AmountExtractor.formatMinor(amountMinor, currency),
        style = style.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * Tabular-figure text for pre-formatted amount strings (rows that already
 * carry a formatted amount from the repository layer).
 */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
) {
    Text(
        text = text,
        style = style.copy(fontFeatureSettings = "tnum"),
        fontWeight = fontWeight,
        modifier = modifier,
    )
}
