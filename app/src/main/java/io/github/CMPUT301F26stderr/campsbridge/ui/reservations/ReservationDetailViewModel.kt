/*
 * Owns one saved record and its explicit actions across overview, payment and terms.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.ui.common.time.awaitDeadline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

/** Owns one saved record and its explicit actions across overview, payment and terms.
 * @param accountId Current actor; entrants may read only their own history.
 * @param reservationId Exact saved identity supplied by the caller.
 * @param context Caller presentation; repository authorization remains authoritative.
 * @param bookings Complete saved aggregate and coordinated cancellation source.
 * @param clock Current booked-local deadline source.
 * @param savedState Primitive recovery marker and submitted reason.
 */
class ReservationDetailViewModel(
    private val accountId: String,
    private val reservationId: String,
    val context: ReservationContext,
    private val bookings: BookingRepository,
    private val clock: Clock = Clock.systemUTC(),
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReservationDetailUiState>(ReservationDetailUiState.Loading)
    val state = mutableState.asStateFlow()
    private var readJob: Job? = null
    private var deadlineJob: Job? = null
    private var readVersion = 0L
    private var closed = false
    private var confirmedCancellation: Reservation? = null
    private val actions =
        ReservationCancellationController(
            accountId,
            reservationId,
            context,
            bookings,
            clock,
            savedState,
            viewModelScope,
            currentRecord = { (state.value as? ReservationDetailUiState.Content)?.reservation },
            onConfirmed = ::confirmRecord,
        )
    val cancellation = actions.state

    init {
        observeRecord()
        actions.restore()
    }

    /** Restarts only a failed saved source, keeping the same graph and target. */
    fun retry() {
        if (!closed && state.value == ReservationDetailUiState.Failure) observeRecord()
    }

    /** Opens a confirmation without writing. */
    fun requestCancel() =
        actions.request()

    /** Dismisses an idle confirmation without changing the saved stay. */
    fun dismissCancel() =
        actions.dismiss()

    /** Preserves optional user input until confirmation or dismissal.
     * @param reason Exact editable explanation.
     */
    fun editReason(reason: String) =
        actions.edit(reason)

    /** Sends one guarded cancellation or rejection through the existing atomic operation. */
    fun confirmCancel() =
        actions.confirm()

    /** Checks an uncertain server result without replaying the command. */
    fun retryCancellationRead() =
        actions.retryRead()

    /** Whether navigation can leave this saved parent without an unresolved result. */
    fun canLeave(): Boolean =
        actions.canLeave()

    /** Rechecks booked-local eligibility on foregrounding and owns the next midnight wakeup. */
    fun refreshTime() {
        if (closed) return
        actions.refresh()
        deadlineJob?.cancel()
        deadlineJob = null
        val record = (state.value as? ReservationDetailUiState.Content)?.reservation ?: return
        if (record.cancellation != null) return
        val zone = runCatching { ZoneId.of(record.bookedCampsite.timeZone) }.getOrNull() ?: return
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        if (today >= record.checkOutDate) return
        val next = today.plusDays(1).atStartOfDay(zone).toInstant()
        deadlineJob =
            viewModelScope.launch {
                awaitDeadline(clock, next)
                refreshTime()
            }
    }

    /** Validates saved identity before exposing history or allowing an action.
     * @param record Complete observation, or confirmed absence.
     */
    private fun matches(record: Reservation?): Boolean =
        record != null && record.id == reservationId &&
            (context != ReservationContext.ENTRANT || record.entrantId == accountId)

    /** Retains server-confirmed cancellation/refund facts against an older cached observation.
     * @param record Matching complete observation from the existing reader.
     */
    private fun publishRecord(record: Reservation?) {
        val confirmed = confirmedCancellation
        mutableState.value =
            if (matches(record)) {
                val incoming = requireNotNull(record)
                val saved =
                    if (confirmed == null) {
                        incoming
                    } else {
                        incoming.copy(
                            cancellation = incoming.cancellation ?: confirmed.cancellation,
                            payment = if (incoming.payment.refundedAt == null) confirmed.payment else incoming.payment,
                        )
                    }
                ReservationDetailUiState.Content(saved)
            } else if (confirmed != null) {
                ReservationDetailUiState.Content(confirmed)
            } else {
                ReservationDetailUiState.Unavailable
            }
        refreshTime()
    }

    /** Shares the server's complete result across the saved children without inventing refund facts.
     * @param record Identity-checked authoritative recovery result.
     */
    private fun confirmRecord(record: Reservation) {
        if (record.cancellation != null) confirmedCancellation = record
        publishRecord(record)
    }

    /** Installs ownership before a repository can synchronously emit or fail. */
    private fun observeRecord() {
        val version = ++readVersion
        readJob?.cancel()
        mutableState.value = ReservationDetailUiState.Loading
        actions.refresh()
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    bookings.observe(reservationId).collect { record ->
                        currentCoroutineContext().ensureActive()
                        if (!closed && version == readVersion) publishRecord(record)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!closed && version == readVersion) {
                        mutableState.value = confirmedCancellation?.let(ReservationDetailUiState::Content)
                            ?: ReservationDetailUiState.Failure
                        refreshTime()
                    }
                }
            }
        readJob = pending
        pending.start()
    }

    /** Retires all readers, commands and deadlines when the saved navigation parent is removed. */
    override fun onCleared() {
        closed = true
        readVersion++
        actions.close()
        readJob?.cancel()
        deadlineJob?.cancel()
    }
}
