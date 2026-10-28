package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Passive category and independently supplied context, without repositories or commands.
 * @param state Actual-shaped primary list result.
 * @param category Scope-allowed selected tab.
 * @param contexts Independently supplied visible campsite outcomes.
 */
data class LotteryListDisplayPreviewState(
    val state: LotteryListUiState,
    val category: LotteryListCategory,
    val contexts: Map<String, LotteryOffersState>,
)

/** Covers entrant outcomes and independent context states without faking production operations. */
class LotteryListDisplayPreviewProvider : PreviewParameterProvider<LotteryListDisplayPreviewState> {
    override val values: Sequence<LotteryListDisplayPreviewState> =
        run {
            val details =
                LotteryDetailPreviewProvider()
                    .values
                    .filterIsInstance<LotteryDetailUiState.Content>()
                    .toList()
            val base = details.first { it.lifecycle == LotteryLifecycle.UPCOMING }
            val waiting =
                details.first {
                    (it.membership as? LotteryMembershipRead.Content)?.status ==
                        LotteryMembershipStatus.WAITING
                }
            val winner = details.first { it.opportunity == LotteryOpportunityRead.Unused }
            val loaded = base.offers as LotteryOffersState.Content
            val missing = loaded.copy(offers = loaded.offers.map { it.copy(campsite = null) })
            val long =
                base.lottery.copy(
                    title =
                        "Riverside long weekend camping with family and friends " +
                            "in the mountain valley",
                )
            val longSiteName = "Riverside campsite beside the mountain valley walking trails"
            val longOffers =
                loaded.copy(
                    offers =
                        loaded.offers.map { summary ->
                            summary.copy(
                                campsite =
                                    summary.campsite?.let { site ->
                                        site.copy(
                                            details =
                                                site.details.copy(
                                                    name = longSiteName,
                                                ),
                                        )
                                    },
                            )
                        },
                )
            sequenceOf(
                LotteryListDisplayPreviewState(
                    LotteryListUiState.Content(
                        listOf(
                            LotteryListItem(waiting.lottery, waiting.lifecycle, EntryState.WAITING),
                        ),
                    ),
                    LotteryListCategory.WAITING,
                    mapOf(waiting.lottery.id to waiting.offers),
                ),
                LotteryListDisplayPreviewState(
                    LotteryListUiState.Content(
                        listOf(
                            LotteryListItem(winner.lottery, winner.lifecycle, EntryState.OFFERED),
                        ),
                    ),
                    LotteryListCategory.ACTION_NEEDED,
                    mapOf(winner.lottery.id to winner.offers),
                ),
                LotteryListDisplayPreviewState(
                    LotteryListUiState.Content(
                        listOf(
                            LotteryListItem(
                                winner.lottery,
                                winner.lifecycle,
                                EntryState.OFFERED,
                                "saved-reservation",
                            ),
                        ),
                    ),
                    LotteryListCategory.HISTORY,
                    mapOf(winner.lottery.id to winner.offers),
                ),
                LotteryListDisplayPreviewState(
                    LotteryListUiState.Content(
                        listOf(LotteryListItem(base.lottery, base.lifecycle)),
                    ),
                    LotteryListCategory.PUBLISHED,
                    mapOf(base.lottery.id to LotteryOffersState.Loading),
                ),
                LotteryListDisplayPreviewState(
                    LotteryListUiState.Content(
                        listOf(LotteryListItem(base.lottery, base.lifecycle)),
                    ),
                    LotteryListCategory.PUBLISHED,
                    mapOf(base.lottery.id to LotteryOffersState.Failure),
                ),
                LotteryListDisplayPreviewState(
                    LotteryListUiState.Content(
                        listOf(
                            LotteryListItem(
                                base.lottery.copy(publishedAt = null),
                                LotteryLifecycle.DRAFT,
                            ),
                        ),
                    ),
                    LotteryListCategory.DRAFTS,
                    mapOf(base.lottery.id to LotteryOffersState.Content(emptyList())),
                ),
                LotteryListDisplayPreviewState(
                    LotteryListUiState.Content(
                        listOf(LotteryListItem(base.lottery, base.lifecycle)),
                    ),
                    LotteryListCategory.PUBLISHED,
                    mapOf(base.lottery.id to missing),
                ),
                LotteryListDisplayPreviewState(
                    LotteryListUiState.Content(listOf(LotteryListItem(long, base.lifecycle))),
                    LotteryListCategory.PUBLISHED,
                    mapOf(long.id to longOffers),
                ),
                LotteryListDisplayPreviewState(
                    LotteryListUiState.Content(
                        listOf(
                            LotteryListItem(
                                winner.lottery,
                                LotteryLifecycle.CLOSED,
                                EntryState.LOST,
                            ),
                        ),
                    ),
                    LotteryListCategory.HISTORY,
                    mapOf(winner.lottery.id to winner.offers),
                ),
                LotteryListDisplayPreviewState(
                    LotteryListUiState.Content(
                        emptyList(),
                        listOf(
                            LotteryListReference("loading-draw", LotteryReferenceIssue.LOADING),
                            LotteryListReference("failed-draw", LotteryReferenceIssue.FAILURE),
                        ),
                    ),
                    LotteryListCategory.WAITING,
                    emptyMap(),
                ),
            )
        }
}

/** Frames passive owner/entrant lists without a second production frame or live readers.
 * @param state Current preview category, primary data and display outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun LotteryListDisplayPreview(
    @PreviewParameter(LotteryListDisplayPreviewProvider::class) state:
        LotteryListDisplayPreviewState,
) {
    val owner = state.category in listOf(LotteryListCategory.PUBLISHED, LotteryListCategory.DRAFTS)
    val categories =
        if (owner) {
            listOf(LotteryListCategory.PUBLISHED, LotteryListCategory.DRAFTS)
        } else {
            listOf(
                LotteryListCategory.ACTION_NEEDED,
                LotteryListCategory.WAITING,
                LotteryListCategory.HISTORY,
            )
        }
    CampsbridgeTheme {
        AppScreen(stringResource(R.string.my_lotteries)) {
            LotteryListScreen(
                state.state,
                state.category,
                categories,
                {},
                {},
                {},
                contexts = state.contexts,
                onCreate = if (owner) ({}) else null,
            )
        }
    }
}
