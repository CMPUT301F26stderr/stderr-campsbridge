package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation

/** Presentation chosen by the actual caller; repositories own mutation authorization. */
enum class ReservationContext { ENTRANT, OWNER }

/** One saved aggregate shared by overview, payment and accepted terms. */
sealed interface ReservationDetailUiState {
    /** The saved identity has not produced a confirmed observation. */
    data object Loading : ReservationDetailUiState

    /** Transport failure, distinct from confirmed absence. */
    data object Failure : ReservationDetailUiState

    /** Missing, mismatched or unrelated entrant record. */
    data object Unavailable : ReservationDetailUiState

    /** Complete booked facts; current listings cannot replace any nested history.
     * @param reservation Observed snapshot, payment and cancellation together.
     */
    data class Content(
        val reservation: Reservation,
    ) : ReservationDetailUiState
}
