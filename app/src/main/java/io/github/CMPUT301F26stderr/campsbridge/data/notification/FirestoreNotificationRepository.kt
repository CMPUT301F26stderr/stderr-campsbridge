package io.github.CMPUT301F26stderr.campsbridge.data.notification

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.cancellation
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Clock

/**
 * Durable database inbox delivery. OS/FCM push transport is a separate integration.
 *
 * @param db Firestore client used for queries and coordinated writes.
 * @param clock Injectable time source for recorded timestamps and deadline checks.
 */
class FirestoreNotificationRepository(
    private val db: FirebaseFirestore,
    private val clock: Clock,
) : NotificationRepository {
    /**
     * Returns a shared notification event, or null when absent.
     *
     * @param id Notification event document ID.
     */
    override suspend fun get(id: String) =
        db.document(FirestorePaths.notification(id)).read(NotificationDocument)

    /**
     * Observes account-specific inbox receipts; shared event content is fetched separately.
     *
     * @param accountId Account ID whose participation or data is addressed.
     */
    override fun observeInbox(accountId: String) =
        db.collection("${FirestorePaths.account(accountId)}/inbox").observe(InboxReceiptDocument)

    /**
     * Observes the sender’s events, newest first.
     *
     * @param senderId Account ID sending the message or announcement.
     */
    override fun observeSent(senderId: String) =
        db
            .collection("notifications")
            .whereEqualTo("senderId", senderId)
            .observe(NotificationDocument)
            .map { it.sortedByDescending { event -> event.createdAt } }

    /**
     * Observes readable notification events, newest first, for log inspection.
     */
    override fun observeAll() =
        db.collection("notifications").observe(NotificationDocument).map {
            it.sortedByDescending { event -> event.createdAt }
        }

    /**
     * Captures joined, nonwithdrawn entrants, including draw outcomes, and queues an owner
     * announcement. Returns the event ID; inbox fan-out resumes separately.
     *
     * @param lotteryId Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param announcement Owner-supplied announcement content.
     */
    override suspend fun announce(
        lotteryId: String,
        ownerId: String,
        announcement: Announcement,
    ): String {
        val recipients =
            db
                .collection("${FirestorePaths.lottery(lotteryId)}/entries")
                .read(LotteryEntryDocument)
                .filter { it.state != EntryState.WITHDRAWN }
                .map { it.accountId }
        val id = db.collection("notifications").document().id
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val lottery =
                tx.require(db.document(FirestorePaths.lottery(lotteryId)), LotteryDocument)
            requireOperation(lottery.ownerId == ownerId, NOT_ALLOWED, "Different lottery owner")
            NotificationWrites.enqueue(
                db,
                tx,
                Notification(
                    id,
                    ownerId,
                    NotificationType.OWNER_ANNOUNCEMENT,
                    NotificationSubject(SubjectType.LOTTERY, lotteryId),
                    announcement,
                    clock.instant(),
                ),
                recipients,
            )
        }
        return id
    }

    /**
     * Sets an inbox receipt’s first-read time without replacing an existing acknowledgement.
     *
     * @param accountId Account ID whose participation or data is addressed.
     * @param notificationId Shared notification event ID.
     */
    override suspend fun markRead(
        accountId: String,
        notificationId: String,
    ) {
        val ref = db.document(FirestorePaths.receipt(accountId, notificationId))
        db.atomic { tx ->
            val receipt = tx.require(ref, InboxReceiptDocument)
            if (receipt.readAt == null) tx.update(ref, "readAt", clock.instant().stored())
        }
    }

    /**
     * Resumes receipt fan-out without resetting read times. Draw-result jobs wait for
     * completion; failed jobs remain retryable. This does not send OS/FCM push messages.
     */
    override suspend fun resumeDeliveries() {
        val jobs = db.collection("_notificationDeliveries").get(Source.SERVER).await()
        for (job in jobs.documents) {
            val fields = Fields(job.reference.path, job.data!!)
            val lotteryId = fields.optionalString("waitForDraw")
            if (lotteryId != null) {
                val lottery =
                    db.document(FirestorePaths.lottery(lotteryId)).read(LotteryDocument) ?: continue
                if (lottery.cancellation != null && lottery.drawnAt == null) {
                    job.reference.delete().await()
                    continue
                }
                if (lottery.drawnAt == null) continue
            }
            for (recipient in fields.strings("recipientIds")) {
                val ref = db.document(FirestorePaths.receipt(recipient, job.id))
                db.atomic { tx ->
                    val receipt = tx.read(ref, InboxReceiptDocument)
                    if (receipt == null) {
                        tx.set(ref, InboxReceiptDocument.encode(InboxReceipt(job.id, null)))
                    }
                }
            }
            job.reference.delete().await()
        }
    }
}

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
     * @param waitForDraw Lottery whose completed draw gates receipt delivery, or null for
     * immediate delivery.
     */
    fun enqueue(
        db: FirebaseFirestore,
        tx: com.google.firebase.firestore.Transaction,
        event: Notification,
        recipients: List<String>,
        waitForDraw: String? = null,
    ) {
        tx.set(
            db.document(FirestorePaths.notification(event.id)),
            NotificationDocument.encode(event),
        )
        tx.set(
            db.collection("_notificationDeliveries").document(event.id),
            mapOf("recipientIds" to recipients.distinct(), "waitForDraw" to waitForDraw),
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
        )
    }
}
