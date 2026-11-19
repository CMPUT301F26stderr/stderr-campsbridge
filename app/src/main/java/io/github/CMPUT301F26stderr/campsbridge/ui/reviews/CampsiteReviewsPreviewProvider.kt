package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import java.time.Instant

/** Supplies passive author/reader states with separate component and action outcomes. */
class CampsiteReviewsPreviewProvider : PreviewParameterProvider<CampsiteReviewsUiState> {
    override val values: Sequence<CampsiteReviewsUiState> =
        run {
            val now = Instant.parse("2027-05-10T18:00:00Z")
            val site =
                previewCampsite().let {
                    it.copy(details = it.details.copy(name = "Riverbend family campsite beside the mountain trail"))
                }
            val own =
                Review(
                    site.id,
                    "author",
                    4,
                    "A quiet stay by the water.\nThe shaded pitches were helpful in the afternoon.",
                    now,
                    now,
                )
            val rows =
                listOf(
                    own,
                    Review(site.id, "other", 5, null, now, now),
                    Review(
                        site.id,
                        "missing-profile",
                        null,
                        "Room for our family and a good view of the river.",
                        now,
                        now,
                    ),
                )
            val content =
                CampsiteReviewsUiState(
                    listing = CampsiteDetailUiState.Content(site),
                    feedback = FeedbackRead.Content(rows),
                    authorNames = mapOf("author" to "Alex Chen", "other" to "Jamie Lee with a longer display name"),
                    ownReview = own,
                    canEdit = true,
                    isAuthor = true,
                    rating = RatingEditState(4),
                    textInput = own.body.orEmpty(),
                )
            sequenceOf(
                content,
                content.copy(canEdit = false, isAuthor = false),
                content.copy(
                    feedback = FeedbackRead.Content(emptyList()),
                    ownReview = null,
                    rating = RatingEditState(),
                    textInput = "",
                ),
                content.copy(feedback = FeedbackRead.Loading, canEdit = false),
                content.copy(feedback = FeedbackRead.Failure, canEdit = false),
                content.copy(
                    rating = RatingEditState(3, RatingSaveStatus.SAVING),
                    textInput = "A separate written draft\nStill being edited.",
                ),
                content.copy(
                    rating = RatingEditState(3, RatingSaveStatus.NOT_SAVED),
                    textInput = "A separate written draft\nStill being edited.",
                ),
                content.copy(textSaving = true),
                content.copy(textFailed = true, textInput = "Keep this draft after a failed save."),
                content.copy(confirmingClear = true, textInput = ""),
                content.copy(confirmingDiscard = true, textInput = "Unsaved written changes."),
                CampsiteReviewsUiState(listing = CampsiteDetailUiState.Loading),
                CampsiteReviewsUiState(listing = CampsiteDetailUiState.Failure),
                CampsiteReviewsUiState(listing = CampsiteDetailUiState.Unavailable),
            )
        }
}
