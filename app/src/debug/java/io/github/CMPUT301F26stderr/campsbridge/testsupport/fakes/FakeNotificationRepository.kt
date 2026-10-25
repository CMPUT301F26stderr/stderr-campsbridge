package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Controlled immutable event reads; other operations require a deliberately added consumer.
 * @param initial Initial immutable event fixtures.
 */
class FakeNotificationRepository(
    initial: List<Notification> = emptyList(),
) : NotificationRepository {
    val records = MutableStateFlow(initial)
    val getRequests = mutableListOf<String>()
    var getHandler: (suspend (String) -> Notification?)? = null

    /** Captures the exact event identity and returns supplied facts, including confirmed absence.
     * @param id Requested immutable event.
     */
    override suspend fun get(id: String): Notification? {
        getRequests.add(id)
        val handler = getHandler
        return if (handler != null) handler(id) else records.value.firstOrNull { it.id == id }
    }

    /** Rejects an unconfigured account-receipt source.
     * @param accountId Requested account.
     */
    override fun observeInbox(accountId: String): Flow<List<InboxReceipt>> =
        unsupported()

    /** Rejects an unconfigured sender history source.
     * @param senderId Requested sender.
     */
    override fun observeSent(senderId: String): Flow<List<Notification>> =
        unsupported()

    /** Rejects an unconfigured admin log source. */
    override fun observeAll(): Flow<List<Notification>> =
        unsupported()

    /** Rejects an unconfigured announcement mutation.
     * @param lotteryId Target lottery.
     * @param ownerId Requesting owner.
     * @param announcement Supplied content.
     */
    override suspend fun announce(
        lotteryId: String,
        ownerId: String,
        announcement: Announcement,
    ): String =
        unsupported()

    /** Rejects an unconfigured receipt acknowledgement.
     * @param accountId Requesting account.
     * @param notificationId Target receipt/event identity.
     */
    override suspend fun markRead(
        accountId: String,
        notificationId: String,
    ): Unit =
        unsupported()

    /** Requires an explicit test contract before a new operation can simulate behavior. */
    private fun unsupported(): Nothing =
        error("Configure this notification operation before using it")
}
