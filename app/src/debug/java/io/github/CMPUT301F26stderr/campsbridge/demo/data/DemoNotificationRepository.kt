package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Offline history is explicitly empty; distributed delivery and receipt writes are unsupported. */
class DemoNotificationRepository : NotificationRepository {
    /** Confirms absence in the empty offline store.
     * @param id Requested actual event identity.
     */
    override suspend fun get(id: String): Notification? =
        null

    /** Supplies empty local history without initializing Firebase.
     * @param accountId Current demo account.
     */
    override fun observeInbox(accountId: String): Flow<List<InboxReceipt>> =
        flowOf(emptyList())

    /** Supplies empty sender history without simulating announcements.
     * @param senderId Requested demo sender.
     */
    override fun observeSent(senderId: String): Flow<List<Notification>> =
        flowOf(emptyList())

    /** Supplies the empty offline event log. */
    override fun observeAll(): Flow<List<Notification>> =
        flowOf(emptyList())

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

    /** Rejects writes against the empty offline receipt store.
     * @param accountId Requested recipient.
     * @param notificationId Requested event.
     */
    override suspend fun markRead(
        accountId: String,
        notificationId: String,
    ): Unit =
        throw UnsupportedOperationException("Receipt writes require the Firebase host")
}
