/*
 * Persistence contract for two-participant reservation conversations and recipient read acknowledgements.
 */

package io.github.CMPUT301F26stderr.campsbridge.domain.messaging

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for two-participant reservation conversations and recipient read
 * acknowledgements.
 */
interface MessageRepository {
    /** Reads saved content without acknowledging it; confirmed absence returns null.
     * @param id Retained message identity.
     */
    suspend fun get(id: String): Message?

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
     * Validates two distinct active participants and commits the message and alert together.
     * Matching retained-ID retries preserve saved history; changed content conflicts.
     *
     * @param messageId Caller-retained submission identity.
     * @param reservationId Reservation document ID.
     * @param senderId Account ID sending the message or announcement.
     * @param body Nonblank message or reply text.
     */
    suspend fun send(
        messageId: String,
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
