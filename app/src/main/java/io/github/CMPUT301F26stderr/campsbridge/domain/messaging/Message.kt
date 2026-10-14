package io.github.CMPUT301F26stderr.campsbridge.domain.messaging

import java.time.Instant

/**
 * Immutable reservation-conversation text with the recipient’s optional first-read time.
 *
 * @param id Message document ID.
 * @param reservationId Reservation document ID.
 * @param senderId Account ID sending the message or announcement.
 * @param body Nonblank message or reply text.
 * @param createdAt Original record creation time.
 * @param readAt First-read acknowledgement time, or null if unread.
 */
data class Message(
    val id: String,
    val reservationId: String,
    val senderId: String,
    val body: String,
    val createdAt: Instant,
    val readAt: Instant? = null,
) {
    init {
        require(body.isNotBlank())
    }
}
