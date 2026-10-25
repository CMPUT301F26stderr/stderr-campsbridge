package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery

/** Explicit query/presentation scope; administration has its own read-only browser. */
enum class LotteryListContext { OWNER, ENTRANT }

/** Derived list categories; these are never stored on lottery or membership documents. */
enum class LotteryListCategory { PUBLISHED, DRAFTS, ACTION_NEEDED, WAITING, HISTORY }

/** A resolved row with current facts and an optional already-consumed opportunity.
 * @param lottery Current saved parent.
 * @param lifecycle Current phase from the injected clock.
 * @param entryState Displayable own membership; unfinished winner/loser outcomes are absent.
 * @param reservationId Consumed opportunity's saved reservation, including cancelled stays.
 */
data class LotteryListItem(
    val lottery: Lottery,
    val lifecycle: LotteryLifecycle,
    val entryState: EntryState? = null,
    val reservationId: String? = null,
)

/** A linked source cannot be silently omitted or mistaken for a confirmed empty result. */
enum class LotteryReferenceIssue { LOADING, MISSING, FAILURE }

/** Unresolved referenced record or campsite allocation, retained across category selection.
 * @param lotteryId Original target identity.
 * @param issue Current loading, confirmed absence or failed-source outcome.
 */
data class LotteryListReference(
    val lotteryId: String,
    val issue: LotteryReferenceIssue,
)

/** Primary list reads, independently of category/scroll state. */
sealed interface LotteryListUiState {
    /** Required scoped sources have not all emitted. */
    data object Loading : LotteryListUiState

    /** An account-wide required source failed; retry cannot claim an empty list. */
    data object Failure : LotteryListUiState

    /** Confirmed rows plus unresolved references whose category is still unknown.
     * @param items Resolved rows in the selected category.
     * @param unresolved References retained visibly until their source resolves.
     */
    data class Content(
        val items: List<LotteryListItem>,
        val unresolved: List<LotteryListReference> = emptyList(),
    ) : LotteryListUiState
}
