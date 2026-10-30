package io.github.CMPUT301F26stderr.campsbridge.data.notification

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceDelivery
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceMember
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceUnavailable
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/** Owns server-only audience assembly within the existing notification data implementation.
 * @param db Authenticated client for existing event, scope, job and account reads.
 * @param receipt Real exact-document IO boundary, injectable for deterministic receipt failures.
 */
internal class NotificationAudienceQueries(
    private val db: FirebaseFirestore,
    private val receipt: suspend (String, String) -> InboxReceipt? = { accountId, eventId ->
        db.document(FirestorePaths.receipt(accountId, eventId)).read(InboxReceiptDocument)
    },
) {
    /** Captures intended IDs before receipt reads and preserves them if delivery removes the job.
     * Required IO/decoding failures propagate, never a successful partial result. This is an
     * assembled read across time, not an atomic database snapshot or proof of OS delivery.
     * @param notificationId Original immutable event identity.
     */
    suspend fun get(notificationId: String): NotificationAudience {
        val event =
            db.document(FirestorePaths.notification(notificationId)).read(NotificationDocument)
                ?: return NotificationAudience.Unavailable(
                    NotificationAudienceUnavailable.EVENT_MISSING,
                )
        val scope =
            when (event.subject.type) {
                SubjectType.LOTTERY -> DeliveryScope.Lottery(event.subject.id)

                SubjectType.RESERVATION -> DeliveryScope.Reservation(event.subject.id)

                SubjectType.MESSAGE -> return NotificationAudience.Unavailable(
                    NotificationAudienceUnavailable.UNSUPPORTED_SUBJECT,
                )
            }
        val parent = db.document(scope.path.substringBeforeLast('/')).get(Source.SERVER).await()
        if (!parent.exists()) {
            return NotificationAudience.Unavailable(NotificationAudienceUnavailable.SUBJECT_MISSING)
        }
        val job =
            db
                .document("${scope.path}/${DocumentKeys.id(notificationId)}")
                .get(Source.SERVER)
                .await()
        val intended = capturedRecipients(job)
        val known =
            db
                .collection("accounts")
                .get(Source.SERVER)
                .await()
                .documents
                .map { it.id }
        val ids = (known + intended.orEmpty()).toSortedSet()
        val members =
            coroutineScope {
                ids.chunked(4).flatMap { batch ->
                    batch
                        .map { accountId ->
                            async {
                                val delivered = receipt(accountId, notificationId)
                                when {
                                    delivered != null -> {
                                        NotificationAudienceMember(
                                            accountId,
                                            NotificationAudienceDelivery.DELIVERED,
                                            delivered.readAt,
                                        )
                                    }

                                    accountId in intended.orEmpty() -> {
                                        NotificationAudienceMember(
                                            accountId,
                                            NotificationAudienceDelivery.PENDING,
                                        )
                                    }

                                    else -> {
                                        null
                                    }
                                }
                            }
                        }.awaitAll()
                        .filterNotNull()
                }
            }
        return if (intended != null || members.isNotEmpty()) {
            NotificationAudience.Recorded(members)
        } else {
            NotificationAudience.NoRecordedInformation
        }
    }

    /** Distinguishes an absent job from a captured empty set, rejecting invalid stored IDs.
     * @param job Confirmed server snapshot of the existing scoped delivery job.
     */
    private fun capturedRecipients(job: DocumentSnapshot): Set<String>? {
        if (!job.exists()) return null
        val fields = Fields(job.reference.path, checkNotNull(job.data))
        return fields
            .strings("recipientIds")
            .map { id ->
                try {
                    DocumentKeys.id(id)
                } catch (_: IllegalArgumentException) {
                    fields.malformed("recipientIds")
                }
            }.toSet()
    }
}
