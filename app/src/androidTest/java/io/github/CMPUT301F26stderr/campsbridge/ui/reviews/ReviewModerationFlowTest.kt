package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Actual saved campsite feedback navigation; no separate administrator review browser. */
class ReviewModerationFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val site = sampleCampsite("site/moderation ?1", "owner")
    private val original =
        Review(site.id, "author/one", 4, "Keep this independent written feedback.", Instant.EPOCH, Instant.EPOCH)
    private val rows = host.reviews.ratings(site.id)
    private var fail = false
    private var gate: CompletableDeferred<Unit>? = null

    @After fun close() {
        compose.runOnIdle {
            gate?.complete(Unit)
            host.close()
        }
    }

    private fun openFeedback(role: Role = Role.ADMIN) {
        host.accounts.account.value = sampleAccount(setOf(role))
        host.campsites.records.value = listOf(site)
        host.matchesText = { _, _ -> true }
        host.matchesCapacity = { _, _ -> true }
        rows.value = listOf(original)
        host.moderation.removeRatingHandler = { id, author, _ -> remove(id, author, rating = true) }
        host.moderation.removeReviewTextHandler = { id, author, _ -> remove(id, author, rating = false) }
        compose.setContent { host.Content() }
        compose.onNodeWithText(site.details.name).performClick()
        click("Ratings and reviews")
    }

    private suspend fun remove(
        siteId: String,
        author: String,
        rating: Boolean,
    ) {
        gate?.await()
        if (fail) throw IOException("offline")
        rows.value =
            rows.value.map {
                if (it.campsiteId != siteId || it.authorId != author) {
                    it
                } else if (rating) {
                    it.copy(rating = null)
                } else {
                    it.copy(body = null)
                }
            }
    }

    @Test fun cancellingEitherComponentDoesNotWrite() {
        openFeedback()
        for (label in listOf("Remove rating", "Remove written review")) {
            click(label)
            compose.onNodeWithText("Campsite ID: ${site.id}").assertIsDisplayed()
            compose.onNodeWithText("Author ID: ${original.authorId}").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
        }
        compose.runOnIdle {
            assertTrue(host.moderation.requests.isEmpty())
            assertEquals(listOf(original), rows.value)
        }
    }

    @Test fun ratingRemovalRefreshesTheSavedSummaryAndKeepsWrittenFeedback() {
        openFeedback()
        click("Remove rating")
        compose.onNodeWithText("Selected rating: 4 out of 5").assertIsDisplayed()
        compose.onNodeWithText("Confirm removal").performClick()
        compose.onNodeWithText("Selected feedback component removed.").assertIsDisplayed()
        compose.onNodeWithText("Return to feedback").performClick()
        compose.onNodeWithText(original.body!!).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No ratings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove rating").assertDoesNotExist()
        compose.runOnIdle { assertEquals(original.copy(rating = null), rows.value.single()) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("No ratings").assertIsDisplayed()
    }

    @Test fun writtenRemovalKeepsTheSavedStarsAndCampsite() {
        openFeedback()
        click("Remove written review")
        compose.onNodeWithText("Confirm removal").performClick()
        compose.onNodeWithText("Selected feedback component removed.").assertIsDisplayed()
        compose.onNodeWithText("Return to feedback").performClick()
        compose.onNodeWithText(original.body!!).assertDoesNotExist()
        compose.onNodeWithText("4.0 / 5 (1 rating)").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(original.copy(body = null), rows.value.single())
            assertEquals(listOf(site), host.campsites.records.value)
        }
    }

    @Test fun retryRetainsOriginalRatingAfterTheListChanges() =
        failureRetry(rating = true)

    @Test fun retryRetainsOriginalTextAfterTheListChanges() =
        failureRetry(rating = false)

    private fun failureRetry(rating: Boolean) {
        fail = true
        openFeedback()
        click(if (rating) "Remove rating" else "Remove written review")
        compose.onNodeWithText("Confirm removal").performClick()
        compose
            .onNodeWithText(
                "Removal did not finish. Retry the same selected feedback component.",
            ).assertIsDisplayed()
        val other = original.copy(authorId = "new-author", rating = 5, body = "Other review")
        compose.runOnIdle {
            rows.value = listOf(other, original)
            fail = false
        }
        compose.onNodeWithText("Author ID: ${original.authorId}").assertIsDisplayed()
        compose.onNodeWithText("Retry removal").performClick()
        compose.onNodeWithText("Selected feedback component removed.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(other, rows.value.first())
            assertEquals(if (rating) null else original.rating, rows.value.last().rating)
            assertEquals(if (rating) original.body else null, rows.value.last().body)
            val prefix = if (rating) "rating" else "review"
            assertEquals(List(2) { "$prefix:${site.id}:${original.authorId}" }, host.moderation.requests)
        }
    }

    @Test fun heldRemovalCannotBeRepeatedOrReportedAsComplete() {
        gate = CompletableDeferred()
        openFeedback()
        click("Remove rating")
        compose.onNodeWithText("Confirm removal").performClick()
        compose.onNodeWithText("Confirm removal").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Selected feedback component removed.").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, host.moderation.requests.size)
            gate!!.complete(Unit)
        }
        compose.onNodeWithText("Selected feedback component removed.").assertIsDisplayed()
    }

    @Test fun changedSelectedRatingIsExplainedWithoutRemovingItsReplacement() {
        openFeedback()
        click("Remove rating")
        compose.runOnIdle { rows.value = listOf(original.copy(rating = 5)) }
        compose.onNodeWithText("Confirm removal").performClick()
        compose
            .onNodeWithText(
                "The original component is missing, has changed, or is no longer available to this administrator. No replacement will be removed.",
            ).assertIsDisplayed()
        compose.runOnIdle { assertTrue(host.moderation.requests.isEmpty()) }
    }

    @Test fun entrantFeedbackDoesNotExposeModeration() {
        openFeedback(Role.ENTRANT)
        compose.onNodeWithText(original.body!!).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove rating").assertDoesNotExist()
        compose.onNodeWithText("Remove written review").assertDoesNotExist()
        compose.runOnIdle { assertTrue(host.moderation.requests.isEmpty()) }
    }

    private fun click(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
    }
}
