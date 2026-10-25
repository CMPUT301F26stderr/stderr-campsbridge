package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.InlineNotice
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.components.LotteryCard
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.ZoneId

/** Connects the read owner to real navigation and retains a scroll position per category.
 * @param model Account-scoped source owner.
 * @param onCreate Registered new-setup entry, absent in entrant presentation.
 * @param onOpen Navigation using the selected actual row identity.
 */
@Composable
fun LotteryListRoute(
    model: LotteryListViewModel,
    onCreate: (() -> Unit)?,
    onOpen: (LotteryListItem) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val category by model.category.collectAsStateWithLifecycle()
    val campsite by model.campsiteId.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(model, lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED) model.refreshTime()
    }
    // Each remembered category keeps its own saveable lazy-list position.
    val positions = model.categories.associateWith { item -> key(item) { rememberLazyListState() } }
    LotteryListScreen(
        state,
        category,
        model.categories,
        model::selectCategory,
        onOpen,
        model::retry,
        listState = positions.getValue(category),
        onCreate = onCreate,
        campsiteId = campsite,
        onClearCampsite = { model.selectCampsite(null) },
    )
}

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
) {
    Column(modifier.fillMaxSize()) {
        onCreate?.let { action ->
            TextButton(action) { Text(stringResource(R.string.lottery_create)) }
        }
        PrimaryTabRow(categories.indexOf(category)) {
            for (item in categories) {
                Tab(selected = item == category, onClick = {
                    onSelectCategory(item)
                }, text = { Text(lotteryCategoryLabel(item)) })
            }
        }
        if (campsiteId != null) {
            Text(stringResource(R.string.lottery_filtered_campsite, campsiteId))
            TextButton(onClearCampsite) { Text(stringResource(R.string.lottery_show_all)) }
        }
        when (state) {
            LotteryListUiState.Loading -> {
                LoadingContent()
            }

            LotteryListUiState.Failure -> {
                FailureContent(stringResource(R.string.lottery_list_failed), onRetry)
            }

            is LotteryListUiState.Content -> {
                if (state.items.isEmpty() && state.unresolved.isEmpty()) {
                    EmptyContent(
                        stringResource(R.string.lottery_list_empty, lotteryCategoryLabel(category)),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = Spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                    ) {
                        items(
                            state.unresolved,
                            key = { "unresolved:${it.lotteryId}" },
                        ) { reference ->
                            val label =
                                when (reference.issue) {
                                    LotteryReferenceIssue.LOADING -> {
                                        R.string.lottery_reference_loading
                                    }

                                    LotteryReferenceIssue.MISSING -> {
                                        R.string.lottery_reference_missing
                                    }

                                    LotteryReferenceIssue.FAILURE -> {
                                        R.string.lottery_reference_failed
                                    }
                                }
                            InlineNotice(
                                stringResource(label),
                                reference.lotteryId,
                                action =
                                    if (reference.issue == LotteryReferenceIssue.FAILURE) {
                                        {
                                            TextButton(
                                                onRetry,
                                            ) { Text(stringResource(R.string.retry)) }
                                        }
                                    } else {
                                        null
                                    },
                            )
                        }
                        items(state.items, key = { it.lottery.id }) { item ->
                            val record = item.lottery
                            LotteryCard(
                                record,
                                lotteryLifecycleLabel(
                                    item.lifecycle,
                                ),
                                lotteryListDeadline(item),
                                {
                                    onOpen(item)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Formats the task-relevant saved boundary in an explicit zone, without a per-row site query.
 * @param item Confirmed row and controller-derived phase.
 */
@Composable
private fun lotteryListDeadline(item: LotteryListItem): String {
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
        formatDateTime(instant, ZoneId.of("UTC"), LocalConfiguration.current.locales[0]),
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
        LotteryListScreen(
            state,
            LotteryListCategory.PUBLISHED,
            listOf(LotteryListCategory.PUBLISHED, LotteryListCategory.DRAFTS),
            {},
            {},
            {},
            onCreate = {},
        )
    }
}
