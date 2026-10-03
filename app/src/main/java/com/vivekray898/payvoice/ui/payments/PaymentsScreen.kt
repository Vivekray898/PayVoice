package com.vivekray898.payvoice.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivekray898.payvoice.R
import com.vivekray898.payvoice.core.parser.AmountExtractor
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.ui.MainViewModel
import com.vivekray898.payvoice.ui.components.PvDivider
import com.vivekray898.payvoice.ui.components.PvTab
import com.vivekray898.payvoice.ui.components.PvTabScaffold
import com.vivekray898.payvoice.ui.components.PvSegmentedGroup
import com.vivekray898.payvoice.ui.components.PvEmptyState
import com.vivekray898.payvoice.ui.components.PvErrorState
import com.vivekray898.payvoice.ui.components.PvLoadingList
import com.vivekray898.payvoice.ui.components.PvPaymentRow
import com.vivekray898.payvoice.ui.components.PvScaffold
import com.vivekray898.payvoice.ui.components.PvSecondaryButton
import com.vivekray898.payvoice.ui.components.PvSectionHeader
import com.vivekray898.payvoice.ui.components.PvTextField
import com.vivekray898.payvoice.ui.components.PvTopBar
import com.vivekray898.payvoice.ui.components.timeAgo
import com.vivekray898.payvoice.ui.theme.Spacing

/** Which slice of history the screen is showing. */
enum class PaymentsRange { TODAY, WEEK, MONTH, ALL }

/** One row, already formatted for display. Immutable so the list skips well. */
@Immutable
data class PaymentRowUi(
    val key: String,
    val amountText: String,
    val source: String?,
    val sender: String?,
    val timeText: String,
)

/**
 * The screen state, exactly as the brief requires: an immutable `UiState`
 * the composable renders and never mutates.
 *
 * Three mandatory states are modelled explicitly rather than inferred from
 * `list.isEmpty()`, which is what made the old screens show "No employees
 * yet" during the first fetch.
 */
@Immutable
data class PaymentsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val query: String = "",
    val range: PaymentsRange = PaymentsRange.TODAY,
    val rows: List<PaymentRowUi> = emptyList(),
    val totalCount: Int = 0,
    val totalMinor: Long = 0,
    val hasMore: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && !error && rows.isEmpty()
}

/** Windowed paging: enough for a history list without a Paging3 dependency. */
private const val PAGE_SIZE = 30

/**
 * Payments history.
 *
 * Owner: searchable and filterable (today / week / month / all) with windowed
 * paging. Employee: the same screen, read-only and capped — they can see what
 * the business received, not edit anything.
 *
 * This screen is what "Show all payments" should always have opened; before
 * it existed that button routed to the technical diagnostics log.
 */
@Composable
fun PaymentsScreen(
    viewModel: MainViewModel,
    selected: PvTab = PvTab.LIST,
    onSelectTab: (PvTab) -> Unit = {},
    role: DeviceRole = DeviceRole.OWNER,
    readOnly: Boolean = false,
) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var range by remember { mutableStateOf(PaymentsRange.TODAY) }
    var limit by remember { mutableStateOf(PAGE_SIZE) }

    val state = buildState(
        history = history,
        loading = false,
        query = query,
        range = range,
        limit = if (readOnly) minOf(limit, 20) else limit,
    )

    PvTabScaffold(
        role = role,
        selected = selected,
        onSelect = onSelectTab,
        topBar = { PvTopBar(title = stringResource(R.string.payments_title)) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                // Without this the search field and range chips were laid out
                // UNDER the top bar and clipped by it.
                top = inner.calculateTopPadding() + Spacing.sm,
                bottom = Spacing.xxl + inner.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "search") {
                if (!readOnly) {
                    PvTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            limit = PAGE_SIZE
                        },
                        label = stringResource(R.string.payments_search),
                        placeholder = stringResource(R.string.payments_search_hint),
                    )
                }
            }

            item(key = "ranges") {
                if (!readOnly) {
                    PvSegmentedGroup(
                        options = PaymentsRange.entries,
                        selected = range,
                        onSelect = { candidate ->
                            range = candidate
                            limit = PAGE_SIZE
                        },
                        label = { candidate -> rangeLabel(candidate) },
                    )
                }
            }

            item(key = "summary") {
                if (!state.loading && !state.error) {
                    Column {
                        PvSectionHeader(
                            text = stringResource(
                                R.string.payments_summary,
                                state.totalCount,
                                AmountExtractor.formatMinor(state.totalMinor, "INR"),
                            ),
                        )
                    }
                }
            }

            when {
                state.loading -> item(key = "loading") { PvLoadingList(rows = 6) }

                state.error -> item(key = "error") {
                    PvErrorState(
                        title = stringResource(R.string.payments_error_title),
                        body = stringResource(R.string.payments_error_body),
                        onRetry = { viewModel.refreshStatus() },
                        retryLabel = stringResource(R.string.health_retry),
                    )
                }

                state.isEmpty -> item(key = "empty") {
                    PvEmptyState(
                        title = stringResource(R.string.payments_empty_title),
                        body = if (query.isBlank()) {
                            stringResource(R.string.payments_empty_body)
                        } else {
                            stringResource(R.string.payments_empty_search, query)
                        },
                    )
                }

                else -> {
                    items(state.rows.size, key = { state.rows[it].key }) { index ->
                        val row = state.rows[index]
                        if (index > 0) PvDivider()
                        PvPaymentRow(
                            amountText = row.amountText,
                            source = row.source,
                            sender = row.sender,
                            timeText = row.timeText,
                        )
                    }
                    if (state.hasMore && !readOnly) {
                        item(key = "more") {
                            Spacer(Modifier.height(Spacing.sm))
                            PvSecondaryButton(
                                text = stringResource(R.string.payments_load_more),
                                onClick = { limit += PAGE_SIZE },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rangeLabel(range: PaymentsRange): String = stringResource(
    when (range) {
        PaymentsRange.TODAY -> R.string.range_today
        PaymentsRange.WEEK -> R.string.range_week
        PaymentsRange.MONTH -> R.string.range_month
        PaymentsRange.ALL -> R.string.range_all
    },
)



/**
 * Pure function over the history list so the state is testable and previewable
 * without a ViewModel.
 */
internal fun buildState(
    history: List<com.vivekray898.payvoice.core.database.AnnouncementEntity>,
    loading: Boolean,
    query: String,
    range: PaymentsRange,
    limit: Int,
): PaymentsUiState {
    if (loading) return PaymentsUiState(loading = true)

    val from = when (range) {
        PaymentsRange.TODAY -> startOfDayMs()
        PaymentsRange.WEEK -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        PaymentsRange.MONTH -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        PaymentsRange.ALL -> 0L
    }

    val needle = query.trim().lowercase()
    val filtered = history
        .asSequence()
        .filter { it.announcedAtMs >= from }
        .filter {
            needle.isEmpty() ||
                it.sourceName.lowercase().contains(needle) ||
                (it.senderName ?: "").lowercase().contains(needle)
        }
        .toList()

    val windowed = filtered.take(limit)
    return PaymentsUiState(
        loading = false,
        error = false,
        query = query,
        range = range,
        rows = windowed.map { e ->
            PaymentRowUi(
                key = e.eventId,
                amountText = AmountExtractor.formatMinor(e.amountMinor, e.currency),
                source = e.sourceName,
                sender = e.senderName,
                timeText = timeAgo(e.announcedAtMs),
            )
        },
        totalCount = filtered.size,
        totalMinor = filtered.sumOf { it.amountMinor },
        hasMore = filtered.size > windowed.size,
    )
}

private fun startOfDayMs(): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
