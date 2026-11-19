package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** RV-05 through the saved campsite graph, with observed records and independent author actions. */
class CampsiteReviewsFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val site = sampleCampsite("site/reviews ?1", "owner")
    private val now = Instant.parse("2027-05-10T18:00:00Z")
    private val other = Review(site.id, "another-entrant", 5, "Quiet beside the water.", now, now)
    private val ratings = mutableListOf<Long>()
    private val bodies = mutableListOf<String?>()

    /** Releases the actual root and every feedback source it acquired. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** RV-05: star Retry, explicit written save and dirty discard remain independent. */
    @Test fun savedReaderFeedbackActionsFollowObservedRecordsAndKeepConfirmedStarsAfterDiscard() {
        host.matchesText = { _, _ -> true }
        host.matchesCapacity = { _, _ -> true }
        host.campsites.records.value = listOf(site)
        host.reviews.ratings(site.id).value = listOf(other)
        host.reviews.setRatingHandler = { _, _, value ->
            ratings.add(value)
            throw IOException("rating offline")
        }
        host.reviews.setReviewTextHandler = { _, author, body ->
            bodies.add(body)
            val saved =
                host.reviews
                    .ratings(site.id)
                    .value
                    .first { it.authorId == author }
            host.reviews.ratings(site.id).value =
                listOf(other, saved.copy(body = body, updatedAt = now.plusSeconds(60)))
        }
        compose.setContent { host.Content() }
        compose.onNodeWithText(site.details.name).performClick()
        open("Ratings and reviews")
        compose.onNodeWithText(other.body!!).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Rate 3 out of 5").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(emptyList<Long>(), ratings) }
        open("Save rating")
        compose.onNodeWithText("Rating not saved.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(3L), ratings)
            assertEquals(listOf(other), host.reviews.ratings(site.id).value)
            host.reviews.setRatingHandler = { _, author, value ->
                ratings.add(value)
                host.reviews.ratings(site.id).value = listOf(other, Review(site.id, author, value, null, now, now))
            }
        }
        open("Retry rating")
        compose
            .onNodeWithTag(
                "review-text",
            ).performScrollTo()
            .performTextReplacement("A useful written review.\nTwo lines.")
        open("Post review")
        compose
            .onAllNodesWithText(
                "A useful written review.\nTwo lines.",
            ).onFirst()
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithTag(
                "review-text",
            ).performScrollTo()
            .performTextReplacement("Unsaved changes that should be discarded.")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard review draft?").assertIsDisplayed()
        compose.onNodeWithText("Discard draft").performClick()
        compose.onNodeWithText(site.details.name).assertIsDisplayed()
        compose.onNodeWithText("4.0 / 5 (2 ratings)").assertIsDisplayed()
        open("Ratings and reviews")
        compose.onNodeWithTag("review-text").performScrollTo()
        compose.onAllNodesWithText("A useful written review.\nTwo lines.").onFirst().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(3L, 3L), ratings)
            assertEquals(listOf("A useful written review.\nTwo lines."), bodies)
            assertEquals(
                3L,
                host.reviews
                    .ratings(site.id)
                    .value
                    .first { it.authorId == "account-1" }
                    .rating,
            )
        }
    }

    /** Reaches one action through its real scroll owner.
     * @param label Localized visible action.
     */
    private fun open(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
    }
}
