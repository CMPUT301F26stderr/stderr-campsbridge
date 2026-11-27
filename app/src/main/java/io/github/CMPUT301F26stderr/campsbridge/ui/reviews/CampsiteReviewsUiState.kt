/*
 * Required feedback source; an empty emission differs from a failed observation.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailUiState

/** Required feedback source; an empty emission differs from a failed observation. */
sealed interface FeedbackRead {
    /** No confirmed feedback emission yet. */
    data object Loading : FeedbackRead

    /** Failed feedback observation, available for explicit Retry. */
    data object Failure : FeedbackRead

    /** Observed visible components; retained empty author records are kept separately.
     * @param reviews Saved records with stars or written text.
     */
    data class Content(
        val reviews: List<Review>,
    ) : FeedbackRead
}

/** Explicit star-action feedback, separate from the observed aggregate and list. */
enum class RatingSaveStatus { NONE, DIRTY, SAVING, NOT_SAVED, SAVED }

/** A displayed saved or tentative star choice.
 * @param value Observed stars or the explicitly requested value.
 * @param status Whether the operation confirmed this attempt.
 */
data class RatingEditState(
    val value: Long? = null,
    val status: RatingSaveStatus = RatingSaveStatus.NONE,
)

/** Saved reader facts and independent author input; optional profile failures never remove rows.
 * @param listing Existing campsite reader outcome.
 * @param feedback Primary feedback observation outcome.
 * @param authorNames Supplemental display names keyed by actual author.
 * @param failedNames Supplemental profile reads with an explicit retry.
 * @param loadingNames Profile identities awaiting a label.
 * @param ownReview Current author's observed record, including retained empty content.
 * @param canEdit Active entrant context may submit feedback for the published listing.
 * @param isAuthor Entrant presentation keeps its editor visible during recoverable read failures.
 * @param canModerate Active admin inspection may supply component moderation actions.
 * @param accountFailed Current capabilities could not be read; author actions remain unavailable.
 * @param rating Independent requested-star operation state.
 * @param textInput Raw written draft, retained after failed operations.
 * @param textSaving A written operation is pending.
 * @param textFailed The last written operation failed.
 * @param textSaved The last explicit written operation returned successfully.
 * @param confirmingClear An unsubmitted removal of saved written feedback is being confirmed.
 * @param confirmingDiscard An unsubmitted dirty-draft exit is being confirmed.
 */
data class CampsiteReviewsUiState(
    val listing: CampsiteDetailUiState = CampsiteDetailUiState.Loading,
    val feedback: FeedbackRead = FeedbackRead.Loading,
    val authorNames: Map<String, String?> = emptyMap(),
    val failedNames: Set<String> = emptySet(),
    val loadingNames: Set<String> = emptySet(),
    val ownReview: Review? = null,
    val canEdit: Boolean = false,
    val isAuthor: Boolean = false,
    val canModerate: Boolean = false,
    val accountFailed: Boolean = false,
    val rating: RatingEditState = RatingEditState(),
    val textInput: String = "",
    val textSaving: Boolean = false,
    val textFailed: Boolean = false,
    val textSaved: Boolean = false,
    val confirmingClear: Boolean = false,
    val confirmingDiscard: Boolean = false,
)
