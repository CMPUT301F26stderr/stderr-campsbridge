package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.components.LotteryCard
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Same-parent choice list; an allocation failure cannot hide a real associated lottery.
 * @param state Saved campsite result.
 * @param lotteries Independent scoped association and row outcomes.
 * @param onBack Returns to the same saved overview.
 * @param onRetry Retries only a failed parent listing.
 * @param onRetryLotteries Retries only the failed association.
 * @param onRetryOffer Retries only the selected row's failed allocation.
 * @param onOpenLottery Opens the existing entrant saved detail by actual ID.
 * @param modifier Caller layout.
 */
@Composable
internal fun CampsiteLotteryChoicesScreen(
    state: CampsiteDetailUiState,
    lotteries: CampsiteLotteriesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryLotteries: () -> Unit,
    onRetryOffer: (String) -> Unit,
    onOpenLottery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(stringResource(R.string.campsite_available_lotteries), modifier, onBack = onBack) {
        when (state) {
            CampsiteDetailUiState.Loading -> {
                LoadingContent()
            }

            CampsiteDetailUiState.Failure -> {
                FailureContent(
                    stringResource(R.string.campsite_detail_failure),
                    onRetry,
                )
            }

            CampsiteDetailUiState.Unavailable -> {
                Text(stringResource(R.string.campsite_unavailable))
            }

            is CampsiteDetailUiState.Content -> {
                val site = state.campsite
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                    item { Text(site.details.name, style = MaterialTheme.typography.headlineSmall) }
                    when (lotteries) {
                        CampsiteLotteriesUiState.Unavailable -> {
                            Unit
                        }

                        CampsiteLotteriesUiState.Loading -> {
                            item { LoadingContent() }
                        }

                        CampsiteLotteriesUiState.Failure -> {
                            item {
                                FailureContent(
                                    stringResource(R.string.campsite_lotteries_failed),
                                    onRetryLotteries,
                                )
                            }
                        }

                        is CampsiteLotteriesUiState.Content -> {
                            if (lotteries.choices.isEmpty()) {
                                item {
                                    Text(stringResource(R.string.campsite_no_active_lottery))
                                }
                            }
                            items(lotteries.choices, key = { it.lottery.id }) { choice ->
                                LotteryCard(
                                    choice.lottery,
                                    stringResource(R.string.lottery_registration_open),
                                    campsiteLotteryDeadline(choice.lottery, site.details.timeZone),
                                    { onOpenLottery(choice.lottery.id) },
                                    supportingContent = {
                                        when (val offer = choice.offer) {
                                            CampsiteLotteryOfferRead.Loading -> {
                                                LoadingContent()
                                            }

                                            CampsiteLotteryOfferRead.Unavailable -> {
                                                Text(
                                                    stringResource(
                                                        R.string.campsite_lottery_offer_unavailable,
                                                    ),
                                                )
                                            }

                                            CampsiteLotteryOfferRead.Failure -> {
                                                FailureContent(
                                                    stringResource(
                                                        R.string.campsite_lottery_offer_failed,
                                                    ),
                                                    { onRetryOffer(choice.lottery.id) },
                                                )
                                            }

                                            is CampsiteLotteryOfferRead.Ready -> {
                                                val locale = LocalConfiguration.current.locales[0]
                                                Text(
                                                    offer.offer.nights.joinToString {
                                                        formatDate(
                                                            it,
                                                            locale,
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Previews real list geometry, long titles and independent allocation failures without readers.
 * @param state Passive scoped-list variant.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun CampsiteLotteryChoicesPreview(
    @PreviewParameter(CampsiteLotteryPreviewProvider::class) state: CampsiteLotteriesUiState,
) {
    CampsbridgeTheme {
        CampsiteLotteryChoicesScreen(CampsiteDetailUiState.Content(previewCampsite()), state, {
        }, {}, {}, {}, {})
    }
}
