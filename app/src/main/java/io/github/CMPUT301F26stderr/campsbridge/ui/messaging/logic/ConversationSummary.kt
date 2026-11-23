/*
 * Turns a participant's reservations and their saved messages into conversation-list rows.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message

/** One reservation conversation in the participant's list.
 * @param reservation Saved reservation that is the conversation context.
 * @param latest Newest saved message.
 * @param unread The other participant's messages this reader hasn't read yet.
 * @param latestIsMine Whether the reader sent the newest message.
 */
data class ConversationSummary(
    val reservation: Reservation,
    val latest: Message,
    val unread: Int,
    val latestIsMine: Boolean = false,
)

/** Builds rows newest conversation first; reservations without messages are left out.
 * @param reservations Reservations the reader takes part in.
 * @param messages Saved messages keyed by reservation identity.
 * @param readerId Account viewing the list.
 * @return Rows ordered by newest message, then reservation identity.
 */
fun conversationSummaries(
    reservations: List<Reservation>,
    messages: Map<String, List<Message>>,
    readerId: String,
): List<ConversationSummary> =
    reservations
        .mapNotNull { reservation ->
            val saved = messages[reservation.id].orEmpty()
            val latest =
                saved.maxWithOrNull(compareBy<Message> { it.createdAt }.thenBy { it.id }) ?: return@mapNotNull null
            ConversationSummary(
                reservation,
                latest,
                saved.count { it.senderId != readerId && it.readAt == null },
                latest.senderId == readerId,
            )
        }.sortedWith(
            compareByDescending<ConversationSummary> { it.latest.createdAt }.thenBy { it.reservation.id },
        )
