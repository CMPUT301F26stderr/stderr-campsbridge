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

}
