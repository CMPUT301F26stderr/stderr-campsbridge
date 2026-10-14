package io.github.CMPUT301F26stderr.campsbridge.domain.moderation

/**
 * Persistence contract for account/listing removal and resumable cleanup of dependent active
 * state.
 */
interface ModerationRepository {
    /**
     * Retains references, scrubs profile data, and resumes dependent cleanup on retries.
     *
     * @param accountId Account ID whose participation or data is addressed.
     * @param actorId Account ID responsible for the requested operation.
     * @param reason Optional explanation recorded with the operation.
     */
    suspend fun removeAccount(
        accountId: String,
        actorId: String,
        reason: String? = null,
    )

    /**
     * Marks a listing removed for its owner/admin and resumes dependent cancellation and
     * cleanup.
     *
     * @param campsiteId Campsite document ID.
     * @param actorId Account ID responsible for the requested operation.
     * @param reason Optional explanation recorded with the operation.
     */
    suspend fun removeCampsite(
        campsiteId: String,
        actorId: String,
        reason: String? = null,
    )

    /**
     * Drains readable removal jobs, including dependent jobs created while processing.
     */
    suspend fun resumeRemovals()
}
