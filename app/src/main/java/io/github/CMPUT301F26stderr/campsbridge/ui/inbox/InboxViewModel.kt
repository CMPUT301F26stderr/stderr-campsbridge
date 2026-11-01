package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.logic.sortInboxItems
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.logic.unreadInboxCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class InboxViewModel(
    private val accountId: String,
    private val notifications: NotificationRepository,
    private val content: NotificationContentLoader,
    private val sorter: (List<InboxItem>) -> List<InboxItem> = ::sortInboxItems,
) : ViewModel() {
    private val mutableState = MutableStateFlow<InboxUiState>(InboxUiState.Loading)
    val state = mutableState.asStateFlow()
    private var observation: Job? = null

    init {
        retry()
    }

    fun retry() {
        observation?.cancel()
        mutableState.value = InboxUiState.Loading
        observation = viewModelScope.launch {
            try {
                notifications.observeInbox(accountId).collect { receipts ->
                    val unread = unreadInboxCount(receipts)
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
                                        )
                                    )
                                }
                                NotificationContent.Unavailable -> {
                                    unavailable.add(
                                        UnavailableInboxReceipt(receipt, InboxEventStatus.UNAVAILABLE)
                                    )
                                }
                                NotificationContent.Failure -> {
                                    unavailable.add(
                                        UnavailableInboxReceipt(receipt, InboxEventStatus.FAILURE)
                                    )
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            unavailable.add(
                                UnavailableInboxReceipt(receipt, InboxEventStatus.FAILURE)
                            )
                        }
                    }

                    mutableState.value = InboxUiState.Content(
                        items = sorter(items),
                        unavailable = unavailable,
                        unreadCount = unread,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = InboxUiState.Failure
            }
        }
    }

    fun open(item: InboxItem) {
        val current = state.value as? InboxUiState.Content ?: return
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
                val latest = state.value as? InboxUiState.Content
                if (latest != null) {
                    mutableState.value = latest.copy(saveFailed = true, operation = InboxOperation.IDLE)
                }
                return@launch
            }
            val latest = state.value as? InboxUiState.Content
            if (latest != null) {
                mutableState.value = latest.copy(operation = InboxOperation.IDLE, saveFailed = false)
            }
        }
    }

    fun consumeOpen() {
        val current = state.value as? InboxUiState.Content ?: return
        if (current.openingItem != null) {
            mutableState.value = current.copy(openingItem = null)
        }
    }
}
