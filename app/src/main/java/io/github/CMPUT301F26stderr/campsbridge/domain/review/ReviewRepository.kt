package io.github.CMPUT301F26stderr.campsbridge.domain.review

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for campsite ratings, threaded replies, and per-account reactions.
 */
interface ReviewRepository {
    /**
     * Observes reviews for one campsite.
     *
     * @param campsiteId Campsite document ID.
     */
    fun observeReviews(campsiteId: String): Flow<List<Review>>

    /**
     * Observes replies in the selected review thread in creation order.
     *
     * @param campsiteId Campsite document ID.
     * @param authorId Account ID identifying the review author.
     */
    fun observeReplies(
        campsiteId: String,
        authorId: String,
    ): Flow<List<Reply>>

    /**
     * Observes per-account reactions to the selected review.
     *
     * @param campsiteId Campsite document ID.
     * @param authorId Account ID identifying the review author.
     */
    fun observeReactions(
        campsiteId: String,
        authorId: String,
    ): Flow<List<Reaction>>

    /**
     * Creates or updates the entrant’s rating on a published campsite while retaining the
     * first creation time.
     *
     * @param campsiteId Campsite document ID.
     * @param authorId Account ID identifying the review author.
     * @param rating Integer star rating from 1 to 5.
     * @param body Optional review text; null supports rating-only feedback.
     */
    suspend fun rate(
        campsiteId: String,
        authorId: String,
        rating: Long,
        body: String?,
    )

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
    suspend fun reply(
        campsiteId: String,
        authorId: String,
        senderId: String,
        parentReplyId: String?,
        body: String,
    ): String

    /**
     * Sets the account’s reaction on an existing review, or removes it when null.
     *
     * @param campsiteId Campsite document ID.
     * @param authorId Account ID identifying the review author.
     * @param reactorId Account ID supplying the review reaction.
     * @param reaction Reaction to save, or null to remove the current reaction.
     */
    suspend fun react(
        campsiteId: String,
        authorId: String,
        reactorId: String,
        reaction: ReactionType?,
    )
}
