package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

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

/** Shared command presentation for lottery and campsite entry points.
 * @param canJoin Current confirmed facts permit joining or rejoining.
 * @param canLeave Current confirmed facts permit withdrawal.
 * @param isConfirmingLeave Explicit withdrawal confirmation is open.
 * @param action One pending command or its awaited observed confirmation.
 * @param error Last command failure, independent of source outcomes.
 */
data class LotteryMembershipUiState(
    val canJoin: Boolean = false,
    val canLeave: Boolean = false,
    val isConfirmingLeave: Boolean = false,
    val action: LotteryMembershipAction? = null,
    val error: LotteryMembershipActionError? = null,
)
