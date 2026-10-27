package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Fixed Inbox events with local receipt updates for inspecting the real unread helper. */
internal class TaskNotificationRepository : NotificationRepository by DemoNotificationRepository() {
    private val receipts = MutableStateFlow(TaskExampleData.receipts)

    /** Reads an immutable event by identity.
     * @param id Requested event.
     */
    override suspend fun get(id: String): Notification? =
        TaskExampleData.events.find { it.id == id }

    /** Observes only the sample recipient's receipts.
     * @param accountId Requested recipient.
     */
    override fun observeInbox(accountId: String): Flow<List<InboxReceipt>> =
        receipts.map {
            if (accountId ==
                TaskExampleData.ACCOUNT_ID
            ) {
                it
            } else {
                emptyList()
            }
        }

    /** Marks the selected local receipt read, independently of navigation.
     * @param accountId Current sample recipient.
     * @param notificationId Opened event identity.
     */
    override suspend fun markRead(
        accountId: String,
        notificationId: String,
    ) {
        require(accountId == TaskExampleData.ACCOUNT_ID)
        receipts.value =
            receipts.value.map {
                if (it.notificationId == notificationId &&
                    it.readAt == null
                ) {
                    it.copy(readAt = TaskExampleData.now)
                } else {
                    it
                }
            }
    }
}
