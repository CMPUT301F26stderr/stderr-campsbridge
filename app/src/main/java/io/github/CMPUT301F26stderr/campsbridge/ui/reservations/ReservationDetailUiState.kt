package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation

/** Read outcome shared by the saved overview, payment, and accepted terms pages. */
sealed interface ReservationDetailUiState {
    /** No result has arrived for the requested saved identity. */
    data object Loading : ReservationDetailUiState

    /** The source failed; Retry can restart the same read. */
    data object Failure : ReservationDetailUiState

    /** The source confirmed an absent, mismatched, or unrelated entrant record. */
    data object Unavailable : ReservationDetailUiState

    /** Complete saved history, including payment and accepted terms.
     * @param reservation Authoritative saved aggregate without current-listing substitutions.
     */
    data class Content(
        val reservation: Reservation,
    ) : ReservationDetailUiState
}
