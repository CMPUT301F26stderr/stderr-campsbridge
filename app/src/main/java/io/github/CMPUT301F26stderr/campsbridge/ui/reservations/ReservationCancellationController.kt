/*
 * Owns one explicit cancellation and its read-only recovery within the saved parent.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.SavedStateHandle
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.canCancelBeforeCheckout
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
import java.time.LocalDate
import java.time.ZoneId

/** Owns one explicit cancellation and its read-only recovery within the saved parent.
 * @param accountId Actual actor supplied by the host.
 * @param reservationId Exact saved target.
 * @param context Entrant cancellation or owner rejection.
 * @param bookings Existing atomic write and server read operations.
 * @param clock Current booked-local date source.
 * @param savedState Primitive uncertainty and submitted input across recreation.
 * @param scope Saved parent's coroutine lifetime.
 * @param currentRecord Current matching readable aggregate, never a separate observer.
 * @param onConfirmed Publishes the complete server result to every saved child.
 */
internal class ReservationCancellationController(
    private val accountId: String,
    private val reservationId: String,
    private val context: ReservationContext,
    private val bookings: BookingRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val currentRecord: () -> Reservation?,
    private val onConfirmed: (Reservation) -> Unit,
) {
    private val mutableState = MutableStateFlow(CancellationUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var closed = false

    /** Restored uncertainty is reconciled by identity without replaying a write. */
    fun restore() {
        if (savedState.get<Boolean>(RECOVERY_KEY) == true) {
            mutableState.value =
                CancellationUiState(
                    prompt = CancellationPrompt(savedState.get<String>(REASON_KEY).orEmpty()),
                    phase = CancellationPhase.UNKNOWN,
                )
            retryRead()
        }
    }

    /** Reprojects eligibility without allowing source updates to unlock a held command. */
    fun refresh() {
        if (closed) return
        val state = mutableState.value
        val eligible = state.phase == CancellationPhase.IDLE && isEligible()
        mutableState.value =
            state.copy(
                canRequest = eligible,
                prompt =
                    if (state.phase == CancellationPhase.IDLE && currentRecord() != null && !eligible) {
                        null
                    } else {
                        state.prompt
                    },
            )
    }

    /** Opens one idle prompt; inspecting it performs no repository operation. */
    fun request() {
        refresh()
        if (closed || !state.value.canRequest || state.value.prompt != null) return
        mutableState.value = CancellationUiState(canRequest = true, prompt = CancellationPrompt(""))
    }

    /** Drops idle input without changing the saved reservation. */
    fun dismiss() {
        if (closed || state.value.phase != CancellationPhase.IDLE) return
        mutableState.value = CancellationUiState()
        refresh()
    }

    /** Changes only idle input, retaining the exact text until submission.
     * @param reason Optional user explanation.
     */
    fun edit(reason: String) {
        val state = state.value
        if (closed || state.phase != CancellationPhase.IDLE || state.prompt == null) return
        mutableState.value = state.copy(prompt = CancellationPrompt(reason), failure = null, notCancelled = false)
    }

    /** Freezes actor and input behind a synchronous guard before entering repository code. */
    fun confirm() {
        refresh()
        val state = state.value
        val prompt = state.prompt ?: return
        if (closed || !state.canRequest || state.phase != CancellationPhase.IDLE) return
        savedState[RECOVERY_KEY] = true
        savedState[REASON_KEY] = prompt.reason
        mutableState.value =
            state.copy(
                canRequest = false,
                phase = CancellationPhase.CANCELLING,
                failure = null,
                notCancelled = false,
            )
        val request =
            CancellationRequest(
                reservationId,
                accountId,
                if (context == ReservationContext.OWNER) Role.OWNER else Role.ENTRANT,
                prompt.reason.trim().takeIf { it.isNotEmpty() },
            )
        launchOperation {
            try {
                bookings.cancel(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rejected: PersistenceFailure) {
                currentCoroutineContext().ensureActive()
                clearRecovery()
                mutableState.value =
                    mutableState.value.copy(
                        phase = CancellationPhase.IDLE,
                        failure = rejected.reason,
                    )
                refresh()
                return@launchOperation
            } catch (_: Exception) {
                // A transport failure cannot establish whether the atomic operation committed.
            }
            currentCoroutineContext().ensureActive()
            mutableState.value = mutableState.value.copy(phase = CancellationPhase.RECOVERING)
            reconcile()
        }
    }

    /** Explicit retry reads only the uncertain saved result, never the cancellation operation. */
    fun retryRead() {
        if (closed || state.value.phase != CancellationPhase.UNKNOWN || job?.isActive == true) return
        mutableState.value = state.value.copy(canRequest = false, phase = CancellationPhase.RECOVERING)
        launchOperation { reconcile() }
    }

    /** Navigation stays blocked while this parent owns an unresolved command. */
    fun canLeave(): Boolean =
        state.value.phase == CancellationPhase.IDLE

    /** Checks the existing date predicate with saved-zone conversion and entrant identity. */
    private fun isEligible(): Boolean {
        val record = currentRecord() ?: return false
        if (!matches(record)) return false
        val today =
            runCatching { LocalDate.now(clock.withZone(ZoneId.of(record.bookedCampsite.timeZone))) }
                .getOrNull() ?: return false
        return canCancelBeforeCheckout(record, today)
    }

    /** Rejects unrelated history even when a recovery source returns a complete record.
     * @param record Server or observation result being checked.
     */
    private fun matches(record: Reservation): Boolean =
        record.id == reservationId &&
            (context != ReservationContext.ENTRANT || record.entrantId == accountId)

    /** Publishes only a matching server aggregate; absent or failed reads remain unresolved. */
    private suspend fun reconcile() {
        val record =
            try {
                bookings.get(reservationId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        currentCoroutineContext().ensureActive()
        if (record == null || !matches(record)) {
            mutableState.value = state.value.copy(phase = CancellationPhase.UNKNOWN)
            return
        }
        onConfirmed(record)
        clearRecovery()
        mutableState.value =
            state.value.copy(
                phase = CancellationPhase.IDLE,
                prompt = if (record.cancellation != null) null else state.value.prompt,
                failure = null,
                notCancelled = record.cancellation == null,
            )
        refresh()
    }

    /** Installs the job before starting work and ignores callbacks after parent retirement.
     * @param operation One write/reconciliation sequence or a read-only recovery.
     */
    private fun launchOperation(operation: suspend () -> Unit) {
        val version = ++generation
        val pending =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    operation()
                } catch (cancelled: CancellationException) {
                    if (!closed && generation == version) {
                        mutableState.value = state.value.copy(canRequest = false, phase = CancellationPhase.UNKNOWN)
                    }
                    throw cancelled
                }
            }
        job = pending
        pending.start()
    }

    /** Removes recovery markers only after known rejection or a confirmed server outcome. */
    private fun clearRecovery() {
        savedState.remove<Boolean>(RECOVERY_KEY)
        savedState.remove<String>(REASON_KEY)
    }

    /** Retires callbacks before cancelling any held write or server read. */
    fun close() {
        closed = true
        generation++
        job?.cancel()
    }

    private companion object {
        const val RECOVERY_KEY = "reservationCancellationRecovery"
        const val REASON_KEY = "reservationCancellationReason"
    }
}
