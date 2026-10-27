package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.RatingState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState

/** Editable criteria remain available during every result outcome.
 * @param queryText Raw toolbar text.
 * @param submittedQuery Last explicitly submitted text.
 * @param filters Applied criteria.
 * @param filterDraft Optional working editor, independent of applied filters.
 * @param filterErrors Feedback for a failed Apply.
 * @param result Required listing/calendar outcome.
 */
data class ExploreUiState(
    val queryText: String = "",
    val submittedQuery: String = "",
    val filters: DiscoveryFilters = DiscoveryFilters(),
    val filterDraft: FilterDraft? = null,
    val filterErrors: FilterErrors = FilterErrors(),
    val result: ExploreResult = ExploreResult.Loading,
)

/** Required-source outcome; optional row sources cannot remove a confirmed listing. */
sealed interface ExploreResult {
    /** Required listing or calendar facts are unresolved. */
    data object Loading : ExploreResult

    /** A required read failed; this is not a confirmed empty search. */
    data object Failure : ExploreResult

    /** Reserved matching dependencies have not been integrated. */
    data object HelperPending : ExploreResult

    /** Confirmed result identities with independent optional display reads.
     * @param campsites Matched actual records.
     * @param orderingPending Whether reserved F11 ordering is absent.
     * @param photos Visible-row attachment states.
     * @param ratings Visible-row rating states.
     */
    data class Content(
        val campsites: List<Campsite>,
        val orderingPending: Boolean,
        val photos: Map<String, ImageSlotState> = emptyMap(),
        val ratings: Map<String, RatingState> = emptyMap(),
    ) : ExploreResult
}
