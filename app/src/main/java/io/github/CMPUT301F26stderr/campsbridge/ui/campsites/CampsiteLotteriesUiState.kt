package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipRead
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipUiState

/** Independent lottery association outcome; unknown data never implies no active lottery. */
sealed interface CampsiteLotteriesUiState {
    /** This parent/context does not expose entrant participation. */
    data object Unavailable : CampsiteLotteriesUiState

    /** The scoped association query has not emitted. */
    data object Loading : CampsiteLotteriesUiState

    /** Association failed independently of the saved campsite. */
    data object Failure : CampsiteLotteriesUiState

    /** Confirmed open choices with independent own-entry and command feedback.
     * @param choices Current open lotteries, ordered by closing instant and identity.
     * @param membership Sole choice's own-entry outcome; not required for several/zero choices.
     * @param participation Shared sole-choice command presentation.
     */
    data class Content(
        val choices: List<CampsiteLotteryChoice>,
        val membership: LotteryMembershipRead = LotteryMembershipRead.NotApplicable,
        val participation: LotteryMembershipUiState = LotteryMembershipUiState(),
    ) : CampsiteLotteriesUiState
}

/** Saved choice and its separate visible-row allocation outcome.
 * @param lottery Actual associated lottery.
 * @param offer This campsite's offered-night source, observed only in the choices child.
 */
data class CampsiteLotteryChoice(
    val lottery: Lottery,
    val offer: CampsiteLotteryOfferRead = CampsiteLotteryOfferRead.Loading,
)

/** Scoped offered-night facts; a missing allocation differs from an unread or failed source. */
sealed interface CampsiteLotteryOfferRead {
    /** The visible row's source has not emitted. */
    data object Loading : CampsiteLotteryOfferRead

    /** The actual source confirms that this campsite is no longer allocated. */
    data object Unavailable : CampsiteLotteryOfferRead

    /** This row's allocation failed independently of the choices list. */
    data object Failure : CampsiteLotteryOfferRead

    /** Actual allocation for the parent campsite.
     * @param offer Confirmed matching allocation.
     */
    data class Ready(
        val offer: LotteryOffer,
    ) : CampsiteLotteryOfferRead
}
