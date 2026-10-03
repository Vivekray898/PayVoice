package com.vivekray898.payvoice.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vivekray898.payvoice.ui.theme.PayVoiceTheme
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * Previews for every shared component, in the four variants the brief
 * requires: light · dark · fontScale 1.3 · 320dp width.
 *
 * The four `@Preview` annotations are stacked directly rather than folded
 * into a meta-annotation, so nothing depends on repeatable-annotation
 * flattening behaviour to render in Android Studio.
 *
 * Screens get their previews in Step 4 alongside their `UiState` extraction:
 * they currently take a `MainViewModel`, which cannot be constructed inside
 * the preview renderer.
 */
@Composable
private fun PvPreviewTheme(content: @Composable () -> Unit) {
    PayVoiceTheme {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.lg),
        ) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvPrimaryButtonPreview() {
    PvPreviewTheme { PvPrimaryButton(text = "Add employee", onClick = {}) }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvSecondaryButtonPreview() {
    PvPreviewTheme { PvSecondaryButton(text = "Show all payments", onClick = {}) }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvTextButtonPreview() {
    PvPreviewTheme {
        PvTextButton(text = "Turn on", onClick = {})
    }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvLinkPreview() {
    PvPreviewTheme { PvLink(text = "Manage permissions", onClick = {}) }
}

// ---------------------------------------------------------------------------
// Containers & rows
// ---------------------------------------------------------------------------

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvCardPreview() {
    PvPreviewTheme {
        PvCard {
            PvSectionHeader(text = "Connection", uppercase = false)
            StatusLine(ok = true, label = "Connected")
            StatusLine(ok = false, label = "Not connected")
        }
    }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvListItemPreview() {
    PvPreviewTheme {
        PvListItem(
            title = "Diagnostics",
            subtitle = "Technical logs and captured notifications",
            leadingIcon = Icons.Filled.People,
            onClick = {},
        )
    }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvSwitchRowPreview() {
    PvPreviewTheme {
        PvSwitchRow(
            label = "Payment announcements",
            checked = true,
            onCheckedChange = {},
            supporting = "Voice style, language, speed & volume",
        )
    }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvIconCirclePreview() {
    PvPreviewTheme { PvIconCircle(icon = Icons.Filled.People) }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvSectionHeaderPreview() {
    PvPreviewTheme {
        PvSectionHeader(text = "Announcements", gutter = Spacing.gutter)
    }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvDividerPreview() {
    PvPreviewTheme { PvDivider() }
}

// ---------------------------------------------------------------------------
// Inputs
// ---------------------------------------------------------------------------

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvTextFieldPreview() {
    PvPreviewTheme {
        PvTextField(value = "Shop name", onValueChange = {}, label = "Business name")
    }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvCodeFieldPreview() {
    PvPreviewTheme {
        PvCodeField(value = "K7QM2XR4PB", onValueChange = {})
    }
}

// ---------------------------------------------------------------------------
// Status
// ---------------------------------------------------------------------------

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun StatusPillPreview() {
    PvPreviewTheme {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm),
        ) {
            StatusPill(text = "All good", tone = StatusTone.Success)
            StatusPill(text = "Needs fixing", tone = StatusTone.Warning)
            StatusPill(text = "Blocked", tone = StatusTone.Error)
            StatusPill(text = "Paused", tone = StatusTone.Neutral)
        }
    }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun StatusIconPreview() {
    PvPreviewTheme {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm),
        ) {
            StatusIcon(ok = true)
            StatusIcon(ok = false)
            StatusIcon(ok = null)
        }
    }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvPaymentRowPreview() {
    PvPreviewTheme {
        PvPaymentRow(
            amountText = "₹1,250.00",
            source = "Google Pay",
            sender = "Ravi",
            timeText = "2m ago",
        )
    }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvStatTilePreview() {
    PvPreviewTheme {
        PvStatTile(
            label = "Received today",
            value = "₹12,480",
            supporting = "7 payments",
            tabular = true,
        )
    }
}

// ---------------------------------------------------------------------------
// Loading / empty / error
// ---------------------------------------------------------------------------

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvLoadingListPreview() {
    PvPreviewTheme { PvLoadingList(rows = 3) }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvLoadingPreview() {
    PvPreviewTheme { PvLoading(label = "Connecting…") }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvEmptyStatePreview() {
    PvPreviewTheme {
        PvEmptyState(
            title = "No payments yet",
            body = "Payments announced on this phone will show up here.",
        )
    }
}

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvErrorStatePreview() {
    PvPreviewTheme {
        PvErrorState(
            title = "Couldn't load payments",
            body = "Check your connection and try again.",
            onRetry = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Feedback
// ---------------------------------------------------------------------------

@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font 1.3", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvSnackbarPreview() {
    PvPreviewTheme {
        val state = rememberPvSnackbarState()
        PvSnackbarHost(state)
    }
}
