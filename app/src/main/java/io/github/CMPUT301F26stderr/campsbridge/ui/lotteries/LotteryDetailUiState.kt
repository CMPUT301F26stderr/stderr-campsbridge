package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer

/** Saved allocations keep source failure separate from a confirmed empty result. */
sealed interface LotteryOffersState {
    /** No confirmed allocation emission yet. */
    data object Loading : LotteryOffersState

    /** Allocation or campsite-label source failed; the saved parent remains readable. */
    data object Failure : LotteryOffersState

    /** Current allocations and optional labels from their actual referenced campsites.
     * @param offers Confirmed allocations; missing sites retain their original identity.
     */
    data class Content(
        val offers: List<LotteryOfferSummary>,
    ) : LotteryOffersState
}

/** Read-only allocation facts; labels never replace the authoritative campsite identity.
 * @param offer Actual saved allocation.
 * @param name Current campsite name, or null when that record is absent.
 * @param timeZone Current campsite zone, or null when that record is absent.
 */
data class LotteryOfferSummary(
    val offer: LotteryOffer,
    val name: String?,
    val timeZone: String?,
)

/** Saved owner overview; later participation actions extend this same record owner. */
sealed interface LotteryDetailUiState {
    /** The saved parent has not emitted yet. */
    data object Loading : LotteryDetailUiState

    /** Parent observation failed, requiring an explicit retry. */
    data object Failure : LotteryDetailUiState

    /** Missing or unrelated target. */
    data object Unavailable : LotteryDetailUiState

    /** Actual saved facts with independently loaded allocations.
     * @param lottery Current parent record.
     * @param offers Independent allocation source outcome.
     * @param canContinue Whether the current owner may reopen the unpublished active draft.
     */
    data class Content(
        val lottery: Lottery,
        val offers: LotteryOffersState = LotteryOffersState.Loading,
        val canContinue: Boolean = false,
    ) : LotteryDetailUiState
}
