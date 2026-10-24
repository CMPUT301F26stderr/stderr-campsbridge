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
