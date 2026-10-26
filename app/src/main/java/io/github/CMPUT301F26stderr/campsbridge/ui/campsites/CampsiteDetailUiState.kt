package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite

/** Selects presentation and graph identity; the caller supplies actual account capabilities.
 * @param routeKey Stable context token in navigation.
 */
enum class CampsiteDetailContext(
    val routeKey: String,
) {
    MANAGE("manage"),
    PREVIEW("preview"),
    VIEW("view"),
    INSPECT("inspect"),
}

/** Saved listing reads never substitute preview data for missing or failed records. */
sealed interface CampsiteDetailUiState {
    /** Listing read has not completed. */
    data object Loading : CampsiteDetailUiState

    /** A saved listing is available.
     * @param campsite Actual observed record.
     */
    data class Content(
        val campsite: Campsite,
    ) : CampsiteDetailUiState

    /** Listing is absent, removed or incompatible with this presentation. */
    data object Unavailable : CampsiteDetailUiState

    /** Listing source failed and can be retried. */
    data object Failure : CampsiteDetailUiState
}
