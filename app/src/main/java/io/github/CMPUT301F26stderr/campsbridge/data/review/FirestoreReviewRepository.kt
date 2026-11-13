/*
 * Firebase-backed implementation for campsite ratings, threaded replies, and per-account reactions.
 */

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
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
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

    /** Updates only stars, preserving written feedback and the first creation time.
     * @param campsiteId Feedback campsite.
     * @param authorId Submitting entrant.
     * @param rating Requested stars.
     */
    override suspend fun setRating(
        campsiteId: String,
        authorId: String,
        rating: Long,
    ) {
        require(rating in 1..5)
        edit(campsiteId, authorId) { current -> current.copy(rating = rating) }
    }

    /** Updates only written feedback; blank input clears it without restoring moderated stars.
     * @param campsiteId Feedback campsite.
     * @param authorId Submitting entrant.
     * @param body Requested text, or null to clear it.
     */
    override suspend fun setReviewText(
        campsiteId: String,
        authorId: String,
        body: String?,
    ) =
        edit(campsiteId, authorId) { current -> current.copy(body = body?.takeIf { it.isNotBlank() }) }

    /** Applies an author's field change against current content in the owning transaction.
     * @param campsiteId Published feedback campsite.
     * @param authorId Active entrant whose slot is changed.
     * @param change Pure component edit; the transaction retains creation time and assigns update time.
     */
    private suspend fun edit(
        campsiteId: String,
        authorId: String,
        change: (Review) -> Review,
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, authorId, Role.ENTRANT)
            val campsite =
                tx.require(db.document(FirestorePaths.campsite(campsiteId)), CampsiteDocument)
            requireOperation(campsite.isPublished, INACTIVE, "Campsite unavailable")
            val ref = db.document(FirestorePaths.review(campsiteId, authorId))
            val old = tx.read(ref, ReviewDocument)
            val now = clock.instant()
            val current = old ?: Review(campsiteId, authorId, null, null, now, now)
            val changed = change(current)
            if (changed.rating == current.rating && changed.body == current.body) return@atomic
            tx.set(ref, ReviewDocument.encode(changed.copy(updatedAt = now)))
        }
    }

    /**
     * Saves a nonblank reply once, validating that any parent belongs to the same review.
     * Matching retries retain the first timestamp; a different retained-ID payload conflicts.
     *
     * @param replyId Caller-retained submission identity.
     * @param campsiteId Campsite document ID.
     * @param authorId Account ID identifying the review author.
     * @param senderId Account ID sending the message or announcement.
     * @param parentReplyId Parent reply in the same review, or null to reply to the review
     * itself.
     * @param body Nonblank message or reply text.
     */
    override suspend fun reply(
        replyId: String,
        campsiteId: String,
        authorId: String,
        senderId: String,
        parentReplyId: String?,
        body: String,
    ): String {
        require(body.isNotBlank())
        val ref = db.document(FirestorePaths.reply(campsiteId, authorId, replyId))
        db.atomic { tx ->
            tx.activeAccount(db, senderId)
            val existing = tx.read(ref, ReplyDocument)
            if (existing != null) {
                requireOperation(
                    existing.authorId == senderId && existing.parentReplyId == parentReplyId && existing.body == body,
                    CONFLICT,
                    "Reply identity already has different content",
                )
                return@atomic
            }
            val review = tx.require(db.document(FirestorePaths.review(campsiteId, authorId)), ReviewDocument)
            requireOperation(review.rating != null || review.body != null, INACTIVE, "Feedback is empty")
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
            val review = tx.require(db.document(FirestorePaths.review(campsiteId, authorId)), ReviewDocument)
            val ref = db.document(FirestorePaths.reaction(campsiteId, authorId, reactorId))
            if (reaction == null) {
                tx.delete(ref)
            } else {
                requireOperation(review.rating != null || review.body != null, INACTIVE, "Feedback is empty")
                tx.set(ref, ReactionDocument.encode(Reaction(reactorId, reaction)))
            }
        }
    }
}
