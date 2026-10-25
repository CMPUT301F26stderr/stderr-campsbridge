package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState

/** Owner's active inventory categories; removal history is excluded from both. */
enum class CampsiteListTab { PUBLISHED, DRAFTS }

/** Source outcomes remain independent from the selected inventory tab. */
sealed interface CampsiteListUiState {
    /** The owner-scoped source has not produced a result yet. */
    data object Loading : CampsiteListUiState

    /** The source failed; an empty list has not been established. */
    data object Failure : CampsiteListUiState

    /** Confirmed rows, with explicit pending ordering and independently resolved images.
     * @param campsites Rows for the selected tab.
     * @param orderingPending Whether the reserved ordering helper is absent.
     * @param photos Transient image reads for currently visible records.
     */
    data class Content(
        val campsites: List<Campsite>,
        val orderingPending: Boolean,
        val photos: Map<String, ImageSlotState> = emptyMap(),
    ) : CampsiteListUiState
}
