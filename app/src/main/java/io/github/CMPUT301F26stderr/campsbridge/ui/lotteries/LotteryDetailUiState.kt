package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer

/** Saved reader children share one context-qualified parent and its current source outcomes.
 * @param routeKey Stable navigation token, independent of localized labels.
 */
enum class LotteryDetailSection(
    val routeKey: String,
) {
    OVERVIEW("overview"),
    SCHEDULE("schedule"),
    CAMPSITES("campsites"),
}

/** Server-confirmed consuming reservation and its subsequent aggregate observation. */
sealed interface LotteryOpportunityRead {
    /** No completed own winning opportunity requires this read. */
    data object NotApplicable : LotteryOpportunityRead

    /** The current server read has not confirmed either a record or absence. */
    data object Loading : LotteryOpportunityRead

    /** Unknown consumption; a failed read must never enable booking. */
    data object Failure : LotteryOpportunityRead

    /** The server confirmed no consuming reservation for this opportunity. */
    data object Unused : LotteryOpportunityRead

    /** Saved history, including nested payment, cancellation and refund.
     * @param reservation Confirmed consuming record; cancellation does not release the win.
     */
    data class Reserved(
        val reservation: Reservation,
    ) : LotteryOpportunityRead

    /** A previously found saved record is now unavailable, rather than an unused opportunity.
     * @param reservationId Known consuming identity whose live document is absent.
     */
    data class Unavailable(
        val reservationId: String,
    ) : LotteryOpportunityRead
}

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

/** Read-only allocation and its already-read current campsite; the offer owns its identity.
 * @param offer Actual saved allocation.
 * @param campsite Current referenced record, or null when it is absent.
 */
data class LotteryOfferSummary(
    val offer: LotteryOffer,
    val campsite: Campsite?,
)

/** Saved owner/entrant overview with independent source outcomes. */
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
     * @param lifecycle Current display phase evaluated by the owner with its injected clock.
     * @param canContinue Whether the current owner may reopen the unpublished active draft.
     * @param membership Independent own participation, hidden for owner management.
     * @param opportunity Server-confirmed consumption and live saved history.
     * @param participation Shared entrant command feedback; independent of source reads.
     * @param canDraw Current owner/lifecycle facts permit the manual fallback.
     * @param isDrawing An actual manual repository call is pending acknowledgement.
     * @param drawFailed The last manual call failed while the parent remains incomplete.
     */
    data class Content(
        val lottery: Lottery,
        val offers: LotteryOffersState = LotteryOffersState.Loading,
        val canContinue: Boolean = false,
        val lifecycle: LotteryLifecycle,
        val membership: LotteryMembershipRead = LotteryMembershipRead.NotApplicable,
        val opportunity: LotteryOpportunityRead = LotteryOpportunityRead.NotApplicable,
        val participation: LotteryMembershipUiState = LotteryMembershipUiState(),
        val canDraw: Boolean = false,
        val isDrawing: Boolean = false,
        val drawFailed: Boolean = false,
    ) : LotteryDetailUiState
}
