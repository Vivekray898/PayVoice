package com.vivekray898.payvoice.ui.reliability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.R
import com.vivekray898.payvoice.core.health.HealthSummary
import com.vivekray898.payvoice.core.health.InAppAction
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvErrorState
import com.vivekray898.payvoice.ui.components.PvLoadingList
import com.vivekray898.payvoice.ui.components.PvPermissionCard
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSectionHeader
import com.vivekray898.payvoice.ui.components.PvTopBar
import com.vivekray898.payvoice.ui.components.StatusPill
import com.vivekray898.payvoice.ui.components.StatusTone
import com.vivekray898.payvoice.ui.theme.Spacing

/**
 * "Fix a problem" — the full-screen version of the Home health banner.
 *
 * This screen previously built its own local `data class Check` list, which is
 * why it could disagree with Home and why none of it was testable. It now
 * renders exactly what [com.vivekray898.payvoice.core.health.AppHealthChecker]
 * returns, so there is one source of truth for "is this phone ready".
 *
 * Route name (`reliability`) is kept for now; renaming the navigation graph
 * is part of the Step 4 screen map.
 */
@Composable
fun ReliabilityScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val health by viewModel.health.collectAsStateWithLifecycle()
    val loading by viewModel.healthLoading.collectAsStateWithLifecycle()

    PvScaffold(
        topBar = { PvTopBar(title = "Health & fixes", onBack = onBack) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.xxl + inner.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when {
                loading && health == null -> item(key = "loading") {
                    PvLoadingList(rows = 5)
                }

                health == null -> item(key = "error") {
                    PvErrorState(
                        title = stringResource(R.string.health_check_error),
                        body = stringResource(R.string.health_check_error_why),
                        onRetry = { viewModel.refreshHealth() },
                        retryLabel = stringResource(R.string.health_retry),
                    )
                }

                else -> {
                    val summary = health ?: HealthSummary(emptyList())
                    item(key = "summary") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(
                                text = if (summary.healthy) {
                                    stringResource(R.string.health_banner_all_good)
                                } else {
                                    stringResource(
                                        R.string.health_banner_blocked,
                                        summary.attentionCount,
                                    )
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            StatusPill(
                                text = if (summary.healthy) {
                                    stringResource(R.string.health_banner_all_good)
                                } else {
                                    "${summary.attentionCount} / ${summary.items.size}"
                                },
                                tone = if (summary.healthy) StatusTone.Success else StatusTone.Warning,
                            )
                        }
                    }

                    val attention = summary.ordered().filter { it.needsAttention }
                    if (attention.isNotEmpty()) {
                        item(key = "needs-fixing-header") {
                            PvSectionHeader(text = "Needs fixing")
                        }
                        items(attention.size, key = { attention[it].id }) { index ->
                            val item = attention[index]
                            PvPermissionCard(
                                item = item,
                                onClick = {
                                    when (item.inAppAction) {
                                        InAppAction.PAIR_DEVICE,
                                        InAppAction.OPEN_HEALTH,
                                        -> Unit // navigation handled by the host
                                        else -> viewModel.applyHealthFix(context, item)
                                    }
                                },
                            )
                        }
                    }

                    val healthyRows = summary.ordered().filter { !it.needsAttention }
                    if (healthyRows.isNotEmpty()) {
                        item(key = "all-clear-header") {
                            PvSectionHeader(text = "All clear")
                        }
                        items(healthyRows.size, key = { healthyRows[it].id }) { index ->
                            PvPermissionCard(item = healthyRows[index], onClick = {})
                        }
                    }

                    item(key = "note") {
                        Text(
                            "Checks run every time you return to PayVoice, so a fix " +
                                "takes effect as soon as you come back.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.md),
                        )
                    }
                }
            }
        }
    }
}
