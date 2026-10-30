package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.ViewModel
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock

/** Defines the saved-reader interface while its contract tests establish missing behavior.
 * @param accountId Current actor identity.
 * @param reservationId Saved identity to observe.
 * @param context Actual entrant or owner presentation.
 * @param bookings Saved aggregate repository.
 * @param clock Host clock retained by the reader's constructor contract.
 */
@Suppress("UNUSED_PARAMETER")
class ReservationDetailViewModel(
    accountId: String,
    reservationId: String,
    val context: ReservationContext,
    bookings: BookingRepository,
    clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val mutableState =
        MutableStateFlow<ReservationDetailUiState>(ReservationDetailUiState.Loading)
    val state = mutableState.asStateFlow()

    /** Pending reader retry; the contract suite must fail until observation is implemented. */
    fun retry() =
        Unit
}
