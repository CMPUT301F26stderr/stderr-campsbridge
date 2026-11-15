/*
 * Local event history, captured announcement audiences and first-read acknowledgements.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.InboxReceiptDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceDelivery
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceMember
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceUnavailable
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths as Paths

/** Local event history and announcements shared across demo people.
 * @param records Supplied immutable events.
 * @param inboxes Initial fixture receipts by recipient identity.
 * @param clock Shared timestamp source for first-read acknowledgement.
 * @param store Shared event and receipt state.
 */
class DemoNotificationRepository(
    records: List<Notification> = emptyList(),
    inboxes: Map<String, List<InboxReceipt>> = emptyMap(),
    private val clock: Clock = Clock.systemUTC(),
    private val store: DemoStore =
        DemoStore(
            buildMap {
                records.forEach { put(Paths.notification(it.id), NotificationDocument.encode(it)) }
                inboxes.forEach { (id, values) ->
                    values.forEach { put(Paths.receipt(id, it.notificationId), InboxReceiptDocument.encode(it)) }
                }
            },
        ),
) : NotificationRepository {
    /** Returns the supplied event, or null when absent.
     * @param id Requested actual event identity.
     */
    override suspend fun get(id: String): Notification? =
        store.read { get(Paths.notification(id), NotificationDocument) }

    /** Returns current local receipts without claiming a Firebase server read.
     * @param accountId Current demo account.
     */
    override suspend fun getInbox(accountId: String): List<InboxReceipt> =
        store.read { list("${Paths.account(accountId)}/inbox", InboxReceiptDocument) }

    /** Assembles audience facts from actual local receipts without modifying them.
     * @param notificationId Requested original event identity.
     */
    override suspend fun getAudience(notificationId: String): NotificationAudience {
        if (get(notificationId) == null) {
            return NotificationAudience.Unavailable(NotificationAudienceUnavailable.EVENT_MISSING)
        }
        val members =
            store.read {
                raw.keys
                    .filter { path ->
                        path.startsWith("accounts/") && path.endsWith("/inbox/$notificationId")
                    }.map { path ->
                        NotificationAudienceMember(
                            path.split('/')[1],
                            NotificationAudienceDelivery.DELIVERED,
                            require(path, InboxReceiptDocument).readAt,
                        )
                    }.sortedBy { it.accountId }
            }
        return if (members.isEmpty()) {
            NotificationAudience.NoRecordedInformation
        } else {
            NotificationAudience
                .Recorded(
                    members,
                )
        }
    }

    /** Observes local read changes without initializing Firebase.
     * @param accountId Current demo account.
     */
    override fun observeInbox(accountId: String): Flow<List<InboxReceipt>> =
        store.observe { list("${Paths.account(accountId)}/inbox", InboxReceiptDocument) }

    /** Observes the actual sender's local events, newest first.
     * @param senderId Requested demo sender.
     */
    override fun observeSent(senderId: String): Flow<List<Notification>> =
        store.observe {
            list(
                "notifications",
                NotificationDocument,
            ).filter { it.senderId == senderId }.sortedByDescending { it.createdAt }
        }

    /** Supplies the populated fixture event log. */
    override fun observeAll(): Flow<List<Notification>> =
        store.observe { list("notifications", NotificationDocument).sortedByDescending { it.createdAt } }

    /** Commits one announcement and its captured receipts; retries do not recapture its audience.
     * @param notificationId Caller-retained submission identity.
     * @param lotteryId Requested saved lottery.
     * @param ownerId Requested sender.
     * @param announcement User-supplied content.
     */
    override suspend fun announce(
        notificationId: String,
        lotteryId: String,
        ownerId: String,
        announcement: Announcement,
    ): String =
        store.change {
            active(ownerId, Role.OWNER)
            val existing = get(Paths.notification(notificationId), NotificationDocument)
            val subject = NotificationSubject(SubjectType.LOTTERY, lotteryId)
            if (existing != null) {
                requireOperation(
                    existing.type == NotificationType.OWNER_ANNOUNCEMENT && existing.senderId == ownerId &&
                        existing.subject == subject && existing.announcement == announcement,
                    CONFLICT,
                    "Announcement identity has different content",
                )
            } else {
                val lottery = require(Paths.lottery(lotteryId), LotteryDocument)
                requireOperation(ownerId in lottery.participantOwnerIds, NOT_ALLOWED, "Not a participating owner")
                val recipients =
                    list("${Paths.lottery(lotteryId)}/entries", LotteryEntryDocument)
                        .filter { it.state != EntryState.WITHDRAWN }
                        .map { it.accountId }
                notify(
                    Notification(
                        notificationId,
                        ownerId,
                        NotificationType.OWNER_ANNOUNCEMENT,
                        subject,
                        announcement,
                        clock.instant(),
                    ),
                    recipients,
                )
            }
            notificationId
        }

    /** Records the first local read once; reset reinstalls the fixture receipts.
     * @param accountId Requested recipient.
     * @param notificationId Requested event.
     */
    override suspend fun markRead(
        accountId: String,
        notificationId: String,
    ) {
        store.change {
            val path = Paths.receipt(accountId, notificationId)
            val receipt = require(path, InboxReceiptDocument)
            put(path, InboxReceiptDocument, receipt.copy(readAt = receipt.readAt ?: clock.instant()))
        }
    }
}
