package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

/** Scoped command owner; its parent supplies observations and owns every query and deadline.
 * @param accountId Fixed entrant identity for explicit operations.
 * @param lotteryId Fixed actual saved target.
 * @param lotteries Existing membership operations.
 * @param clock Current eligibility time, shared with the parent.
 * @param scope Parent's serialized coroutine scope.
 * @param onChanged Publishes changed command feedback through the owning ViewModel.
 */
internal class LotteryMembershipController(
    private val accountId: String,
    private val lotteryId: String,
    private val lotteries: LotteryRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit = {},
) : AutoCloseable {
    private val mutableState = MutableStateFlow(LotteryMembershipUiState())
    val state = mutableState.asStateFlow()
    private var parent: Lottery? = null
    private var ownEntry: LotteryEntry? = null
    private var known = false
    private var closed = false
    private var job: Job? = null
    private var version = 0L
    private var action: LotteryMembershipAction? = null
    private var acknowledged = false
    private var observed = false
    private var confirming = false
    private var error: LotteryMembershipActionError? = null

    /** Supplies scoped facts without starting any observation.
     * @param lottery Current actionable presentation parent, or null for retirement.
     * @param entry Actual own entry, including null for confirmed absence.
     * @param membershipKnown Whether the source confirmed this own-entry result.
     */
    fun updateFacts(
        lottery: Lottery?,
        entry: LotteryEntry?,
        membershipKnown: Boolean,
    ) {
        if (closed) return
        parent = lottery?.takeIf { it.id == lotteryId }
        val matching =
            entry == null || (entry.lotteryId == lotteryId && entry.accountId == accountId)
        ownEntry = entry.takeIf { matching }
        known = membershipKnown && matching
        if (parent == null) retireCommand()
        publish()
    }

    /** Requests one eligible join/rejoin. */
    fun join() {
        if (closed) return
        publish()
        if (action == null && !confirming && canJoin()) start(LotteryMembershipAction.JOIN)
    }

    /** Opens an eligible withdrawal confirmation. */
    fun requestLeave() {
        if (closed) return
        publish()
        if (action == null && canLeave()) {
            confirming = true
            error = null
            publish()
        }
    }

    /** Dismisses confirmation without writing. */
    fun dismissLeave() {
        if (closed || action != null) return
        confirming = false
        publish()
    }

    /** Rechecks the window and submits one confirmed withdrawal. */
    fun confirmLeave() {
        if (closed) return
        publish()
        if (confirming && action == null && canLeave()) start(LotteryMembershipAction.LEAVE)
    }

    /** Releases this command owner and rejects late work. */
    override fun close() {
        if (closed) return
        closed = true
        retireCommand()
        mutableState.value = LotteryMembershipUiState()
    }

    /** Checks only current supplied facts and the shared registration window. */
    private fun ready(): Boolean =
        known && parent?.registrationIsOpen(clock.instant()) == true

    /** Allows confirmed absence or a withdrawn entry to rejoin. */
    private fun canJoin(): Boolean =
        ready() && (ownEntry == null || ownEntry?.state == EntryState.WITHDRAWN)

    /** Allows only confirmed waiting membership to withdraw. */
    private fun canLeave(): Boolean =
        ready() && ownEntry?.state == EntryState.WAITING

    /** Claims the fixed tuple before suspension and awaits acknowledgement plus observed evidence.
     * @param requested Explicit eligible membership operation.
     */
    private fun start(requested: LotteryMembershipAction) {
        val currentVersion = ++version
        action = requested
        acknowledged = false
        observed = false
        error = null
        publish()
        val pending =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    when (requested) {
                        LotteryMembershipAction.JOIN -> lotteries.join(lotteryId, accountId)
                        LotteryMembershipAction.LEAVE -> lotteries.withdraw(lotteryId, accountId)
                    }
                    currentCoroutineContext().ensureActive()
                    if (currentVersion != version) return@launch
                    acknowledged = true
                    publish()
                } catch (cancelled: CancellationException) {
                    if (currentVersion == version) {
                        clearPending()
                        publish()
                    }
                    throw cancelled
                } catch (failure: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (currentVersion == version) {
                        clearPending()
                        error =
                            when ((failure as? PersistenceFailure)?.reason) {
                                PersistenceFailure.Reason.CAPACITY_REACHED -> {
                                    LotteryMembershipActionError.FULL
                                }

                                PersistenceFailure.Reason.WINDOW_CLOSED -> {
                                    LotteryMembershipActionError.CLOSED
                                }

                                else -> {
                                    LotteryMembershipActionError.FAILED
                                }
                            }
                        publish()
                    }
                } finally {
                    if (currentVersion == version) job = null
                }
            }
        job = pending
        pending.start()
    }

    /** Projects changed feedback only, keeping parent callbacks free of duplicate refresh loops. */
    private fun publish() {
        if (closed) return
        settle()
        // Keep source recovery reachable while retaining the separate pending-command guard.
        if (!known || (action == null && !canLeave())) confirming = false
        val next = LotteryMembershipUiState(canJoin(), canLeave(), confirming, action, error)
        if (next != mutableState.value) {
            mutableState.value = next
            onChanged()
        }
    }

    /** Retains a matching observed effect across later source changes until acknowledgement. */
    private fun settle() {
        val pending = action ?: return
        if (known) {
            val matched =
                when (pending) {
                    LotteryMembershipAction.JOIN -> {
                        ownEntry != null &&
                            ownEntry?.state != EntryState.WITHDRAWN
                    }

                    LotteryMembershipAction.LEAVE -> {
                        ownEntry?.state == EntryState.WITHDRAWN
                    }
                }
            if (matched) observed = true
        }
        if (acknowledged && observed) clearPending()
    }

    /** Releases transient feedback after confirmation, failure or cancellation. */
    private fun clearPending() {
        action = null
        acknowledged = false
        observed = false
        confirming = false
        error = null
    }

    /** Invalidates old results before cancelling their call and clearing command presentation. */
    private fun retireCommand() {
        version++
        job?.cancel()
        job = null
        clearPending()
    }
}
