package io.github.CMPUT301F26stderr.campsbridge.data.notification

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentDecodingException
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import kotlinx.coroutines.tasks.await

/** Aggregate identity that owns a queue; it is derived from the job's document path. */
internal sealed interface DeliveryScope {
    val path: String

    /** A lottery's outcome and announcement jobs.
     * @param id Lottery identity.
     */
    data class Lottery(
        val id: String,
    ) : DeliveryScope {
        override val path: String = "${FirestorePaths.lottery(id)}/_notificationDeliveries"
    }

    /** A stay's confirmation, cancellation and conversation jobs.
     * @param id Reservation identity.
     */
    data class Reservation(
        val id: String,
    ) : DeliveryScope {
        override val path: String = "${FirestorePaths.reservation(id)}/_notificationDeliveries"
    }
}

/** One durable fan-out identity, without duplicating event content or parent state.
 * @param scope Parent aggregate authorizing discovery and processing.
 * @param id Shared notification identity.
 */
internal data class DeliveryJob(
    val scope: DeliveryScope,
    val id: String,
)

/**
 * Discovers scoped jobs and resumes idempotent inbox fan-out. FCM remains separate.
 * @param db Authenticated client used for server reads and receipt transactions.
 */
internal class NotificationDeliveries(
    private val db: FirebaseFirestore,
) {
    /** Observes all jobs under a permitted parent, including random announcement/message IDs.
     * @param scope Lottery or reservation whose jobs are being discovered.
     */
    fun observe(scope: DeliveryScope) =
        db.collection(scope.path).observe { snapshot ->
            Fields(snapshot.reference.path, snapshot.data!!).strings("recipientIds")
            DeliveryJob(scope, snapshot.id)
        }

    /** Processes a known aggregate; failed fan-out remains durable and propagates.
     * @param scope Parent whose current jobs should be resumed.
     */
    suspend fun resume(scope: DeliveryScope) {
        val jobs = db.collection(scope.path).get(Source.SERVER).await()
        for (job in jobs.documents) deliver(DeliveryJob(scope, job.id))
    }

    /** Explicit administrative scan, never used by an ordinary session. */
    suspend fun resumeAll() {
        val jobs = db.collectionGroup("_notificationDeliveries").get(Source.SERVER).await()
        for (job in jobs.documents) deliver(jobFromSnapshot(job))
    }

    /**
     * Preserves existing read state and deletes work only after every receipt succeeds.
     * Undrawn outcomes stay queued; cancelled, unfinished outcomes are discarded.
     * @param job Durable identity; missing jobs are already complete.
     */
    suspend fun deliver(job: DeliveryJob) {
        val ref = db.document("${job.scope.path}/${DocumentKeys.id(job.id)}")
        val snapshot = ref.get(Source.SERVER).await()
        if (!snapshot.exists()) return
        val recipients = Fields(ref.path, snapshot.data!!).strings("recipientIds")
        val event =
            db.document(FirestorePaths.notification(job.id)).read(NotificationDocument)
                ?: throw DocumentDecodingException(ref.path, "Missing notification event")
        if (event.type in setOf(NotificationType.LOTTERY_WON, NotificationType.LOTTERY_LOST)) {
            val lottery =
                db
                    .document(FirestorePaths.lottery(event.subject.id))
                    .read(LotteryDocument)
                    ?: throw DocumentDecodingException(ref.path, "Missing lottery")
            if (lottery.drawnAt == null) {
                if (lottery.cancellation != null) ref.delete().await()
                return
            }
        }
        for (recipient in recipients) {
            val receiptRef = db.document(FirestorePaths.receipt(recipient, job.id))
            db.atomic { tx ->
                if (tx.read(receiptRef, InboxReceiptDocument) == null) {
                    tx.set(receiptRef, InboxReceiptDocument.encode(InboxReceipt(job.id, null)))
                }
            }
        }
        ref.delete().await()
    }
}

/** Decodes a group-query job's scope from its canonical path.
 * @param snapshot Snapshot from the scoped delivery collection group.
 */
private fun jobFromSnapshot(snapshot: DocumentSnapshot): DeliveryJob {
    val parent =
        snapshot.reference.parent.parent
            ?: throw DocumentDecodingException(snapshot.reference.path, "Missing job parent")
    val scope =
        when (parent.parent.path) {
            "lotteries" -> DeliveryScope.Lottery(parent.id)
            "reservations" -> DeliveryScope.Reservation(parent.id)
            else -> throw DocumentDecodingException(snapshot.reference.path, "Invalid job parent")
        }
    return DeliveryJob(scope, snapshot.id)
}
