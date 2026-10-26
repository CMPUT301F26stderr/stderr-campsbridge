package io.github.CMPUT301F26stderr.campsbridge.data.messaging

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.data.notification.DeliveryScope
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationWrites
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.flow.map
import java.time.Clock

/**
 * Firebase-backed implementation for two-participant reservation conversations and recipient
 * read acknowledgements.
 *
 * @param db Firestore client used for queries and coordinated writes.
 * @param clock Injectable time source for recorded timestamps and deadline checks.
 */
class FirestoreMessageRepository(
    private val db: FirebaseFirestore,
    private val clock: Clock,
) : MessageRepository {
    /**
     * Observes reservation messages in creation order.
     *
     * @param reservationId Reservation document ID.
     */
    override fun observeConversation(reservationId: String) =
        db
            .collection("messages")
            .whereEqualTo("reservationId", reservationId)
            .observe(MessageDocument)
            .map { it.sortedBy { message -> message.createdAt } }

    /**
     * Observes readable messages newest first for log inspection.
     */
    override fun observeAll() =
        db.collection("messages").observe(MessageDocument).map {
            it.sortedByDescending { message -> message.createdAt }
        }

    /**
     * Validates two distinct active reservation participants, then atomically stores the
     * message and queues an alert to the other participant. Returns the message ID.
     *
     * @param reservationId Reservation document ID.
     * @param senderId Account ID sending the message or announcement.
     * @param body Nonblank message or reply text.
     */
    override suspend fun send(
        reservationId: String,
        senderId: String,
        body: String,
    ): String {
        require(body.isNotBlank())
        val reference = db.collection("messages").document()
        db.atomic { tx ->
            tx.activeAccount(db, senderId)
            val reservation =
                tx.require(
                    db.document(FirestorePaths.reservation(reservationId)),
                    ReservationDocument,
                )
            val campsite =
                tx.require(
                    db.document(FirestorePaths.campsite(reservation.campsiteId)),
                    CampsiteDocument,
                )
            val participants = setOf(reservation.entrantId, campsite.ownerId)
            requireOperation(
                participants.size == 2 && senderId in participants,
                NOT_ALLOWED,
                "Conversation requires two distinct participants",
            )
            val recipient = participants.single { it != senderId }
            tx.activeAccount(db, recipient)
            val message = Message(reference.id, reservationId, senderId, body, clock.instant())
            tx.set(reference, MessageDocument.encode(message))
            val eventId = DocumentKeys.compound(NotificationType.NEW_MESSAGE.name, message.id)
            NotificationWrites.enqueue(
                db,
                tx,
                Notification(
                    eventId,
                    senderId,
                    NotificationType.NEW_MESSAGE,
                    NotificationSubject(SubjectType.MESSAGE, message.id),
                    null,
                    message.createdAt,
                ),
                listOf(recipient),
                scope = DeliveryScope.Reservation(reservationId),
            )
        }
        return reference.id
    }

    /**
     * Records the recipient’s first-read time; the sender cannot acknowledge their own
     * message.
     *
     * @param messageId Message document ID.
     * @param readerId Account ID acknowledging the received message.
     */
    override suspend fun markRead(
        messageId: String,
        readerId: String,
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, readerId)
            val ref = db.document(FirestorePaths.message(messageId))
            val message = tx.require(ref, MessageDocument)
            val reservation =
                tx.require(
                    db.document(FirestorePaths.reservation(message.reservationId)),
                    ReservationDocument,
                )
            val site =
                tx.require(
                    db.document(FirestorePaths.campsite(reservation.campsiteId)),
                    CampsiteDocument,
                )
            requireOperation(
                readerId != message.senderId &&
                    readerId in setOf(reservation.entrantId, site.ownerId),
                NOT_ALLOWED,
                "Only the recipient can mark this message read",
            )
            if (message.readAt == null) tx.update(ref, "readAt", clock.instant().stored())
        }
    }
}
