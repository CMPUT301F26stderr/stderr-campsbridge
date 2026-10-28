package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.components.LotteryCard
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.LocalDate

/** Ordinary Material list content; its workspace or child AppScreen belongs to navigation.
 * @param state Confirmed rows and unresolved references.
 * @param category Selected derived category.
 * @param categories Allowed categories for this explicit context.
 * @param onSelectCategory Explicit tab selection.
 * @param onOpen Parent-owned actual-ID navigation.
 * @param onRetry Retries failed sources.
 * @param modifier Caller layout.
 * @param listState Category's saveable scroll owner.
 * @param onCreate Optional real setup entry.
 * @param campsiteId Optional active owner association filter.
 * @param onClearCampsite Clears that filter without changing saved records.
 * @param contexts Independently observed campsite facts for visible resolved rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryListScreen(
    state: LotteryListUiState,
    category: LotteryListCategory,
    categories: List<LotteryListCategory>,
    onSelectCategory: (LotteryListCategory) -> Unit,
    onOpen: (LotteryListItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onCreate: (() -> Unit)? = null,
    campsiteId: String? = null,
    onClearCampsite: () -> Unit = {},
    contexts: Map<String, LotteryOffersState> = emptyMap(),
) {
    Column(modifier.fillMaxSize()) {
        LotteryCategoryTabs(category, categories, onSelectCategory)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            if (campsiteId != null) {
                item(key = "filter") {
                    Text(
                        stringResource(R.string.lottery_filtered_campsite, campsiteId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClearCampsite) { Text(stringResource(R.string.lottery_show_all)) }
                }
            }
            when (state) {
                LotteryListUiState.Loading -> {
                    item { LoadingContent() }
                }

                LotteryListUiState.Failure -> {
                    item {
                        FailureContent(stringResource(R.string.lottery_list_failed), onRetry)
                    }
                }

                is LotteryListUiState.Content -> {
                    if (state.items.isEmpty() && state.unresolved.isEmpty()) {
                        item {
                            EmptyContent(
                                stringResource(
                                    R.string.lottery_list_empty,
                                ),
                            )
                        }
                    }
                    items(state.unresolved, key = { "unresolved:${it.lotteryId}" }) { reference ->
                        val label =
                            when (reference.issue) {
                                LotteryReferenceIssue.LOADING -> R.string.lottery_reference_loading
                                LotteryReferenceIssue.MISSING -> R.string.lottery_reference_missing
                                LotteryReferenceIssue.FAILURE -> R.string.lottery_reference_failed
                            }
                        SummarySection(stringResource(label)) {
                            Text(
                                reference.lotteryId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (reference.issue == LotteryReferenceIssue.FAILURE) {
                                TextButton(onRetry) { Text(stringResource(R.string.retry)) }
                            }
                        }
                    }
                    items(state.items, key = { it.lottery.id }) { item ->
                        val context = contexts[item.lottery.id] ?: LotteryOffersState.Loading
                        LotteryCard(
                            item.lottery,
                            lotteryLifecycleLabel(item.lifecycle),
                            lotteryListDeadline(item, context),
                            { onOpen(item) },
                            supportingContent = {
                                LotteryRowContext(context, onRetry)
                                val outcome =
                                    if (item.reservationId != null) {
                                        R.string.lottery_list_reservation_made
                                    } else {
                                        when (item.entryState) {
                                            EntryState.WAITING -> {
                                                R.string.lottery_membership_waiting
                                            }

                                            EntryState.WITHDRAWN -> {
                                                R.string.lottery_membership_withdrawn
                                            }

                                            EntryState.OFFERED -> {
                                                R.string.lottery_membership_won
                                            }

                                            EntryState.LOST -> {
                                                R.string.lottery_membership_lost
                                            }

                                            EntryState.DECLINED -> {
                                                R.string.lottery_membership_declined
                                            }

                                            null -> {
                                                null
                                            }
                                        }
                                    }
                                if (outcome != null) {
                                    Text(
                                        stringResource(outcome),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
        onCreate?.let { action ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.medium),
                horizontalArrangement = Arrangement.End,
            ) {
                ExtendedFloatingActionButton(
                    onClick = action,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(Spacing.small))
                    Text(stringResource(R.string.lottery_create))
                }
            }
        }
    }
}

/** Uses Material scrolling for the longer entrant labels and the existing page gutter.
 * @param category Current scope-allowed category.
 * @param categories Actual categories in display order.
 * @param onSelect Explicit selection without resetting other category scroll positions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LotteryCategoryTabs(
    category: LotteryListCategory,
    categories: List<LotteryListCategory>,
    onSelect: (LotteryListCategory) -> Unit,
) {
    val tabs: @Composable () -> Unit = {
        for (item in categories) {
            Tab(
                selected = item == category,
                onClick = { onSelect(item) },
                text = {
                    Text(lotteryCategoryLabel(item), style = MaterialTheme.typography.labelLarge)
                },
            )
        }
    }
    if (categories.size > 2) {
        PrimaryScrollableTabRow(categories.indexOf(category), edgePadding = 0.dp, tabs = tabs)
    } else {
        PrimaryTabRow(categories.indexOf(category), tabs = tabs)
    }
}

/** Shows only known allocation/site facts without making their absence a primary-list failure.
 * @param state Independent visible-row display result.
 * @param onRetry Retries failed context sources through the owner.
 */
@Composable
private fun LotteryRowContext(
    state: LotteryOffersState,
    onRetry: () -> Unit,
) {
    when (state) {
        LotteryOffersState.Loading -> {
            Text(
                stringResource(R.string.lottery_list_context_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LotteryOffersState.Failure -> {
            Text(
                stringResource(R.string.lottery_list_context_failed),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onRetry) { Text(stringResource(R.string.retry)) }
        }

        is LotteryOffersState.Content -> {
            if (state.offers.isEmpty()) {
                Text(
                    stringResource(R.string.lottery_no_offers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (summary in state.offers) {
                Text(
                    summary.campsite?.details?.name
                        ?: stringResource(R.string.lottery_unavailable_campsite),
                    style = MaterialTheme.typography.bodyMedium,
                )
                summary.campsite?.details?.locationLabel?.let { location ->
                    Text(
                        location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val count = summary.offer.nights.size
                Text(
                    pluralStringResource(R.plurals.lottery_allocated_nights, count, count),
                    style = MaterialTheme.typography.labelLarge,
                )
                val locale = LocalConfiguration.current.locales[0]
                Text(
                    summary.offer.nights
                        .sorted()
                        .joinToString { formatDate(it, locale) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Formats the task-relevant saved boundary in an explicit zone, without a per-row site query.
 * @param item Confirmed row and controller-derived phase.
 * @param context Actual visible-row campsite facts, or explicitly unresolved context.
 */
@Composable
private fun lotteryListDeadline(
    item: LotteryListItem,
    context: LotteryOffersState,
): String {
    val windows = item.lottery.configuration
    val (label, instant) =
        when (item.lifecycle) {
            LotteryLifecycle.DRAFT, LotteryLifecycle.UPCOMING -> {
                R.string.lottery_registration_starts to
                    windows.registrationOpensAt
            }

            LotteryLifecycle.REGISTRATION_OPEN, LotteryLifecycle.DRAW_PENDING -> {
                R.string.lottery_registration_ends to
                    windows.registrationClosesAt
            }

            LotteryLifecycle.AWAITING_BOOKING -> {
                R.string.lottery_booking_starts to
                    windows.bookingOpensAt
            }

            else -> {
                R.string.lottery_booking_deadline to windows.bookingClosesAt
            }
        }
    return stringResource(
        label,
        formatDateTime(instant, lotteryDisplayZone(context), LocalConfiguration.current.locales[0]),
    )
}

/** Localizes the category selected by the controller.
 * @param category Explicit scope-allowed tab.
 */
@Composable
internal fun lotteryCategoryLabel(category: LotteryListCategory): String =
    stringResource(
        when (category) {
            LotteryListCategory.PUBLISHED -> R.string.campsite_published
            LotteryListCategory.DRAFTS -> R.string.campsite_drafts
            LotteryListCategory.ACTION_NEEDED -> R.string.lottery_action_needed
            LotteryListCategory.WAITING -> R.string.lottery_waiting
            LotteryListCategory.HISTORY -> R.string.lottery_history
        },
    )

/** Passive list fixtures reuse saved-detail values without starting dependencies. */
class LotteryListPreviewProvider : PreviewParameterProvider<LotteryListUiState> {
    override val values: Sequence<LotteryListUiState> =
        run {
            val content =
                LotteryDetailPreviewProvider()
                    .values
                    .filterIsInstance<LotteryDetailUiState.Content>()
                    .first {
                        it.lottery.publishedAt !=
                            null
                    }
            sequenceOf(
                LotteryListUiState.Content(
                    listOf(LotteryListItem(content.lottery, content.lifecycle)),
                ),
                LotteryListUiState.Content(emptyList()),
                LotteryListUiState.Loading,
                LotteryListUiState.Failure,
                LotteryListUiState.Content(
                    emptyList(),
                    listOf(LotteryListReference("unavailable-draw", LotteryReferenceIssue.MISSING)),
                ),
            )
        }
}

/** Shows content, empty/error and unresolved references with ordinary and enlarged text.
 * @param state Passive list fixture.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun LotteryListPreview(
    @PreviewParameter(LotteryListPreviewProvider::class) state: LotteryListUiState,
) {
    CampsbridgeTheme {
        AppScreen(stringResource(R.string.my_lotteries)) {
            LotteryListScreen(
                state,
                LotteryListCategory.PUBLISHED,
                listOf(LotteryListCategory.PUBLISHED, LotteryListCategory.DRAFTS),
                {},
                {},
                {},
                onCreate = {},
                contexts =
                    (state as? LotteryListUiState.Content)?.items.orEmpty().associate {
                        val site = previewCampsite()
                        it.lottery.id to
                            LotteryOffersState.Content(
                                listOf(
                                    LotteryOfferSummary(
                                        LotteryOffer(
                                            site.id,
                                            listOf(
                                                LocalDate.of(2027, 5, 14),
                                                LocalDate.of(2027, 5, 15),
                                            ),
                                        ),
                                        site,
                                    ),
                                ),
                            )
                    },
            )
        }
    }
}
