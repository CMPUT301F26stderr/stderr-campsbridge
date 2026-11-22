package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.lifecycle.SavedStateHandle
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewModerationControllerTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun cancellationDoesNotWrite() =
        runTest {
            val f = Fixture(backgroundScope)
            f.request()
            assertEquals(ReviewModerationPhase.CONFIRMING, f.controller.state.value.phase)
            f.controller.dismiss()
            assertEquals(ReviewModerationPhase.IDLE, f.controller.state.value.phase)
            assertTrue(f.writes.isEmpty())
        }

    @Test fun ratingAndTextRemovalPreserveTheIndependentComponentAndCreationTime() =
        runTest {
            val f = Fixture(backgroundScope)
            for (component in ReviewModerationComponent.entries) {
                f.rows.value = listOf(f.original)
                f.request(component)
                f.controller.confirm()
                runCurrent()
                assertEquals(ReviewModerationPhase.COMPLETE, f.controller.state.value.phase)
                val result = f.rows.value.single()
                assertEquals(if (component == ReviewModerationComponent.RATING) null else 4L, result.rating)
                assertEquals(if (component == ReviewModerationComponent.TEXT) null else f.original.body, result.body)
                assertEquals(f.original.createdAt, result.createdAt)
                f.controller.dismiss()
            }
            assertEquals(2, f.refreshes)
        }

    @Test fun failureAndRetryKeepTheSameAuthorCampsiteAndComponent() =
        runTest {
            val f = Fixture(backgroundScope)
            f.failure = IOException("offline")
            f.request()
            f.controller.confirm()
            runCurrent()
            assertEquals(ReviewModerationPhase.FAILED, f.controller.state.value.phase)
            f.controller.request(f.original.copy(authorId = "other"), ReviewModerationComponent.TEXT, "Other", null)
            f.failure = null
            f.controller.retry()
            runCurrent()
            assertEquals(listOf("RATING:${f.site.id}:author", "RATING:${f.site.id}:author"), f.writes)
            assertEquals(
                f.original.body,
                f.rows.value
                    .single()
                    .body,
            )
        }

    private class Fixture(
        private val scope: CoroutineScope,
    ) {
        val admin = CampingFixtures.account().copy(roles = setOf(Role.ADMIN))
        val site = CampingFixtures.campsite()
        val original = Review(site.id, "author", 4, "Keep this written feedback.", Instant.EPOCH, Instant.EPOCH)
        val accounts = FakeAccountRepository(admin)
        val sites = FakeCampsiteRepository(listOf(site))
        val reviews = FakeReviewRepository()
        val rows = reviews.ratings(site.id).also { it.value = listOf(original) }
        val moderation = FakeModerationRepository()
        val saved = SavedStateHandle()
        var allowed = true
        var refreshes = 0
        var failure: Exception? = null
        var failAfterCommit = false
        var gate: CompletableDeferred<Unit>? = null
        val writes = mutableListOf<String>()
        var controller = create()

        init {
            moderation.removeRatingHandler =
                { site, author, _ -> remove(site, author, ReviewModerationComponent.RATING) }
            moderation.removeReviewTextHandler =
                { site, author, _ -> remove(site, author, ReviewModerationComponent.TEXT) }
        }

        private suspend fun remove(
            site: String,
            author: String,
            component: ReviewModerationComponent,
        ) {
            writes += "$component:$site:$author"
            gate?.await()
            failure?.let { throw it }
            rows.value =
                rows.value.map {
                    if (it.campsiteId != site || it.authorId != author) {
                        it
                    } else if (component == ReviewModerationComponent.RATING) {
                        it.copy(rating = null)
                    } else {
                        it.copy(body = null)
                    }
                }
            if (failAfterCommit) throw IOException("response lost")
        }

        fun request(component: ReviewModerationComponent = ReviewModerationComponent.RATING) =
            controller.request(original, component, site.details.name, "Taylor")

        fun create() =
            ReviewModerationController(
                admin.id,
                site.id,
                accounts,
                sites,
                reviews,
                moderation,
                saved,
                scope,
                { allowed },
                { refreshes++ },
            )
    }
}
