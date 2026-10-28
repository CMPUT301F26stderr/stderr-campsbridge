package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review

/** Derived rating facts; absent mean is a confirmed empty set, never a failed read.
 * @param count Number of confirmed reviews.
 * @param mean Mean stars, or null when no reviews exist.
 */
data class RatingSummary(
    val count: Int,
    val mean: Double?,
)

/** Independent read state for an optional rating source. */
sealed interface RatingState {
    /** No confirmed review emission yet. */
    data object Loading : RatingState

    /** Review read failed; no zero-rating claim is made. */
    data object Failure : RatingState

    /** Review facts derived from the actual records.
     * @param summary Current mean and count.
     */
    data class Ready(
        val summary: RatingSummary,
    ) : RatingState
}

/** Summarizes existing reviews without persisting a duplicate average.
 * @param reviews Confirmed rating records.
 */
fun summarizeRatings(reviews: List<Review>): RatingSummary =
    RatingSummary(
        reviews.size,
        if (reviews.isEmpty()) {
            null
        } else {
            reviews.sumOf { it.rating.toDouble() } /
                reviews.size
        },
    )
