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
