package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.review

import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReviewDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reaction
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReactionType
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_FOUND
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Owns editable ratings, thread membership and per-account reactions through real persistence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class ReviewRepositoryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Rating edits use one author slot, preserve creation time and normalize rating-only feedback. */
    @Test(timeout = 60000)
    fun ratingEditsPreserveCreationTimeAndScopeResultsToTheCampsite() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val author = firebase.registeredActor(Role.ENTRANT)
            firebase.seed(
                "campsites/site",
                CampsiteDocument.encode(CampingFixtures.campsite(ownerId = owner.id)),
            )
            val now = firebase.clock.instant()
            author.container.reviews.rate("site", author.id, 4, "Quiet")
            firebase.clock.current = now.plusSeconds(60)
            author.container.reviews.rate("site", author.id, 5, "   ")
            firebase.seed(
                "campsites/other/reviews/${author.id}",
                ReviewDocument.encode(
                    Review("other", author.id, 2, "Elsewhere", now, now),
                ),
            )
            assertEquals(
                listOf(Review("site", author.id, 5, null, now, firebase.clock.instant())),
                author.container.reviews
                    .observeReviews("site")
                    .first(),
            )
        }

    /** An unavailable campsite or an account without entrant capability cannot acquire a rating slot. */
    @Test(timeout = 60000)
    fun ratingRequiresAnEntrantAndPublishedCampsite() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val author = firebase.registeredActor(Role.ENTRANT)
            firebase.seed(
                "campsites/site",
                CampsiteDocument.encode(
                    CampingFixtures
                        .campsite(
                            ownerId = owner.id,
                        ).copy(removal = Removal(firebase.clock.instant(), owner.id)),
                ),
            )
            assertPersistenceFailure(
                INACTIVE,
            ) { author.container.reviews.rate("site", author.id, 4, null) }
            assertPersistenceFailure(
                NOT_ALLOWED,
            ) { owner.container.reviews.rate("site", owner.id, 4, null) }
            assertTrue(
                author.container.reviews
                    .observeReviews("site")
                    .first()
                    .isEmpty(),
            )
        }

    /** Nested replies retain their parent and reject a parent ID from another review thread. */
    @Test(timeout = 60000)
    fun repliesAreOrderedAndCannotCrossThreadBoundaries() =
        runBlocking {
            val author = firebase.registeredActor(Role.ENTRANT)
            val owner = firebase.registeredActor(Role.OWNER)
            val now = firebase.clock.instant()
            for (site in listOf("site", "other")) {
                firebase.seed(
                    "campsites/$site/reviews/${author.id}",
                    ReviewDocument.encode(
                        Review(site, author.id, 4, null, now, now),
                    ),
                )
            }
            val parent = owner.container.reviews.reply("site", author.id, owner.id, null, "Thanks")
            firebase.clock.current = now.plusSeconds(60)
            val nested =
                author.container.reviews.reply(
                    "site",
                    author.id,
                    author.id,
                    parent,
                    "You are welcome",
                )
            assertPersistenceFailure(NOT_FOUND) {
                author.container.reviews.reply(
                    "other",
                    author.id,
                    author.id,
                    parent,
                    "Wrong thread",
                )
            }
            val replies =
                author.container.reviews
                    .observeReplies("site", author.id)
                    .first()
            assertEquals(listOf(parent, nested), replies.map { it.id })
            assertEquals(listOf(null, parent), replies.map { it.parentReplyId })
            assertEquals(listOf(owner.id, author.id), replies.map { it.authorId })
            assertTrue(
                author.container.reviews
                    .observeReplies("other", author.id)
                    .first()
                    .isEmpty(),
            )
        }

    /** Replacing/removing one reaction leaves the other account's reaction untouched. */
    @Test(timeout = 60000)
    fun reactionsHaveOneSlotPerAccount() =
        runBlocking {
            val author = firebase.registeredActor(Role.ENTRANT)
            val owner = firebase.registeredActor(Role.OWNER)
            val now = firebase.clock.instant()
            firebase.seed(
                "campsites/site/reviews/${author.id}",
                ReviewDocument.encode(
                    Review("site", author.id, 4, null, now, now),
                ),
            )
            author.container.reviews.react("site", author.id, author.id, ReactionType.LIKE)
            owner.container.reviews.react("site", author.id, owner.id, ReactionType.LIKE)
            author.container.reviews.react("site", author.id, author.id, ReactionType.HELPFUL)
            assertEquals(
                setOf(
                    Reaction(author.id, ReactionType.HELPFUL),
                    Reaction(owner.id, ReactionType.LIKE),
                ),
                author.container.reviews
                    .observeReactions("site", author.id)
                    .first()
                    .toSet(),
            )
            author.container.reviews.react("site", author.id, author.id, null)
            author.container.reviews.react("site", author.id, author.id, null)
            assertEquals(
                listOf(Reaction(owner.id, ReactionType.LIKE)),
                author.container.reviews
                    .observeReactions("site", author.id)
                    .first(),
            )
        }
}
