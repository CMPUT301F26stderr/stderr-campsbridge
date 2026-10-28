package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Reads one actual saved identity without depending on current listing data.
 * @param accountId Current actor; entrant presentation accepts only its own record.
 * @param reservationId Saved identity carried by the caller.
 * @param context Explicit entrant or owner presentation.
 * @param bookings Complete reservation aggregate source.
 */
class ReservationDetailViewModel(
    private val accountId: String,
    private val reservationId: String,
    val context: ReservationContext,
    private val bookings: BookingRepository,
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

    /** Retries only a failed saved-record observation, never replaying a booking mutation. */
    fun retry() {
        if (!closed && state.value == ReservationDetailUiState.Failure) observeRecord()
    }

    /** Installs the sole parent-owned read before starting synchronous source callbacks. */
    private fun observeRecord() {
        val version = ++readVersion
        readJob?.cancel()
        mutableState.value = ReservationDetailUiState.Loading
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    bookings.observe(reservationId).collect { record ->
                        currentCoroutineContext().ensureActive()
                        if (closed || version != readVersion) return@collect
                        mutableState.value =
                            if (record == null || record.id != reservationId ||
                                (
                                    context == ReservationContext.ENTRANT &&
                                        record.entrantId != accountId
                                )
                            ) {
                                ReservationDetailUiState.Unavailable
                            } else {
                                ReservationDetailUiState.Content(record)
                            }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!closed &&
                        version == readVersion
                    ) {
                        mutableState.value = ReservationDetailUiState.Failure
                    }
                }
            }
        readJob = pending
        pending.start()
    }

    /** Invalidates late reads and stale retry callbacks when the saved parent leaves its graph. */
    override fun onCleared() {
        closed = true
        readVersion++
        readJob?.cancel()
        super.onCleared()
    }
}
