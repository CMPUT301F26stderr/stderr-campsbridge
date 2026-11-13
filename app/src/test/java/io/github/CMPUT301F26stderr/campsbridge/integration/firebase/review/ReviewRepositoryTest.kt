/*
 * Tests editable ratings, thread membership and per-account reactions through real persistence.
 */

package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.review

import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReactionDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReplyDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReviewDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reaction
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReactionType
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_FOUND
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.EmulatorActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /** Author edits preserve the other component, including after independent moderation. */
    @Test(timeout = 60000)
    fun starsAndTextRemainIndependentAfterModeration() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val author = firebase.registeredActor(Role.ENTRANT)
            val admin = firebase.registeredActor(Role.ADMIN)
            firebase.seed("campsites/site", CampsiteDocument.encode(CampingFixtures.campsite(ownerId = owner.id)))
            val now = firebase.clock.instant()
            val reviews = author.container.reviews
            reviews.setReviewText("site", author.id, "Quiet evenings by the river")
            val textOnly = readReview(author.id, author)
            assertEquals(Review("site", author.id, null, "Quiet evenings by the river", now, now), textOnly)
            firebase.clock.current = now.plusSeconds(10)
            reviews.setRating("site", author.id, 4)
            assertEquals(
                textOnly.body,
                reviews
                    .observeReviews("site")
                    .first()
                    .single()
                    .body,
            )
            firebase.clock.current = now.plusSeconds(20)
            admin.container.moderation.removeRating("site", author.id, admin.id)
            firebase.clock.current = now.plusSeconds(30)
            reviews.setReviewText("site", author.id, "Bring a warm sleeping bag")
            val textEdit = readReview(author.id, author)
            assertNull(textEdit.rating)
            assertEquals(now, textEdit.createdAt)
            reviews.setRating("site", author.id, 5)
            assertEquals(
                textEdit.body,
                reviews
                    .observeReviews("site")
                    .first()
                    .single()
                    .body,
            )
            firebase.clock.current = now.plusSeconds(40)
            admin.container.moderation.removeReviewText("site", author.id, admin.id)
            firebase.clock.current = now.plusSeconds(50)
            reviews.setRating("site", author.id, 1)
            val ratingEdit = readReview(author.id, author)
            assertEquals(1L, ratingEdit.rating)
            assertNull(ratingEdit.body)
            assertEquals(now, ratingEdit.createdAt)
        }

    /** Moderation keeps children and first creation time; repeated removal leaves update time unchanged. */
    @Test(timeout = 60000)
    fun moderationRetainsThreadsAndEmptyFeedbackCannotAcquireNewInteractions() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val author = firebase.registeredActor(Role.ENTRANT)
            val admin = firebase.registeredActor(Role.ADMIN)
            val now = firebase.clock.instant()
            firebase.seed("campsites/site", CampsiteDocument.encode(CampingFixtures.campsite(ownerId = owner.id)))
            firebase.seed(
                "campsites/site/reviews/${author.id}",
                ReviewDocument.encode(Review("site", author.id, 5, "A lovely stay", now, now)),
            )
            val reviews = owner.container.reviews
            reviews.reply("kept-reply", "site", author.id, owner.id, null, "Thanks for visiting")
            reviews.react("site", author.id, owner.id, ReactionType.HELPFUL)
            val children = reviews.observeReplies("site", author.id).first()
            val reactions = reviews.observeReactions("site", author.id).first()
            assertPersistenceFailure(
                NOT_ALLOWED,
            ) { author.container.moderation.removeRating("site", author.id, author.id) }
            firebase.clock.current = now.plusSeconds(10)
            admin.container.moderation.removeRating("site", author.id, admin.id)
            val first = readReview(author.id, author)
            assertNull(first.rating)
            assertEquals("A lovely stay", first.body)
            assertEquals(now, first.createdAt)
            firebase.clock.current = now.plusSeconds(20)
            admin.container.moderation.removeRating("site", author.id, admin.id)
            assertEquals(first, readReview(author.id, author))
            admin.container.moderation.removeReviewText("site", author.id, admin.id)
            val empty = readReview(author.id, author)
            firebase.clock.current = now.plusSeconds(30)
            admin.container.moderation.removeReviewText("site", author.id, admin.id)
            assertEquals(empty, readReview(author.id, author))
            assertNull(empty.rating)
            assertNull(empty.body)
            assertEquals(
                children,
                owner.clients.firestore
                    .collection("campsites/site/reviews/${author.id}/replies")
                    .read(ReplyDocument),
            )
            assertEquals(
                reactions,
                owner.clients.firestore
                    .collection(
                        "campsites/site/reviews/${author.id}/reactions",
                    ).read(ReactionDocument),
            )
            assertPersistenceFailure(
                INACTIVE,
            ) { reviews.reply("new-reply", "site", author.id, owner.id, null, "Another reply") }
            assertPersistenceFailure(INACTIVE) { reviews.react("site", author.id, author.id, ReactionType.LIKE) }
            reviews.react("site", author.id, owner.id, null)
            assertTrue(reviews.observeReactions("site", author.id).first().isEmpty())
        }

    /** Retained reply identities preserve the saved reply and reject changed payloads. */
    @Test(timeout = 60000)
    fun replyRetryPreservesIdentityAndCreationTime() =
        runBlocking {
            val author = firebase.registeredActor(Role.ENTRANT)
            val owner = firebase.registeredActor(Role.OWNER)
            val now = firebase.clock.instant()
            firebase.seed("campsites/site", CampsiteDocument.encode(CampingFixtures.campsite(ownerId = owner.id)))
            firebase.seed(
                "campsites/site/reviews/${author.id}",
                ReviewDocument.encode(Review("site", author.id, 4, null, now, now)),
            )
            val repository = owner.container.reviews
            assertEquals(
                "retained-reply",
                repository.reply("retained-reply", "site", author.id, owner.id, null, "Thanks"),
            )
            val saved = repository.observeReplies("site", author.id).first().single()
            firebase.clock.current = now.plusSeconds(60)
            assertEquals(saved.id, repository.reply(saved.id, "site", author.id, owner.id, null, saved.body))
            assertEquals(listOf(saved), repository.observeReplies("site", author.id).first())
            assertPersistenceFailure(
                CONFLICT,
            ) { repository.reply(saved.id, "site", author.id, owner.id, null, "Changed") }
            assertPersistenceFailure(CONFLICT) {
                author.container.reviews.reply(saved.id, "site", author.id, author.id, null, saved.body)
            }
            repository.reply("parent", "site", author.id, owner.id, null, "Parent")
            assertPersistenceFailure(
                CONFLICT,
            ) { repository.reply(saved.id, "site", author.id, owner.id, "parent", saved.body) }
            assertEquals(saved, repository.observeReplies("site", author.id).first().single { it.id == saved.id })
        }

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
            author.container.reviews.setRating("site", author.id, 4)
            author.container.reviews.setReviewText("site", author.id, "Quiet")
            firebase.clock.current = now.plusSeconds(60)
            author.container.reviews.setRating("site", author.id, 5)
            author.container.reviews.setReviewText("site", author.id, "   ")
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
            ) { author.container.reviews.setRating("site", author.id, 4) }
            assertPersistenceFailure(
                NOT_ALLOWED,
            ) { owner.container.reviews.setRating("site", owner.id, 4) }
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
            val parent = owner.container.reviews.reply("reply-1", "site", author.id, owner.id, null, "Thanks")
            firebase.clock.current = now.plusSeconds(60)
            val nested =
                author.container.reviews.reply(
                    "reply-2",
                    "site",
                    author.id,
                    author.id,
                    parent,
                    "You are welcome",
                )
            assertPersistenceFailure(NOT_FOUND) {
                author.container.reviews.reply(
                    "reply-3",
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

    /** Confirms saved feedback on the server rather than accepting an earlier listener cache.
     * @param authorId Feedback identity.
     * @param reader Authenticated reader of the public feedback.
     */
    private suspend fun readReview(
        authorId: String,
        reader: EmulatorActor,
    ): Review =
        checkNotNull(
            reader.clients.firestore
                .document("campsites/site/reviews/$authorId")
                .read(ReviewDocument),
        )
}
