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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Account-scoped receipt/content assembly with independent read acknowledgements.
 * @param accountId Fixed active account; workspace changes do not change receipt ownership.
 * @param notifications Receipt observation and explicit first-read acknowledgements.
 * @param content Shared immutable event/context assembler.
 * @param sorter Reserved loaded-event ordering; null keeps its dependency explicit.
 * @param countUnread Reserved count over all original receipts; null is never a fabricated zero.
 */
class InboxViewModel(
    val accountId: String,
    private val notifications: NotificationRepository,
    private val content: NotificationContentLoader,
    private val sorter: ((List<InboxItem>) -> List<InboxItem>)? = ::sortInboxItems,
    private val countUnread: ((List<InboxReceipt>) -> Int)? = ::unreadInboxCount,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            InboxUiState(
                orderingPending = sorter == null,
                unreadCountPending = countUnread == null,
            ),
        )
    val state = mutableState.asStateFlow()
    private var source = InboxSourceState.LOADING
    private var hasReceipts = false
    private var receipts = emptyList<InboxReceipt>()
    private var sourceJob: Job? = null
    private var sourceVersion = 0L
    private var nextContentVersion = 0L
    private var closed = false
    private val results = mutableMapOf<String, NotificationContent>()
    private val loading = mutableSetOf<String>()
    private val contentJobs = mutableMapOf<String, Job>()
    private val contentVersions = mutableMapOf<String, Long>()

    private var failedReadItem: InboxItem? = null

    init {
        observeReceipts()
    }

    /** Retries only a failed account-receipt observation. */
    fun retry() {
        if (!closed && source == InboxSourceState.FAILURE) observeReceipts()
    }

    /** Retries failed/missing event or subject context without restarting the account query.
     * @param notificationId Actual retained receipt identity.
     */
    fun retryItem(notificationId: String) {
        if (closed || notificationId in loading || receipt(notificationId) == null) return
        val result = results[notificationId]
        val retryable =
            when (result) {
                NotificationContent.Unavailable, NotificationContent.Failure -> {
                    true
                }

                is NotificationContent.Loaded -> {
                    result.subject ==
                        NotificationSubjectContent.Unavailable ||
                        result.subject == NotificationSubjectContent.Failure
                }

                null -> {
                    false
                }
            }
        if (!retryable) return
        val pending = prepareContent(notificationId)
        publish()
        pending.start()
    }

    /**
     * Installs one account source before starting immediate callbacks; retains known rows on
     * retry.
     */
    private fun observeReceipts() {
        val version = ++sourceVersion
        sourceJob?.cancel()
        source = InboxSourceState.LOADING
        publish()
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    notifications.observeInbox(accountId).collect { rows ->
                        currentCoroutineContext().ensureActive()
                        if (closed || version != sourceVersion) return@collect
                        receipts = rows
                        hasReceipts = true
                        source = InboxSourceState.READY
                        reconcileReceipts()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!closed && version == sourceVersion) {
                        source = InboxSourceState.FAILURE
                        publish()
                    }
                }
            }
        sourceJob = pending
        pending.start()
    }

    /** Retires removed identities and installs all new item jobs before any can emit. */
    private fun reconcileReceipts() {
        val ids = receipts.map { it.notificationId }.toSet()
        for (id in contentVersions.keys.toList()) {
            if (id !in ids) {
                contentVersions.remove(id)
                contentJobs.remove(id)?.cancel()
                results.remove(id)
                loading.remove(id)
            }
        }
        val pending = ids.filter { it !in contentVersions }.map { prepareContent(it) }
        publish()
        pending.forEach { it.start() }
    }

    /** Prepares one scoped event/context read while retaining previously known event facts.
     * @param id Actual current receipt identity.
     * @return Installed lazy job; the caller starts it after publishing coherent loading state.
     */
    private fun prepareContent(id: String): Job {
        val version = ++nextContentVersion
        contentVersions[id] = version
        contentJobs[id]?.cancel()
        loading.add(id)
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val result = content.load(id)
                currentCoroutineContext().ensureActive()
                if (closed || contentVersions[id] != version) return@launch
                results[id] = result
                loading.remove(id)
                publish()
            }
        contentJobs[id] = pending
        return pending
    }

    /** Resolves receipt identity from the last confirmed source without creating a read timestamp.
     * @param id Exact event/receipt identity.
     */
    private fun receipt(id: String): InboxReceipt? =
        receipts.firstOrNull { it.notificationId == id }

    /**
     * Projects known events separately from unknown-time receipts; helpers own only their
     * leaves.
     */
    private fun publish() {
        if (closed) return
        val items =
            receipts.mapNotNull { receipt ->
                val content = results[receipt.notificationId] as? NotificationContent.Loaded
                content?.let {
                    InboxItem(
                        it.notification,
                        receipt.readAt,
                        it.subject,
                        receipt.notificationId in loading,
                    )
                }
            }
        val unavailable =
            receipts
                .mapNotNull { receipt ->
                    val id = receipt.notificationId
                    val content = results[id]
                    if (content is NotificationContent.Loaded) {
                        null
                    } else {
                        UnavailableInboxReceipt(
                            receipt,
                            when {
                                id in loading || content == null -> InboxEventStatus.LOADING
                                content == NotificationContent.Failure -> InboxEventStatus.FAILURE
                                else -> InboxEventStatus.UNAVAILABLE
                            },
                        )
                    }
                }.sortedBy { it.receipt.notificationId }
        mutableState.value =
            mutableState.value.copy(
                source = source,
                items = if (hasReceipts) sorter?.invoke(items) ?: items else emptyList(),
                unavailable = unavailable,
                unreadCount = if (hasReceipts) countUnread?.invoke(receipts) else null,
                orderingPending = sorter == null,
                unreadCountPending = countUnread == null,
            )
    }

    /** Retires account work and pending navigation before cancelling noncooperative callbacks. */
    override fun onCleared() {
        closed = true
        sourceVersion++
        contentVersions.clear()
        sourceJob?.cancel()
        contentJobs.values.forEach { it.cancel() }
        super.onCleared()
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
