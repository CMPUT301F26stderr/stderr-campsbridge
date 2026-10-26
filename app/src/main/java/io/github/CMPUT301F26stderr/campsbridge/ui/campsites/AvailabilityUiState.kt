package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import java.time.LocalDate

/** Saved-parent resolution and local offer-editor state; no writes happen on selection. */
sealed interface AvailabilityUiState {
    /** The actual saved parent is being read before any calendar is opened. */
    data object Loading : AvailabilityUiState

    /** A failed parent read may be retried without inventing an empty listing. */
    data object Failure : AvailabilityUiState

    /** A missing, removed or foreign-owned parent cannot be edited. */
    data object Unavailable : AvailabilityUiState

    /** Confirmed parent plus shared inventory work and a separate working selection.
     * @param campsite Actual saved listing, used for title and local zone.
     * @param availability Shared controller's range and requested-write state.
     * @param selectedDates Working dates, before Offer/Withdraw is chosen.
     * @param saved Whether the last explicit Save completed successfully.
     */
    data class Content(
        val campsite: Campsite,
        val availability: CampsiteAvailabilityState,
        val selectedDates: Set<LocalDate> = emptySet(),
        val saved: Boolean = false,
    ) : AvailabilityUiState
}
