package com.vivekray898.payvoice.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot baselines for the shared component library.
 *
 * Renders the same public `PvPreview*` composables that power the IDE
 * previews in `ui/components/PvComponentPreviews.kt`, in the same four
 * variants (light / dark / fontScale 1.3 / 320dp).
 *
 *   ./gradlew updateDebugScreenshotTest   # regenerate baselines
 *   ./gradlew validateDebugScreenshotTest # fail on any visual change
 *
 * Baselines live in `app/src/screenshotTestDebug/reference/`.
 */


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvPrimaryButtonScreenshot() = PvPreviewPrimaryButton()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvSecondaryButtonScreenshot() = PvPreviewSecondaryButton()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvTextButtonScreenshot() = PvPreviewTextButton()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvLinkScreenshot() = PvPreviewLink()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvCardScreenshot() = PvPreviewCard()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvListItemScreenshot() = PvPreviewListItem()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvSwitchRowScreenshot() = PvPreviewSwitchRow()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvIconCircleScreenshot() = PvPreviewIconCircle()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvSectionHeaderScreenshot() = PvPreviewSectionHeader()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvDividerScreenshot() = PvPreviewDivider()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvTextFieldScreenshot() = PvPreviewTextField()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvCodeFieldScreenshot() = PvPreviewCodeField()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvStatusPillScreenshot() = PvPreviewStatusPill()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvStatusIconScreenshot() = PvPreviewStatusIcon()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvPaymentRowScreenshot() = PvPreviewPaymentRow()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvStatTileScreenshot() = PvPreviewStatTile()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvLoadingListScreenshot() = PvPreviewLoadingList()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvLoadingScreenshot() = PvPreviewLoading()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvEmptyStateScreenshot() = PvPreviewEmptyState()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvErrorStateScreenshot() = PvPreviewErrorState()


@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
@Preview(name = "320dp", showBackground = true, widthDp = 320)
@Composable
fun PvSnackbarScreenshot() = PvPreviewSnackbar()
