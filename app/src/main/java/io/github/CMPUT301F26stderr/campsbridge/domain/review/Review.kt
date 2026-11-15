/*
 * Defines campsite ratings, threaded replies and account reactions.
 */

package io.github.CMPUT301F26stderr.campsbridge.domain.review

import java.time.Instant

/**
 * Independently editable stars and written feedback retained under one author identity.
 *
 * @param campsiteId Campsite document ID.
 * @param authorId Account ID identifying the review author.
 * @param rating Integer stars from 1 to 5, or null when absent or moderated.
 * @param body Optional review text; null supports rating-only feedback.
 * @param createdAt Original record creation time.
 * @param updatedAt Time this record was most recently updated.
 */
data class Review(
    val campsiteId: String,
    val authorId: String,
    val rating: Long?,
    val body: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(rating == null || rating in 1..5)
        require(body == null || body.isNotBlank())
    }
}

/**
 * Immutable reply within one review thread, optionally targeting another reply.
 *
 * @param id Reply ID within its review thread.
 * @param authorId Account ID of the reply author.
 * @param parentReplyId Parent reply in the same review, or null to reply to the review itself.
 * @param body Nonblank message or reply text.
 * @param createdAt Original record creation time.
 */
data class Reply(
    val id: String,
    val authorId: String,
    val parentReplyId: String?,
    val body: String,
    val createdAt: Instant,
) {
    init {
        require(body.isNotBlank())
    }
}

/**
 * Supported reactions on a review; replies do not have separate reactions.
 */
enum class ReactionType {
    LIKE,
    HELPFUL,
}

/**
 * One account’s current reaction to a review.
 *
 * @param reactorId Account ID supplying the review reaction.
 * @param type Selected reaction category.
 */
data class Reaction(
    val reactorId: String,
    val type: ReactionType,
)
