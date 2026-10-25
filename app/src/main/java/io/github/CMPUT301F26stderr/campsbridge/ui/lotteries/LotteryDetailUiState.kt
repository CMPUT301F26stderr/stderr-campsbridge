package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer

/** Own participation labels; an unfinished draw never exposes provisional winners or losers. */
enum class LotteryMembershipStatus {
    NOT_JOINED,
    WAITING,
    WITHDRAWN,
    RESULTS_PENDING,
    WON,
    LOST,
    DECLINED,
}

/** The one explicit membership mutation awaiting acknowledgement or observed confirmation. */
enum class LotteryMembershipAction { JOIN, LEAVE }

/** Membership-command feedback without raw repository or transport details. */
enum class LotteryMembershipActionError { FULL, CLOSED, FAILED }

/** Independent own-entry source; owner management does not query this lane. */
sealed interface LotteryMembershipRead {
    /** This presentation does not require entrant membership. */
    data object NotApplicable : LotteryMembershipRead

    /** The current account's membership has not emitted. */
    data object Loading : LotteryMembershipRead

    /** Own-entry observation failed; parent and offers remain readable. */
    data object Failure : LotteryMembershipRead

    /** A confirmed own-entry outcome, with provisional draw states hidden.
     * @param status Current displayable participation facts.
     */
    data class Content(
        val status: LotteryMembershipStatus,
    ) : LotteryMembershipRead
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
     * @param canJoin Current parent/window/own-entry facts permit joining or rejoining.
     * @param canLeave Current parent/window/own-entry facts permit withdrawal.
     * @param isConfirmingLeave Whether the explicit withdrawal confirmation is open.
     * @param membershipAction One pending write or its awaited observed confirmation.
     * @param membershipError Last membership-command failure, independent of read failures.
     */
    data class Content(
        val lottery: Lottery,
        val offers: LotteryOffersState = LotteryOffersState.Loading,
        val canContinue: Boolean = false,
        val lifecycle: LotteryLifecycle,
        val membership: LotteryMembershipRead = LotteryMembershipRead.NotApplicable,
        val opportunity: LotteryOpportunityRead = LotteryOpportunityRead.NotApplicable,
        val canJoin: Boolean = false,
        val canLeave: Boolean = false,
        val isConfirmingLeave: Boolean = false,
        val membershipAction: LotteryMembershipAction? = null,
        val membershipError: LotteryMembershipActionError? = null,
    ) : LotteryDetailUiState
}
