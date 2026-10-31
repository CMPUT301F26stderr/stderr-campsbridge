package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/** Controlled immutable event reads; other operations require a deliberately added consumer.
 * @param initial Initial immutable event fixtures.
 */
class FakeNotificationRepository(
    initial: List<Notification> = emptyList(),
) : NotificationRepository {
    val records = MutableStateFlow(initial)
    val getRequests = mutableListOf<String>()
    val audienceRequests = mutableListOf<String>()
    var getAudienceHandler: (suspend (String) -> NotificationAudience)? = null
    var getHandler: (suspend (String) -> Notification?)? = null
    val receipts = MutableStateFlow<Map<String, List<InboxReceipt>>>(emptyMap())
    val inboxFailure = MutableStateFlow<Exception?>(null)
    val inboxRequests = mutableListOf<String>()
    val inboxReadRequests = mutableListOf<String>()
    var getInboxHandler: (suspend (String) -> List<InboxReceipt>)? = null
    var activeInboxObservers = 0
        private set
    val readRequests = mutableListOf<Pair<String, String>>()
    var markReadHandler: (suspend (String, String) -> Unit)? = null

    /** Captures the exact event identity and returns supplied facts, including confirmed absence.
     * @param id Requested immutable event.
     */
    override suspend fun get(id: String): Notification? {
        getRequests.add(id)
        val handler = getHandler
        return if (handler != null) handler(id) else records.value.firstOrNull { it.id == id }
    }

    /** Requires an explicit server-baseline outcome, independent of observer/cache fixtures.
     * @param accountId Requested account history.
     */
    override suspend fun getInbox(accountId: String): List<InboxReceipt> {
        inboxReadRequests.add(accountId)
        return checkNotNull(getInboxHandler) { "Configure confirmed Inbox history" }(accountId)
    }

    /** Captures explicit audience requests and requires an actual supplied outcome.
     * @param notificationId Requested original event identity.
     */
    override suspend fun getAudience(notificationId: String): NotificationAudience {
        audienceRequests.add(notificationId)
        return checkNotNull(getAudienceHandler) { "Configure recorded audience" }(notificationId)
    }

    /** Observes explicit account fixtures with independent failure and cancellation tracking.
     * @param accountId Requested account.
     */
    override fun observeInbox(accountId: String): Flow<List<InboxReceipt>> =
        flow {
            inboxRequests.add(accountId)
            activeInboxObservers++
            try {
                combine(receipts, inboxFailure) { rows, failure ->
                    failure?.let { throw it }
                    rows[accountId].orEmpty()
                }.collect { emit(it) }
            } finally {
                activeInboxObservers--
            }
        }

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

    /** Captures exact acknowledgement arguments and requires an explicit outcome.
     * @param accountId Requesting account.
     * @param notificationId Target receipt/event identity.
     */
    override suspend fun markRead(
        accountId: String,
        notificationId: String,
    ) {
        readRequests.add(accountId to notificationId)
        checkNotNull(
            markReadHandler,
        ) { "Configure receipt acknowledgement" }(accountId, notificationId)
    }

    /** Requires an explicit test contract before a new operation can simulate behavior. */
    private fun unsupported(): Nothing =
        error("Configure this notification operation before using it")
}
