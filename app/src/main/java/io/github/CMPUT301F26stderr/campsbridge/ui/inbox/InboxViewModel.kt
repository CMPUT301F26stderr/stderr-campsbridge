package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.logic.sortInboxItems
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.logic.unreadInboxCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Observes one account's Inbox and handles opening rows.
 * @param accountId Recipient account.
 * @param notifications Source of receipts and read acknowledgements.
 * @param content Loads each receipt's event and subject.
 * @param sorter Orders loaded rows; null leaves the helper pending.
 * @param countUnread Counts original unread receipts; null leaves the helper pending.
 */
class InboxViewModel(
    val accountId: String,
    private val notifications: NotificationRepository,
    private val content: NotificationContentLoader,
    private val sorter: ((List<InboxItem>) -> List<InboxItem>)? = ::sortInboxItems,
    private val countUnread: (
        (
            List<InboxReceipt>,
        ) -> Int
    )? = ::unreadInboxCount,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            InboxUiState(
                orderingPending = sorter == null,
                unreadCountPending =
                    countUnread == null,
            ),
        )
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private var failedReadItem: InboxItem? = null

    init {
        retry()
    }

    /** Restarts the live source, keeping any rows that already loaded. */
    fun retry() {
        observation?.cancel()
        val known = mutableState.value
        mutableState.value = known.copy(source = InboxSourceState.LOADING)
        observation =
            viewModelScope.launch {
                try {
                    notifications.observeInbox(accountId).collectLatest { receipts ->
                        val unread = countUnread?.invoke(receipts)
                        val items = mutableListOf<InboxItem>()
                        val unavailable = mutableListOf<UnavailableInboxReceipt>()

                        for (receipt in receipts) {
                            try {
                                val loaded = content.load(receipt.notificationId)
                                currentCoroutineContext().ensureActive()
                                when (loaded) {
                                    is NotificationContent.Loaded -> {
                                        items.add(
                                            InboxItem(
                                                notification = loaded.notification,
                                                readAt = receipt.readAt,
                                                subject = loaded.subject,
                                            ),
                                        )
                                    }

                                    NotificationContent.Unavailable -> {
                                        unavailable.add(
                                            UnavailableInboxReceipt(
                                                receipt,
                                                InboxEventStatus.UNAVAILABLE,
                                            ),
                                        )
                                    }

                                    NotificationContent.Failure -> {
                                        unavailable.add(
                                            UnavailableInboxReceipt(
                                                receipt,
                                                InboxEventStatus.FAILURE,
                                            ),
                                        )
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                unavailable.add(
                                    UnavailableInboxReceipt(receipt, InboxEventStatus.FAILURE),
                                )
                            }
                        }

                        // A new snapshot mustn't lose a pending open or a failed read.
                        val previous = mutableState.value
                        mutableState.value =
                            previous.copy(
                                items = sorter?.invoke(items) ?: items,
                                unavailable = unavailable,
                                unreadCount = unread,
                                source = InboxSourceState.READY,
                            )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    val known = mutableState.value
                    mutableState.value = known.copy(source = InboxSourceState.FAILURE)
                }
            }
    }

    /** Retries content through the existing source until individual reads are installed.
     * @param notificationId Retained event identity.
     */
    fun retryItem(notificationId: String) {
        if (state.value.items.any { it.notification.id == notificationId } ||
            state.value.unavailable.any { it.receipt.notificationId == notificationId }
        ) {
            retry()
        }
    }

    /** Selects a current supported row, ignoring unresolved context.
     * @param notificationId Retained event identity.
     */
    fun openItem(notificationId: String) {
        val item = state.value.items.firstOrNull { it.notification.id == notificationId } ?: return
        if (item.isLoading ||
            (
                item.subject !is NotificationSubjectContent.LotteryContent &&
                    item.subject !is NotificationSubjectContent.ReservationContent
            )
        ) {
            return
        }
        open(item)
    }

    /** Requests navigation and marks the row read, ignoring taps while one is pending.
     * @param item Row the user tapped.
     */
    fun open(item: InboxItem) {
        val current = state.value
        if (current.openingItem != null || current.operation != InboxOperation.IDLE) return
        mutableState.value = current.copy(openingItem = item, operation = InboxOperation.OPENING)
        viewModelScope.launch {
            try {
                if (item.readAt == null) {
                    notifications.markRead(accountId, item.notification.id)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                failedReadItem = item
                val latest = state.value
                if (latest != null) {
                    mutableState.value =
                        latest.copy(saveFailed = true, operation = InboxOperation.IDLE)
                }
                return@launch
            }
            failedReadItem = null
            val latest = state.value
            if (latest != null) {
                mutableState.value =
                    latest.copy(operation = InboxOperation.IDLE, saveFailed = false)
            }
        }
    }

    /** Retries the last failed read acknowledgement, ignoring taps while an action is pending. */
    fun retryMarkRead() {
        val item = failedReadItem ?: return
        val current = state.value
        if (current.operation != InboxOperation.IDLE) return
        mutableState.value = current.copy(operation = InboxOperation.MARKING_READ)
        viewModelScope.launch {
            try {
                notifications.markRead(accountId, item.notification.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                val latest = state.value
                if (latest != null) {
                    mutableState.value =
                        latest.copy(saveFailed = true, operation = InboxOperation.IDLE)
                }
                return@launch
            }
            failedReadItem = null
            val latest = state.value
            if (latest != null) {
                mutableState.value =
                    latest.copy(operation = InboxOperation.IDLE, saveFailed = false)
            }
        }
    }

    /** Clears the open request once the route has navigated. */
    fun consumeOpen() {
        val current = state.value
        if (current.openingItem != null) {
            mutableState.value = current.copy(openingItem = null)
        }
    }
}
