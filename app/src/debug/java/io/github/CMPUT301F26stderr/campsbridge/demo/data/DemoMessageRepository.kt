/*
 * Stores offline reservation conversations and their recipient Inbox events together.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.messaging.MessageDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.flow.Flow
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths as Paths

/** Offline message persistence shared across demo people.
 * @param store Committed conversations, reservations and their common clock.
 */
class DemoMessageRepository(
    private val store: DemoStore = DemoStore(),
) : MessageRepository {
    /** Reads content without acknowledging it.
     * @param id Saved message identity.
     */
    override suspend fun get(id: String): Message? =
        store.read { get(Paths.message(id), MessageDocument) }

    /** Observes messages for one actual reservation in creation order.
     * @param reservationId Conversation identity.
     */
    override fun observeConversation(reservationId: String): Flow<List<Message>> =
        store.observe {
            list("messages", MessageDocument).filter { it.reservationId == reservationId }.sortedBy { it.createdAt }
        }

    /** Observes the offline message log, newest first. */
    override fun observeAll(): Flow<List<Message>> =
        store.observe {
            list("messages", MessageDocument).sortedByDescending { it.createdAt }
        }

    /** Commits a retained message and alert once; a retry leaves saved history untouched.
     * @param messageId Caller-retained submission identity.
     * @param reservationId Conversation identity.
     * @param senderId Actual reservation participant.
     * @param body Visible message content.
     */
    override suspend fun send(
        messageId: String,
        reservationId: String,
        senderId: String,
        body: String,
    ): String =
        store.change {
            require(body.isNotBlank())
            active(senderId)
            val path = Paths.message(messageId)
            val old = get(path, MessageDocument)
            if (old != null) {
                requireOperation(
                    old.reservationId == reservationId && old.senderId == senderId && old.body == body,
                    CONFLICT,
                    "Message identity has different content",
                )
            } else {
                val participants = participants(reservationId)
                requireOperation(
                    participants.size == 2 && senderId in participants,
                    NOT_ALLOWED,
                    "Not a conversation participant",
                )
                val recipient = participants.single { it != senderId }
                active(recipient)
                val message = Message(messageId, reservationId, senderId, body, store.clock.instant())
                put(path, MessageDocument, message)
                notify(
                    Notification(
                        DocumentKeys.compound(NotificationType.NEW_MESSAGE.name, messageId),
                        senderId,
                        NotificationType.NEW_MESSAGE,
                        NotificationSubject(SubjectType.MESSAGE, messageId),
                        null,
                        message.createdAt,
                    ),
                    listOf(recipient),
                )
            }
            messageId
        }

    /** Records only the recipient's first acknowledgement.
     * @param messageId Received message.
     * @param readerId Acknowledging person.
     */
    override suspend fun markRead(
        messageId: String,
        readerId: String,
    ) {
        store.change {
            active(readerId)
            val path = Paths.message(messageId)
            val message = require(path, MessageDocument)
            requireOperation(
                readerId != message.senderId && readerId in participants(message.reservationId),
                NOT_ALLOWED,
                "Only the recipient can acknowledge",
            )
            if (message.readAt == null) put(path, MessageDocument, message.copy(readAt = store.clock.instant()))
        }
    }

    /** Resolves membership through the booked site's immutable ownership.
     * @param reservationId Saved conversation context.
     */
    private fun DemoDocuments.participants(reservationId: String): Set<String> {
        val reservation = require(Paths.reservation(reservationId), ReservationDocument)
        val site = require(Paths.campsite(reservation.campsiteId), CampsiteDocument)
        return setOf(reservation.entrantId, site.ownerId)
    }
}
