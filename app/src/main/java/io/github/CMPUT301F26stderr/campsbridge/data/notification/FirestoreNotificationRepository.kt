package io.github.CMPUT301F26stderr.campsbridge.data.notification

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.flow.map
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

    /** Reads one account's confirmed server history without changing receipts or event content.
     * @param accountId Saved receipt owner.
     */
    override suspend fun getInbox(accountId: String): List<InboxReceipt> =
        db.collection("${FirestorePaths.account(accountId)}/inbox").read(InboxReceiptDocument)

    /** Reads existing intended/receipt facts without invoking fan-out or acknowledgements.
     * @param notificationId Original immutable event identity.
     */
    override suspend fun getAudience(notificationId: String): NotificationAudience =
        NotificationAudienceQueries(db).get(notificationId)

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
                scope = DeliveryScope.Lottery(lotteryId),
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
}
