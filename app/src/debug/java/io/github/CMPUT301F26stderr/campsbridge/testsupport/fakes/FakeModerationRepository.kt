/*
 * Keeps controller moderation actions explicit and their outcomes under test control.
 */

package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository

/** Controlled moderation boundary; no default operation reports an invented success. */
class FakeModerationRepository : ModerationRepository {
    val requests = mutableListOf<String>()
    var removeRatingHandler: (suspend (String, String, String) -> Unit)? = null
    var removeReviewTextHandler: (suspend (String, String, String) -> Unit)? = null
    var removeAccountHandler: (suspend (String, String, String?) -> Unit)? = null
    var removeCampsiteHandler: (suspend (String, String, String?) -> Unit)? = null
    var resumeHandler: (suspend () -> Unit)? = null

    /** Records a rating removal and uses the configured result.
     * @param campsiteId Feedback campsite.
     * @param authorId Feedback author.
     * @param adminId Acting administrator.
     */
    override suspend fun removeRating(
        campsiteId: String,
        authorId: String,
        adminId: String,
    ) {
        requests += "rating:$campsiteId:$authorId"
        checkNotNull(removeRatingHandler) { "Rating moderation is not configured" }(campsiteId, authorId, adminId)
    }

    /** Records text removal without changing fake records implicitly.
     * @param campsiteId Feedback campsite.
     * @param authorId Feedback author.
     * @param adminId Acting administrator.
     */
    override suspend fun removeReviewText(
        campsiteId: String,
        authorId: String,
        adminId: String,
    ) {
        requests += "review:$campsiteId:$authorId"
        checkNotNull(removeReviewTextHandler) { "Review moderation is not configured" }(campsiteId, authorId, adminId)
    }

    /** Records the requested account removal and explicit reason.
     * @param accountId Target account.
     * @param actorId Requesting actor.
     * @param reason Requested explanation.
     */
    override suspend fun removeAccount(
        accountId: String,
        actorId: String,
        reason: String?,
    ) {
        requests += "account:$accountId"
        checkNotNull(removeAccountHandler) { "Account removal is not configured" }(accountId, actorId, reason)
    }

    /** Records the requested listing removal.
     * @param campsiteId Target campsite.
     * @param actorId Requesting actor.
     * @param reason Requested explanation.
     */
    override suspend fun removeCampsite(
        campsiteId: String,
        actorId: String,
        reason: String?,
    ) {
        requests += "campsite:$campsiteId"
        checkNotNull(removeCampsiteHandler) { "Campsite removal is not configured" }(campsiteId, actorId, reason)
    }

    /** Delegates explicit maintenance to the test's controlled outcome. */
    override suspend fun resumeRemovals() {
        requests += "resume"
        checkNotNull(resumeHandler) { "Removal maintenance is not configured" }()
    }
}
