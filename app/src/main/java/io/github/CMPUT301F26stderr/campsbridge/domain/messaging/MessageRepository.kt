package io.github.CMPUT301F26stderr.campsbridge.domain.messaging

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for two-participant reservation conversations and recipient read
 * acknowledgements.
 */
interface MessageRepository {
    /**
     * Observes reservation messages in creation order.
     *
     * @param reservationId Reservation document ID.
     */
    fun observeConversation(reservationId: String): Flow<List<Message>>

    /**
     * Observes readable messages newest first for log inspection.
     */
    fun observeAll(): Flow<List<Message>>

    /**
     * Validates two distinct active reservation participants, then atomically stores the
     * message and queues an alert to the other participant. Returns the message ID.
     *
     * @param reservationId Reservation document ID.
     * @param senderId Account ID sending the message or announcement.
     * @param body Nonblank message or reply text.
     */
    suspend fun send(
        reservationId: String,
        senderId: String,
        body: String,
    ): String

    /**
     * Records the recipient’s first-read time; the sender cannot acknowledge their own
     * message.
     *
     * @param messageId Message document ID.
     * @param readerId Account ID acknowledging the received message.
     */
    suspend fun markRead(
        messageId: String,
        readerId: String,
    )
}
