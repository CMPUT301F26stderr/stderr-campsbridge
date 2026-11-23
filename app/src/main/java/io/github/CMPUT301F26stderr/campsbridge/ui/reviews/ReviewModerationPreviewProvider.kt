/*
 * Passive component confirmation, pending, recovery and confirmed outcomes.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/** Passive component confirmation, pending, recovery and confirmed outcomes. */
class ReviewModerationPreviewProvider : PreviewParameterProvider<ReviewModerationState> {
    private val rating =
        ReviewModerationTarget(
            campsiteId = "pine-hollow",
            campsiteName = "Pine Hollow riverside walk-in sites with a very long listing name",
            authorId = "author-with-a-long-saved-identity",
            authorName = "Alex Morgan",
            component = ReviewModerationComponent.RATING,
            rating = 2,
            text = null,
        )
    private val text =
        rating.copy(
            component = ReviewModerationComponent.TEXT,
            rating = null,
            text = "The quiet hours were not enforced and the fire ring was full of litter on arrival.",
        )

    override val values =
        sequenceOf(
            ReviewModerationState(ReviewModerationPhase.CONFIRMING, rating),
            ReviewModerationState(ReviewModerationPhase.CONFIRMING, text),
            ReviewModerationState(ReviewModerationPhase.REMOVING, rating),
            ReviewModerationState(ReviewModerationPhase.FAILED, text),
            ReviewModerationState(ReviewModerationPhase.UNKNOWN, rating),
            ReviewModerationState(ReviewModerationPhase.UNAVAILABLE, text),
            ReviewModerationState(ReviewModerationPhase.COMPLETE, rating),
            ReviewModerationState(ReviewModerationPhase.FAILED, text, dialogVisible = false),
        )
}
