package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceDelivery
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceMember
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceUnavailable
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock

/** Local event history and read acknowledgements; no delivery or announcement simulation.
 * @param records Supplied immutable events.
 * @param inboxes Initial fixture receipts by recipient identity.
 * @param clock Fixed fixture time for local first-read acknowledgement.
 */
class DemoNotificationRepository(
    private val records: List<Notification> = emptyList(),
    inboxes: Map<String, List<InboxReceipt>> = emptyMap(),
    private val clock: Clock = Clock.systemUTC(),
) : NotificationRepository {
    private val receipts = MutableStateFlow(inboxes)

    /** Returns the supplied event, or null when absent.
     * @param id Requested actual event identity.
     */
    override suspend fun get(id: String): Notification? =
        records.firstOrNull { it.id == id }

    /** Returns current local receipts without claiming a Firebase server read.
     * @param accountId Current demo account.
     */
    override suspend fun getInbox(accountId: String): List<InboxReceipt> =
        receipts.value[accountId].orEmpty()

    /** Assembles audience facts from actual local receipts without modifying them.
     * @param notificationId Requested original event identity.
     */
    override suspend fun getAudience(notificationId: String): NotificationAudience {
        if (records.none { it.id == notificationId }) {
            return NotificationAudience.Unavailable(NotificationAudienceUnavailable.EVENT_MISSING)
        }
        val members =
            receipts.value
                .mapNotNull { (accountId, inbox) ->
                    inbox.firstOrNull { it.notificationId == notificationId }?.let {
                        NotificationAudienceMember(
                            accountId,
                            NotificationAudienceDelivery.DELIVERED,
                            it.readAt,
                        )
                    }
                }.sortedBy { it.accountId }
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
        receipts.map { it[accountId].orEmpty() }

    /** Supplies fixture sender history without simulating announcements.
     * @param senderId Requested demo sender.
     */
    override fun observeSent(senderId: String): Flow<List<Notification>> =
        flowOf(records.filter { it.senderId == senderId })

    /** Supplies the populated fixture event log. */
    override fun observeAll(): Flow<List<Notification>> =
        flowOf(records)

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
        require(receipts.value[accountId].orEmpty().any { it.notificationId == notificationId })
        receipts.value = receipts.value + (
            accountId to
                receipts.value.getValue(accountId).map {
                    if (it.notificationId == notificationId &&
                        it.readAt == null
                    ) {
                        it.copy(readAt = clock.instant())
                    } else {
                        it
                    }
                }
        )
    }
}
