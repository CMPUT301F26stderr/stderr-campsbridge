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

    @Test fun changedOtherComponentDoesNotBlockOrOverwriteIt() =
        runTest {
            val f = Fixture(backgroundScope)
            f.request()
            f.rows.value = listOf(f.original.copy(body = "New written feedback"))
            f.controller.confirm()
            runCurrent()
            assertEquals(
                "New written feedback",
                f.rows.value
                    .single()
                    .body,
            )
            assertEquals(ReviewModerationPhase.COMPLETE, f.controller.state.value.phase)
        }

    @Test fun changedSelectedRatingOrTextCannotRemoveAReplacement() =
        runTest {
            for (component in ReviewModerationComponent.entries) {
                val f = Fixture(backgroundScope)
                f.request(component)
                f.rows.value = listOf(f.original.copy(rating = 5, body = "Replacement"))
                f.controller.confirm()
                runCurrent()
                assertEquals(ReviewModerationPhase.UNAVAILABLE, f.controller.state.value.phase)
                assertTrue(f.writes.isEmpty())
            }
        }

    @Test fun missingAuthorRatingOrCampsiteDoesNotWrite() =
        runTest {
            for (missing in 0..2) {
                val f = Fixture(backgroundScope)
                f.request()
                when (missing) {
                    0 -> f.rows.value = emptyList()
                    1 -> f.rows.value = listOf(f.original.copy(rating = null))
                    2 -> f.sites.records.value = emptyList()
                }
                f.controller.confirm()
                runCurrent()
                assertEquals(ReviewModerationPhase.UNAVAILABLE, f.controller.state.value.phase)
                assertTrue(f.writes.isEmpty())
            }
        }

    @Test fun roleLossAndForeignCampsiteCannotSubmit() =
        runTest {
            val f = Fixture(backgroundScope)
            f.controller.request(
                f.original.copy(campsiteId = "foreign"),
                ReviewModerationComponent.RATING,
                "Foreign",
                null,
            )
            assertEquals(ReviewModerationPhase.IDLE, f.controller.state.value.phase)
            f.request()
            f.accounts.account.value = f.admin.copy(roles = setOf(Role.OWNER))
            f.controller.confirm()
            runCurrent()
            assertEquals(ReviewModerationPhase.UNAVAILABLE, f.controller.state.value.phase)
            assertTrue(f.writes.isEmpty())
        }

    @Test fun duplicateClicksCannotRetargetAnInFlightRemoval() =
        runTest {
            val f = Fixture(backgroundScope)
            f.gate = CompletableDeferred()
            f.request()
            f.controller.confirm()
            runCurrent()
            f.controller.confirm()
            f.controller.retry()
            f.controller.request(f.original.copy(authorId = "other"), ReviewModerationComponent.TEXT, "Other", null)
            f.controller.dismiss()
            assertEquals(ReviewModerationPhase.REMOVING, f.controller.state.value.phase)
            f.gate!!.complete(Unit)
            runCurrent()
            assertEquals(1, f.writes.size)
        }

    @Test fun committedButUnacknowledgedRemovalDoesNotReplayTheWrite() =
        runTest {
            val f = Fixture(backgroundScope)
            f.failAfterCommit = true
            f.request()
            f.controller.confirm()
            runCurrent()
            assertEquals(ReviewModerationPhase.COMPLETE, f.controller.state.value.phase)
            f.controller.retry()
            runCurrent()
            assertEquals(1, f.writes.size)
        }

    @Test fun recreationRetainsTheConfirmedTargetWithoutAutomaticallyWriting() =
        runTest {
            val f = Fixture(backgroundScope)
            f.failure = IOException("offline")
            f.request(ReviewModerationComponent.TEXT)
            f.controller.confirm()
            runCurrent()
            f.controller.close()
            f.controller = f.create()
            runCurrent()
            assertEquals(1, f.writes.size)
            assertEquals(
                "author",
                f.controller.state.value.target
                    ?.authorId,
            )
            f.failure = null
            f.controller.retry()
            runCurrent()
            assertEquals(ReviewModerationPhase.COMPLETE, f.controller.state.value.phase)
            assertEquals(
                4L,
                f.rows.value
                    .single()
                    .rating,
            )
        }

    @Test fun failedReadsAreRetryableWithoutWritingAndClosingStopsLateResults() =
        runTest {
            val f = Fixture(backgroundScope)
            f.reviews.failure(f.site.id).value = IOException("read failed")
            f.request()
            f.controller.confirm()
            runCurrent()
            assertEquals(ReviewModerationPhase.UNKNOWN, f.controller.state.value.phase)
            assertTrue(f.writes.isEmpty())
            f.reviews.failure(f.site.id).value = null
            f.gate = CompletableDeferred()
            f.controller.retry()
            runCurrent()
            f.controller.close()
            val state = f.controller.state.value
            f.gate!!.complete(Unit)
            runCurrent()
            f.controller.retry()
            assertEquals(state, f.controller.state.value)
            assertEquals(0, f.refreshes)
        }

    @Test fun dismissedFailureResumesTheOriginalRequest() =
        runTest {
            val f = Fixture(backgroundScope)
            f.failure = IOException("offline")
            f.request()
            f.controller.confirm()
            runCurrent()
            val target = f.controller.state.value.target
            f.controller.dismiss()
            assertEquals(false, f.controller.state.value.dialogVisible)
            f.request(ReviewModerationComponent.TEXT)
            f.controller.resume()
            assertEquals(target, f.controller.state.value.target)
            assertEquals(true, f.controller.state.value.dialogVisible)
            assertEquals(1, f.writes.size)
        }

    @Test fun nonAdminPresentationCannotRequestOrConfirmModeration() =
        runTest {
            val f = Fixture(backgroundScope)
            f.allowed = false
            f.request()
            f.controller.confirm()
            runCurrent()
            assertEquals(ReviewModerationPhase.IDLE, f.controller.state.value.phase)
            assertTrue(f.writes.isEmpty())
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
