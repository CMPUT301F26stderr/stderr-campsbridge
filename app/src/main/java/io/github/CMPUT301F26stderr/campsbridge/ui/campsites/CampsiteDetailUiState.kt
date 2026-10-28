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

/** Read-only children share the saved detail parent and its presentation context.
 * @param routeKey Stable child identity, independent of localized wording.
 */
enum class CampsiteDetailSection(
    val routeKey: String,
) {
    OVERVIEW("overview"),
    ABOUT("about"),
    MAP("map"),
    AVAILABILITY("availability"),
    RULES("rules"),
    LOTTERIES("lotteries"),
}

/** Optional owner label; a failed or missing profile never hides listing facts. */
sealed interface CampsiteOwnerState {
    /** The selected owner's record has not emitted. */
    data object Loading : CampsiteOwnerState

    /** The owner record is absent, removed or inconsistent with the requested identity. */
    data object Unavailable : CampsiteOwnerState

    /** The owner source failed independently of the listing. */
    data object Failure : CampsiteOwnerState

    /** Confirmed display name from the current owner's account.
     * @param name Stored display name.
     */
    data class Ready(
        val name: String,
    ) : CampsiteOwnerState
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
