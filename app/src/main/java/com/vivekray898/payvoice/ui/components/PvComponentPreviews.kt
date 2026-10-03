package com.vivekray898.payvoice.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * Preview harness for every shared component, in the four variants the brief
 * requires: light . dark . fontScale 1.3 . 320dp width.
 *
 * The four @Preview annotations are stacked directly rather than folded into a
 * meta-annotation, so nothing depends on repeatable-annotation flattening to
 * render in Android Studio.
 *
 * Each component has a PUBLIC gallery composable (`PvPreviewX`) plus a private
 * @Preview wrapper. `src/screenshotTest` wraps the same public composables in
 * @PreviewTest, so the IDE previews and the committed screenshot baselines
 * render exactly the same code with no duplication.
 *
 * Screens get their previews in Step 4 alongside their UiState extraction:
 * they currently take a MainViewModel, which the preview renderer cannot
 * construct.
 */

/** Shared frame: brand theme + background + 16dp gutter. */
@Composable
fun PvPreviewTheme(content: @Composable () -> Unit) {
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


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewPrimaryButton() {
    PvPreviewTheme {
        PvPrimaryButton(text = "Add employee", onClick = {})
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvPrimaryButtonPreview() = PvPreviewPrimaryButton()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewSecondaryButton() {
    PvPreviewTheme {
        PvSecondaryButton(text = "Show all payments", onClick = {})
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvSecondaryButtonPreview() = PvPreviewSecondaryButton()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewTextButton() {
    PvPreviewTheme {
        PvTextButton(text = "Turn on", onClick = {})
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvTextButtonPreview() = PvPreviewTextButton()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewLink() {
    PvPreviewTheme {
        PvLink(text = "Manage permissions", onClick = {})
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvLinkPreview() = PvPreviewLink()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewCard() {
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
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvCardPreview() = PvPreviewCard()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewListItem() {
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
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvListItemPreview() = PvPreviewListItem()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewSwitchRow() {
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
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvSwitchRowPreview() = PvPreviewSwitchRow()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewIconCircle() {
    PvPreviewTheme {
        PvIconCircle(icon = Icons.Filled.People)
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvIconCirclePreview() = PvPreviewIconCircle()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewSectionHeader() {
    PvPreviewTheme {
        PvSectionHeader(text = "Announcements", gutter = Spacing.gutter)
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvSectionHeaderPreview() = PvPreviewSectionHeader()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewDivider() {
    PvPreviewTheme {
        PvDivider()
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvDividerPreview() = PvPreviewDivider()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewTextField() {
    PvPreviewTheme {
        PvTextField(value = "Shop name", onValueChange = {}, label = "Business name")
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvTextFieldPreview() = PvPreviewTextField()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewCodeField() {
    PvPreviewTheme {
        PvCodeField(value = "K7QM2XR4PB", onValueChange = {})
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvCodeFieldPreview() = PvPreviewCodeField()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewStatusPill() {
    PvPreviewTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatusPill(text = "All good", tone = StatusTone.Success)
                    StatusPill(text = "Needs fixing", tone = StatusTone.Warning)
                    StatusPill(text = "Blocked", tone = StatusTone.Error)
                    StatusPill(text = "Paused", tone = StatusTone.Neutral)
                }
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvStatusPillPreview() = PvPreviewStatusPill()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewStatusIcon() {
    PvPreviewTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatusIcon(ok = true)
                    StatusIcon(ok = false)
                    StatusIcon(ok = null)
                }
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvStatusIconPreview() = PvPreviewStatusIcon()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewPaymentRow() {
    PvPreviewTheme {
        PvPaymentRow(
                    amountText = "\u20b91,250.00",
                    source = "Google Pay",
                    sender = "Ravi",
                    timeText = "2m ago",
                )
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvPaymentRowPreview() = PvPreviewPaymentRow()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewStatTile() {
    PvPreviewTheme {
        PvStatTile(
                    label = "Received today",
                    value = "\u20b912,480",
                    supporting = "7 payments",
                    tabular = true,
                )
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvStatTilePreview() = PvPreviewStatTile()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewLoadingList() {
    PvPreviewTheme {
        PvLoadingList(rows = 3)
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvLoadingListPreview() = PvPreviewLoadingList()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewLoading() {
    PvPreviewTheme {
        PvLoading(label = "Connecting\u2026")
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvLoadingPreview() = PvPreviewLoading()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewEmptyState() {
    PvPreviewTheme {
        PvEmptyState(
                    title = "No payments yet",
                    body = "Payments announced on this phone will show up here.",
                )
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvEmptyStatePreview() = PvPreviewEmptyState()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewErrorState() {
    PvPreviewTheme {
        PvErrorState(
                    title = "Couldn\u2019t load payments",
                    body = "Check your connection and try again.",
                    onRetry = {},
                )
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvErrorStatePreview() = PvPreviewErrorState()


/** Gallery body shared with the screenshot test source set. */
@Composable
fun PvPreviewSnackbar() {
    PvPreviewTheme {
        val state = rememberPvSnackbarState()
                PvSnackbarHost(state)
    }
}


@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
private fun PvSnackbarPreview() = PvPreviewSnackbar()
