package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory

/** Supplemental label; absence/failure cannot remove a saved reservation. */
sealed interface ReservationEntrantName {
    /** Referenced profile has not completed its first read. */
    data object Loading : ReservationEntrantName

    /** Missing, removed or mismatched profile retains its canonical entrant identity. */
    data object Unavailable : ReservationEntrantName

    /** Referenced profile failed and can be retried independently of the reservation source. */
    data object Failure : ReservationEntrantName

    /** Confirmed profile display name.
     * @param name Saved name for the exact referenced entrant identity.
     */
    data class Available(
        val name: String,
    ) : ReservationEntrantName
}

/** Saved aggregate plus presentation results; booked facts retain one authoritative owner.
 * @param reservation Complete stored stay/payment/cancellation.
 * @param category Supplied active category for the current booked-local date.
 * @param name Owner-only supplemental entrant label; null for entrant presentation.
 */
data class ReservationListItem(
    val reservation: Reservation,
    val category: ReservationCategory,
    val name: ReservationEntrantName? = null,
)

/** Owner filter option derived from the scoped saved aggregates, without live listing reads.
 * @param id Saved campsite identity.
 * @param name Most recently booked name for this identity in the current source.
 */
data class ReservationCampsiteChoice(
    val id: String,
    val name: String,
)

/** Active-list source outcome; the small selected filter remains independent of this read. */
sealed interface ReservationListUiState {
    /** The scoped aggregate has not emitted a confirmed result. */
    data object Loading : ReservationListUiState

    /** Required aggregate failed; partial source results cannot be claimed complete. */
    data object Failure : ReservationListUiState

    /** Confirmed saved source projected through available category/order dependencies.
     * @param items Active rows, with no fallback category implementation.
     * @param campsites Owner choices from the full scoped aggregate, including retained history.
     * @param classificationPending Reserved F17 is absent; items cannot claim active membership.
     * @param orderingPending Reserved F18 is absent; supplied/source order is shown explicitly.
     */
    data class Content(
        val items: List<ReservationListItem>,
        val campsites: List<ReservationCampsiteChoice> = emptyList(),
        val classificationPending: Boolean = false,
        val orderingPending: Boolean = false,
    ) : ReservationListUiState
}
