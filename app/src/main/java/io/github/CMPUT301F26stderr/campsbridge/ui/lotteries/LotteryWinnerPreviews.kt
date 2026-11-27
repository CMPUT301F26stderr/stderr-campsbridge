/*
 * Supplies saved multiple-offer facts with long names, gaps and independent recovery states.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import java.time.LocalDate

/** Supplies saved multiple-offer facts with long names, gaps and independent recovery states. */
class LotteryCampsiteChoicePreviewProvider : PreviewParameterProvider<LotteryDetailUiState> {
    override val values: Sequence<LotteryDetailUiState> =
        run {
            val base = winningLotteryPreview()
            sequenceOf(
                base,
                base.copy(offers = LotteryOffersState.Loading),
                base.copy(offers = LotteryOffersState.Failure),
                base.copy(offers = LotteryOffersState.Content(emptyList())),
                base.copy(opportunity = LotteryOpportunityRead.Failure),
                LotteryDetailUiState.Loading,
                LotteryDetailUiState.Failure,
                LotteryDetailUiState.Unavailable,
            )
        }
}

/** Supplies only command presentation; previews never submit a decline. */
class LotteryDeclinePreviewProvider : PreviewParameterProvider<LotteryDeclineUiState> {
    override val values =
        sequenceOf(
            LotteryDeclineUiState(canDecline = true),
            LotteryDeclineUiState(canDecline = true, isConfirming = true),
            LotteryDeclineUiState(isSubmitting = true),
            LotteryDeclineUiState(canDecline = true, failed = true),
            LotteryDeclineUiState(isAwaitingEntry = true),
        )
}

/** Reuses the saved reader fixtures with two independently identified, noncontiguous offers. */
private fun winningLotteryPreview(): LotteryDetailUiState.Content {
    val base =
        LotteryDetailPreviewProvider().values.filterIsInstance<LotteryDetailUiState.Content>().first {
            it.opportunity ==
                LotteryOpportunityRead.Unused
        }
    val existing = (base.offers as LotteryOffersState.Content).offers.single()
    val first = checkNotNull(existing.campsite)
    val second =
        first.copy(
            id = "wapiti",
            ownerId = "other-owner",
            details = first.details.copy(name = "Wapiti riverside group campsite beside the mountain trail"),
        )
    val dates = listOf(LocalDate.of(2027, 5, 18), LocalDate.of(2027, 5, 19), LocalDate.of(2027, 5, 22))
    return base.copy(
        offers =
            LotteryOffersState.Content(
                listOf(
                    LotteryOfferSummary(LotteryOffer(first.id, dates), first),
                    LotteryOfferSummary(LotteryOffer(second.id, dates), second),
                ),
            ),
    )
}

/** Checks reachable campsite choices and truthful source feedback at ordinary and enlarged scale.
 * @param state Passive saved facts or recovery outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun LotteryCampsiteChoicePreview(
    @PreviewParameter(LotteryCampsiteChoicePreviewProvider::class) state: LotteryDetailUiState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        LotteryCampsiteChoiceScreen(state, LotteryDeclineUiState(), {}, {}, {}, {}, { _, _ -> })
    }
}

/** Checks confirmation, progress and explicit recovery without any mutation.
 * @param decline Passive command state.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun LotteryDeclinePreview(
    @PreviewParameter(LotteryDeclinePreviewProvider::class) decline: LotteryDeclineUiState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        LotteryDetailScreen(winningLotteryPreview(), {
        }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, decline = decline, onChooseCampsite = {})
    }
}
