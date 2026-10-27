package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reaction
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReactionType
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reply
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Supplies an explicitly empty read-only review dataset for offline inspection. */
class DemoReviewRepository : ReviewRepository {
    /** Emits the demo's confirmed empty review set.
     * @param campsiteId Selected listing identity.
     */
    override fun observeReviews(campsiteId: String): Flow<List<Review>> =
        flowOf(emptyList())

    /** Emits no demo replies; no network or listener is created.
     * @param campsiteId Selected listing identity.
     * @param authorId Review author identity.
     */
    override fun observeReplies(
        campsiteId: String,
        authorId: String,
    ): Flow<List<Reply>> =
        flowOf(emptyList())

    /** Emits no demo reactions.
     * @param campsiteId Selected listing identity.
     * @param authorId Review author identity.
     */
    override fun observeReactions(
        campsiteId: String,
        authorId: String,
    ): Flow<List<Reaction>> =
        flowOf(emptyList())

    /** Rejects unsupported offline review changes.
     * @param campsiteId Selected listing identity.
     * @param authorId Review author identity.
     * @param rating Requested stars.
     * @param body Optional review body.
     */
    override suspend fun rate(
        campsiteId: String,
        authorId: String,
        rating: Long,
        body: String?,
    ): Unit =
        throw unavailableChange()

    /** Rejects unsupported offline replies.
     * @param campsiteId Selected listing identity.
     * @param authorId Review author identity.
     * @param senderId Reply author identity.
     * @param parentReplyId Optional parent reply.
     * @param body Requested reply text.
     */
    override suspend fun reply(
        campsiteId: String,
        authorId: String,
        senderId: String,
        parentReplyId: String?,
        body: String,
    ): String =
        throw unavailableChange()

    /** Rejects unsupported offline reactions.
     * @param campsiteId Selected listing identity.
     * @param authorId Review author identity.
     * @param reactorId Reacting account.
     * @param reaction Requested reaction.
     */
    override suspend fun react(
        campsiteId: String,
        authorId: String,
        reactorId: String,
        reaction: ReactionType?,
    ): Unit =
        throw unavailableChange()

    /** Describes the explicit offline write limitation without simulating a successful operation. */
    private fun unavailableChange() =
        PersistenceFailure(
            PersistenceFailure.Reason.NOT_ALLOWED,
            "Review changes require the Firebase-enabled build",
        )
}
