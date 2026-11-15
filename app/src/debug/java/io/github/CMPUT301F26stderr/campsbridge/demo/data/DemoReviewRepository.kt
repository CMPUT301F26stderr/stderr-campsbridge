/*
 * Keeps offline feedback, replies and reactions in the same committed store as campsite records.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReactionDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReplyDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReviewDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reaction
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReactionType
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reply
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.flow.Flow
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths as Paths

/** Local feedback shared by every demo person.
 * @param store Committed records and their common clock.
 */
class DemoReviewRepository(
    private val store: DemoStore = DemoStore(),
) : ReviewRepository {
    /** Observes all retained feedback for one campsite, including empty moderation parents.
     * @param campsiteId Feedback campsite.
     */
    override fun observeReviews(campsiteId: String): Flow<List<Review>> =
        store.observe { list("${Paths.campsite(campsiteId)}/reviews", ReviewDocument) }

    /** Observes a retained reply thread in creation order.
     * @param campsiteId Feedback campsite.
     * @param authorId Review author.
     */
    override fun observeReplies(
        campsiteId: String,
        authorId: String,
    ): Flow<List<Reply>> =
        store.observe { list("${Paths.review(campsiteId, authorId)}/replies", ReplyDocument).sortedBy { it.createdAt } }

    /** Observes the saved per-person reactions.
     * @param campsiteId Feedback campsite.
     * @param authorId Review author.
     */
    override fun observeReactions(
        campsiteId: String,
        authorId: String,
    ): Flow<List<Reaction>> =
        store.observe { list("${Paths.review(campsiteId, authorId)}/reactions", ReactionDocument) }

    /** Changes stars without replacing text or its original creation time.
     * @param campsiteId Published campsite.
     * @param authorId Submitting entrant.
     * @param rating Stars from 1 through 5.
     */
    override suspend fun setRating(
        campsiteId: String,
        authorId: String,
        rating: Long,
    ) {
        require(rating in 1..5)
        edit(campsiteId, authorId) { it.copy(rating = rating) }
    }

    /** Changes only the author's text; null or blank clears that component.
     * @param campsiteId Published campsite.
     * @param authorId Submitting entrant.
     * @param body Requested text.
     */
    override suspend fun setReviewText(
        campsiteId: String,
        authorId: String,
        body: String?,
    ) =
        edit(campsiteId, authorId) { it.copy(body = body?.takeIf { text -> text.isNotBlank() }) }

    /** Commits one component against the latest saved feedback.
     * @param campsiteId Feedback campsite.
     * @param authorId Active entrant.
     * @param change Pure edit preserving the other component.
     */
    private suspend fun edit(
        campsiteId: String,
        authorId: String,
        change: (Review) -> Review,
    ) {
        store.change {
            active(authorId, Role.ENTRANT)
            requireOperation(
                require(Paths.campsite(campsiteId), CampsiteDocument).isPublished,
                INACTIVE,
                "Campsite unavailable",
            )
            val path = Paths.review(campsiteId, authorId)
            val now = store.clock.instant()
            val current = get(path, ReviewDocument) ?: Review(campsiteId, authorId, null, null, now, now)
            val changed = change(current)
            if (changed != current) put(path, ReviewDocument, changed.copy(updatedAt = now))
        }
    }

    /** Saves a reply once and preserves its original timestamp on an identical retry.
     * @param replyId Retained submission identity.
     * @param campsiteId Feedback campsite.
     * @param authorId Review author.
     * @param senderId Reply author.
     * @param parentReplyId Optional parent in this thread.
     * @param body Visible reply content.
     */
    override suspend fun reply(
        replyId: String,
        campsiteId: String,
        authorId: String,
        senderId: String,
        parentReplyId: String?,
        body: String,
    ): String =
        store.change {
            require(body.isNotBlank())
            active(senderId)
            val path = Paths.reply(campsiteId, authorId, replyId)
            val old = get(path, ReplyDocument)
            if (old != null) {
                requireOperation(
                    old.authorId == senderId && old.parentReplyId == parentReplyId && old.body == body,
                    CONFLICT,
                    "Reply identity has different content",
                )
            } else {
                val review = require(Paths.review(campsiteId, authorId), ReviewDocument)
                requireOperation(review.rating != null || review.body != null, INACTIVE, "Feedback is empty")
                if (parentReplyId != null) require(Paths.reply(campsiteId, authorId, parentReplyId), ReplyDocument)
                put(path, ReplyDocument, Reply(replyId, senderId, parentReplyId, body, store.clock.instant()))
            }
            replyId
        }

    /** Changes one person's reaction without changing other reactions or feedback.
     * @param campsiteId Feedback campsite.
     * @param authorId Review author.
     * @param reactorId Reacting person.
     * @param reaction Desired reaction, or null to remove it.
     */
    override suspend fun react(
        campsiteId: String,
        authorId: String,
        reactorId: String,
        reaction: ReactionType?,
    ) {
        store.change {
            active(reactorId)
            val review = require(Paths.review(campsiteId, authorId), ReviewDocument)
            val path = Paths.reaction(campsiteId, authorId, reactorId)
            if (reaction == null) {
                raw.remove(path)
            } else {
                requireOperation(review.rating != null || review.body != null, INACTIVE, "Feedback is empty")
                put(path, ReactionDocument, Reaction(reactorId, reaction))
            }
        }
    }
}
