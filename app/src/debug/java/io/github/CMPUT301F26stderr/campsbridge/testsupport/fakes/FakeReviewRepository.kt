package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reaction
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReactionType
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reply
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Controlled read-only rating source; unsupported feature writes cannot simulate success. */
class FakeReviewRepository : ReviewRepository {
    val records = mutableMapOf<String, MutableStateFlow<List<Review>>>()
    val failures = mutableMapOf<String, MutableStateFlow<Exception?>>()
    val observedIds = mutableListOf<String>()
    var activeObservers = 0

    /** Returns the controlled per-listing review stream.
     * @param campsiteId Actual listing identity.
     */
    fun ratings(campsiteId: String): MutableStateFlow<List<Review>> =
        records.getOrPut(campsiteId) { MutableStateFlow(emptyList()) }

    /** Returns the independent per-listing read failure.
     * @param campsiteId Actual listing identity.
     */
    fun failure(campsiteId: String): MutableStateFlow<Exception?> =
        failures.getOrPut(campsiteId) { MutableStateFlow(null) }

    /** Tracks the selected rating observer's lifetime.
     * @param campsiteId Actual listing identity.
     */
    override fun observeReviews(campsiteId: String): Flow<List<Review>> =
        flow {
            observedIds.add(campsiteId)
            activeObservers++
            try {
                emitAll(
                    combine(ratings(campsiteId), failure(campsiteId)) { rows, error ->
                        error?.let { throw it }
                        rows
                    },
                )
            } finally {
                activeObservers--
            }
        }

    /** Rejects unconfigured thread reads.
     * @param campsiteId Listing identity.
     * @param authorId Review author.
     */
    override fun observeReplies(
        campsiteId: String,
        authorId: String,
    ): Flow<List<Reply>> =
        error("Thread reads are not configured")

    /** Rejects unconfigured reaction reads.
     * @param campsiteId Listing identity.
     * @param authorId Review author.
     */
    override fun observeReactions(
        campsiteId: String,
        authorId: String,
    ): Flow<List<Reaction>> =
        error("Reaction reads are not configured")

    /** Rejects unconfigured review writes.
     * @param campsiteId Listing identity.
     * @param authorId Review author.
     * @param rating Supplied stars.
     * @param body Optional text.
     */
    override suspend fun rate(
        campsiteId: String,
        authorId: String,
        rating: Long,
        body: String?,
    ) {
        error("Review writes are not configured")
    }

    /** Rejects unconfigured replies.
     * @param campsiteId Listing identity.
     * @param authorId Review author.
     * @param senderId Reply author.
     * @param parentReplyId Optional parent.
     * @param body Reply text.
     */
    override suspend fun reply(
        campsiteId: String,
        authorId: String,
        senderId: String,
        parentReplyId: String?,
        body: String,
    ): String =
        error("Reply writes are not configured")

    /** Rejects unconfigured reactions.
     * @param campsiteId Listing identity.
     * @param authorId Review author.
     * @param reactorId Reacting identity.
     * @param reaction Optional reaction.
     */
    override suspend fun react(
        campsiteId: String,
        authorId: String,
        reactorId: String,
        reaction: ReactionType?,
    ) {
        error("Reaction writes are not configured")
    }
}
