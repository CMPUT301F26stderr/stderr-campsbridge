/*
 * Rating aggregation derives display facts without a stored summary.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Rating aggregation derives display facts without a stored summary. */
class RatingSummaryTest {
    /** Written-only and moderated feedback do not contribute stars or a zero-star mean. */
    @Test fun onlyRemainingStarsContributeToTheSummary() {
        val time = Instant.EPOCH
        val records =
            listOf(
                Review("site", "one", 5, null, time, time),
                Review("site", "two", null, "Lovely setting", time, time),
                Review("site", "three", 1, "Busy road", time, time),
                Review("site", "four", null, null, time, time),
            )
        assertEquals(RatingSummary(2, 3.0), summarizeRatings(records))
        assertEquals(RatingSummary(0, null), summarizeRatings(records.filter { it.rating == null }))
    }

    /** Confirmed absence has no fabricated zero-star mean. */
    @Test fun emptyReviewsHaveNoMean() {
        assertEquals(RatingSummary(0, null), summarizeRatings(emptyList()))
    }

    /** One or several ratings retain their actual count and arithmetic mean. */
    @Test fun meanAndCountUseEveryRating() {
        val time = Instant.EPOCH
        val first = Review("A", "one", 5, null, time, time)
        assertEquals(RatingSummary(1, 5.0), summarizeRatings(listOf(first)))
        assertEquals(
            RatingSummary(3, 10.0 / 3),
            summarizeRatings(
                listOf(
                    first,
                    first.copy(authorId = "two", rating = 2),
                    first.copy(authorId = "three", rating = 3),
                ),
            ),
        )
    }
}
