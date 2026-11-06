/*
 * Local event history and read acknowledgements; no delivery or announcement simulation.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.data.notification.InboxReceiptDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceDelivery
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceMember
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceUnavailable
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths as Paths

/** Local event history and read acknowledgements; no delivery or announcement simulation.
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

    /** Supplies fixture sender history without simulating announcements.
     * @param senderId Requested demo sender.
     */
    override fun observeSent(senderId: String): Flow<List<Notification>> =
        store.observe { list("notifications", NotificationDocument).filter { it.senderId == senderId } }

    /** Supplies the populated fixture event log. */
    override fun observeAll(): Flow<List<Notification>> =
        store.observe { list("notifications", NotificationDocument) }

    /** Rejects distributed announcement operations rather than fabricating recipients.
     * @param lotteryId Requested saved lottery.
     * @param ownerId Requested sender.
     * @param announcement User-supplied content.
     */
    override suspend fun announce(
        lotteryId: String,
        ownerId: String,
        announcement: Announcement,
    ): String =
        throw UnsupportedOperationException("Announcements require the Firebase host")

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
