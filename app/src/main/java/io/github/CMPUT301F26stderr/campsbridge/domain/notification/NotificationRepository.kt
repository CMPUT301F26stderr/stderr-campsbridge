package io.github.CMPUT301F26stderr.campsbridge.domain.notification

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for shared notification events, inbox acknowledgements, and resumable
 * receipt delivery.
 */
interface NotificationRepository {
    /**
     * Returns a shared notification event, or null when absent.
     *
     * @param id Notification event document ID.
     */
    suspend fun get(id: String): Notification?

    /**
     * Reads confirmed current inbox receipts from the server, including a confirmed empty result.
     * Failures propagate; cached snapshots cannot establish an alert history baseline.
     *
     * @param accountId Account whose saved receipt history is requested.
     */
    suspend fun getInbox(accountId: String): List<InboxReceipt>

    /** Reads intended/Inbox-delivered audience facts without changing delivery or receipts.
     * Missing information differs from a captured empty set. Required server/decoding failures
     * propagate for retry; the result is assembled across reads, not a database snapshot.
     * @param notificationId Original immutable event identity.
     */
    suspend fun getAudience(notificationId: String): NotificationAudience

    /**
     * Observes account-specific inbox receipts; shared event content is fetched separately.
     *
     * @param accountId Account ID whose participation or data is addressed.
     */
    fun observeInbox(accountId: String): Flow<List<InboxReceipt>>

    /**
     * Observes the sender’s events, newest first.
     *
     * @param senderId Account ID sending the message or announcement.
     */
    fun observeSent(senderId: String): Flow<List<Notification>>

    /**
     * Observes readable notification events, newest first, for log inspection.
     */
    fun observeAll(): Flow<List<Notification>>

    /**
     * Captures all entrants who joined and have not withdrawn, including after a draw.
     *
     * @param lotteryId Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param announcement Owner-supplied announcement content.
     */
    suspend fun announce(
        lotteryId: String,
        ownerId: String,
        announcement: Announcement,
    ): String

    /**
     * Sets an inbox receipt’s first-read time without replacing an existing acknowledgement.
     *
     * @param accountId Account ID whose participation or data is addressed.
     * @param notificationId Shared notification event ID.
     */
    suspend fun markRead(
        accountId: String,
        notificationId: String,
    )
}
