package io.github.CMPUT301F26stderr.campsbridge.domain.review

import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Owns star-rating limits and nonblank review replies. */
class ReviewTest {
    /** Ratings support both endpoints and rating-only feedback, but no out-of-range stars. */
    @Test fun ratingsAreOneThroughFive() {
        for (rating in listOf(1L, 5L)) {
            assertEquals(
                rating,
                Review(
                    "site",
                    "entrant",
                    rating,
                    null,
                    CampingFixtures.now,
                    CampingFixtures.now,
                ).rating,
            )
        }
        for (rating in listOf(0L, 6L)) {
            assertThrows(IllegalArgumentException::class.java) {
                Review("site", "entrant", rating, null, CampingFixtures.now, CampingFixtures.now)
            }
        }
    }

    /** Replies require visible text, including when they target another reply. */
    @Test fun repliesRequireVisibleContent() {
        for (body in listOf("", " \n\t")) {
            assertThrows(IllegalArgumentException::class.java) {
                Reply("reply", "owner", null, body, CampingFixtures.now)
            }
        }
    }
}
