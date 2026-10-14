package io.github.CMPUT301F26stderr.campsbridge.data.review

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reaction
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReactionType
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reply
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.flow.map
import java.time.Clock

/**
 * Firebase-backed implementation for campsite ratings, threaded replies, and per-account
 * reactions.
 *
 * @param db Firestore client used for queries and coordinated writes.
 * @param clock Injectable time source for recorded timestamps and deadline checks.
 */
class FirestoreReviewRepository(
    private val db: FirebaseFirestore,
    private val clock: Clock,
) : ReviewRepository {
    /**
     * Observes reviews for one campsite.
     *
     * @param campsiteId Campsite document ID.
     */
    override fun observeReviews(campsiteId: String) =
        db.collection("${FirestorePaths.campsite(campsiteId)}/reviews").observe(ReviewDocument)

    /**
     * Observes replies in the selected review thread in creation order.
     *
     * @param campsiteId Campsite document ID.
     * @param authorId Account ID identifying the review author.
     */
    override fun observeReplies(
        campsiteId: String,
        authorId: String,
    ) =
        db
            .collection("${FirestorePaths.review(campsiteId, authorId)}/replies")
            .observe(ReplyDocument)
            .map { it.sortedBy { reply -> reply.createdAt } }

    /**
     * Observes per-account reactions to the selected review.
     *
     * @param campsiteId Campsite document ID.
     * @param authorId Account ID identifying the review author.
     */
    override fun observeReactions(
        campsiteId: String,
        authorId: String,
    ) =
        db
            .collection("${FirestorePaths.review(campsiteId, authorId)}/reactions")
            .observe(ReactionDocument)

    /**
     * Creates or updates the entrant’s rating on a published campsite while retaining the
     * first creation time.
     *
     * @param campsiteId Campsite document ID.
     * @param authorId Account ID identifying the review author.
     * @param rating Integer star rating from 1 to 5.
     * @param body Optional review text; null supports rating-only feedback.
     */
    override suspend fun rate(
        campsiteId: String,
        authorId: String,
        rating: Long,
        body: String?,
    ) {
        require(rating in 1..5)
        db.atomic { tx ->
            tx.activeAccount(db, authorId, Role.ENTRANT)
            val campsite =
                tx.require(db.document(FirestorePaths.campsite(campsiteId)), CampsiteDocument)
            requireOperation(campsite.isPublished, INACTIVE, "Campsite unavailable")
            val ref = db.document(FirestorePaths.review(campsiteId, authorId))
            val old = tx.read(ref, ReviewDocument)
            val now = clock.instant()
            tx.set(
                ref,
                ReviewDocument.encode(
                    Review(
                        campsiteId,
                        authorId,
                        rating,
                        body?.takeIf { it.isNotBlank() },
                        old?.createdAt ?: now,
                        now,
                    ),
                ),
            )
        }
    }

    /**
     * Appends a nonblank reply, validating any parent belongs to the same review; returns
     * its ID.
     *
     * @param campsiteId Campsite document ID.
     * @param authorId Account ID identifying the review author.
     * @param senderId Account ID sending the message or announcement.
     * @param parentReplyId Parent reply in the same review, or null to reply to the review
     * itself.
     * @param body Nonblank message or reply text.
     */
    override suspend fun reply(
        campsiteId: String,
        authorId: String,
        senderId: String,
        parentReplyId: String?,
        body: String,
    ): String {
        require(body.isNotBlank())
        val ref = db.collection("${FirestorePaths.review(campsiteId, authorId)}/replies").document()
        db.atomic { tx ->
            tx.activeAccount(db, senderId)
            tx.require(db.document(FirestorePaths.review(campsiteId, authorId)), ReviewDocument)
            if (parentReplyId != null) {
                tx.require(
                    db.document(FirestorePaths.reply(campsiteId, authorId, parentReplyId)),
                    ReplyDocument,
                )
            }
            tx.set(
                ref,
                ReplyDocument.encode(Reply(ref.id, senderId, parentReplyId, body, clock.instant())),
            )
        }
        return ref.id
    }

    /**
     * Sets the account’s reaction on an existing review, or removes it when null.
     *
     * @param campsiteId Campsite document ID.
     * @param authorId Account ID identifying the review author.
     * @param reactorId Account ID supplying the review reaction.
     * @param reaction Reaction to save, or null to remove the current reaction.
     */
    override suspend fun react(
        campsiteId: String,
        authorId: String,
        reactorId: String,
        reaction: ReactionType?,
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, reactorId)
            tx.require(db.document(FirestorePaths.review(campsiteId, authorId)), ReviewDocument)
            val ref = db.document(FirestorePaths.reaction(campsiteId, authorId, reactorId))
            if (reaction == null) {
                tx.delete(ref)
            } else {
                tx.set(ref, ReactionDocument.encode(Reaction(reactorId, reaction)))
            }
        }
    }
}
