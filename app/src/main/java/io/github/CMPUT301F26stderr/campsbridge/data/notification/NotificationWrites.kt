package io.github.CMPUT301F26stderr.campsbridge.data.notification

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.cancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType

/**
 * Event and delivery-job writes that participate in the caller’s transaction.
 */
internal object NotificationWrites {
    /**
     * Writes an event and its deduplicated recipient job into the caller’s transaction;
     * performs no push I/O.
     *
     * @param db Firestore client used for queries and coordinated writes.
     * @param tx Caller-owned Firestore transaction; reads must precede writes.
     * @param event Shared event whose content and delivery job are written together.
     * @param recipients Account IDs captured for inbox delivery.
     * @param scope Aggregate owning the durable job and its discovery query.
     */
    fun enqueue(
        db: FirebaseFirestore,
        tx: com.google.firebase.firestore.Transaction,
        event: Notification,
        recipients: List<String>,
        scope: DeliveryScope,
    ) {
        tx.set(
            db.document(FirestorePaths.notification(event.id)),
            NotificationDocument.encode(event),
        )
        tx.set(
            db.document("${scope.path}/${DocumentKeys.id(event.id)}"),
            mapOf("recipientIds" to recipients.distinct()),
        )
    }

    /**
     * Queues a deterministic confirmation/cancellation event for the reservation entrant.
     *
     * @param db Firestore client used for queries and coordinated writes.
     * @param tx Caller-owned Firestore transaction; reads must precede writes.
     * @param reservation Stored stay whose inventory, payment, or event is addressed.
     * @param cancelled Whether to emit cancellation rather than reservation confirmation.
     * @param at Time the removal, cancellation, or event occurred.
     */
    fun reservation(
        db: FirebaseFirestore,
        tx: com.google.firebase.firestore.Transaction,
        reservation: Reservation,
        cancelled: Boolean,
        at: java.time.Instant,
    ) {
        val type =
            if (cancelled) {
                NotificationType.RESERVATION_CANCELLED
            } else {
                NotificationType.RESERVATION_CONFIRMED
            }
        val id = DocumentKeys.compound(type.name, reservation.id)
        enqueue(
            db,
            tx,
            Notification(
                id,
                null,
                type,
                NotificationSubject(SubjectType.RESERVATION, reservation.id),
                null,
                at,
            ),
            listOf(reservation.entrantId),
            scope = DeliveryScope.Reservation(reservation.id),
        )
    }
}
