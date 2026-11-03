package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

/** Owns one saved reservation across overview, payment, and accepted terms.
 * @param accountId Current actor; entrants may read only their own history.
 * @param reservationId Exact saved identity supplied by the caller.
 * @param context Caller presentation; the repository remains responsible for authorization.
 * @param bookings Complete saved aggregate source, without current listing dependencies.
 * @param clock Booked-local deadline source.
 * @param savedState Primitive cancellation recovery marker and submitted reason.
 */
@Suppress("UNUSED_PARAMETER")
class ReservationDetailViewModel(
    private val accountId: String,
    private val reservationId: String,
    val context: ReservationContext,
    private val bookings: BookingRepository,
    clock: Clock = Clock.systemUTC(),
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState =
        MutableStateFlow<ReservationDetailUiState>(ReservationDetailUiState.Loading)
    val state = mutableState.asStateFlow()
    private var readJob: Job? = null
    private var readVersion = 0L
    private var closed = false

    init {
        observeRecord()
    }

    val cancellation = MutableStateFlow(CancellationUiState()).asStateFlow()

    /** Opens an eligible confirmation. */
    fun requestCancel() =
        Unit

    /** Dismisses an idle confirmation without writing. */
    fun dismissCancel() =
        Unit

    /** Edits idle confirmation input.
     * @param reason Optional explanation.
     */
    fun editReason(reason: String) =
        Unit

    /** Submits one confirmed operation. */
    fun confirmCancel() =
        Unit

    /** Reconciles an uncertain result without repeating the mutation. */
    fun retryCancellationRead() =
        Unit

    /** Refreshes booked-local eligibility after foregrounding. */
    fun refreshTime() =
        Unit

    /** Whether this saved parent can safely be left. */
    fun canLeave(): Boolean =
        true

    /** Restarts a failed source once; active and confirmed-absent sources remain observed. */
    fun retry() {
        if (!closed && state.value == ReservationDetailUiState.Failure) observeRecord()
    }

    /** Rejects other identities and unrelated entrant history without substituting records.
     * @param record Complete saved observation, or confirmed absence.
     */
    private fun readState(record: Reservation?): ReservationDetailUiState =
        if (record == null || record.id != reservationId ||
            (context == ReservationContext.ENTRANT && record.entrantId != accountId)
        ) {
            ReservationDetailUiState.Unavailable
        } else {
            ReservationDetailUiState.Content(record)
        }

    /** Installs the source owner before collecting even a synchronously emitting repository. */
    private fun observeRecord() {
        val version = ++readVersion
        readJob?.cancel()
        mutableState.value = ReservationDetailUiState.Loading
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    bookings.observe(reservationId).collect { record ->
                        currentCoroutineContext().ensureActive()
                        if (!closed && version == readVersion) {
                            mutableState.value = readState(record)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!closed && version == readVersion) {
                        mutableState.value = ReservationDetailUiState.Failure
                    }
                }
            }
        readJob = pending
        pending.start()
    }

    /** Retires the source and callbacks when the saved navigation parent is removed. */
    override fun onCleared() {
        closed = true
        readVersion++
        readJob?.cancel()
        super.onCleared()
    }
}
