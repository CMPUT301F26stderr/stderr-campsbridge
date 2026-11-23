package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.components.AllocationSummary

/** Shows saved offers; choosing one navigates without claiming nights or saving a reservation.
 * @param state Shared saved reader's required source outcomes.
 * @param decline Current command guard for this same opportunity.
 * @param onBack Returns to the retained lottery overview.
 * @param onRetry Retries the parent source.
 * @param onRetryOffers Retries saved offers and campsite facts.
 * @param onRetryOpportunity Retries unknown consumption.
 * @param onChoose Passes the actual lottery and campsite IDs to booking.
 */
@Composable
fun LotteryCampsiteChoiceScreen(
    state: LotteryDetailUiState,
    decline: LotteryDeclineUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryOffers: () -> Unit,
    onRetryOpportunity: () -> Unit,
    onChoose: (String, String) -> Unit,
) {
    LotteryDetailFrame(
        stringResource(R.string.lottery_choose_campsite),
        state,
        onBack,
        onRetry,
        backEnabled = !decline.isSubmitting,
    ) { current ->
        Text(current.lottery.title, style = MaterialTheme.typography.headlineSmall)
        when {
            current.opportunity == LotteryOpportunityRead.Loading -> {
                LoadingContent()
            }

            current.opportunity == LotteryOpportunityRead.Failure -> {
                FailureContent(stringResource(R.string.lottery_opportunity_failed), onRetryOpportunity)
            }

            !winningOpportunityIsAvailable(current, decline) -> {
                Text(stringResource(R.string.lottery_win_unavailable))
            }

            else -> {
                Text(stringResource(R.string.lottery_choose_campsite_hint))
                when (val offers = current.offers) {
                    LotteryOffersState.Loading -> {
                        LoadingContent()
                    }

                    LotteryOffersState.Failure -> {
                        FailureContent(stringResource(R.string.lottery_offers_failed), onRetryOffers)
                    }

                    is LotteryOffersState.Content -> {
                        if (offers.offers.isEmpty()) Text(stringResource(R.string.lottery_no_offers))
                        for (summary in offers.offers) {
                            AllocationSummary(summary.offer, summary.campsite) { id ->
                                val name = summary.campsite?.details?.name ?: id
                                val actionLabel = stringResource(R.string.lottery_choose_campsite_action, name)
                                AppActionButton(
                                    stringResource(R.string.lottery_choose_campsite),
                                    { onChoose(current.lottery.id, id) },
                                    Modifier.fillMaxWidth().semantics { contentDescription = actionLabel },
                                    style = ActionStyle.SECONDARY,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
