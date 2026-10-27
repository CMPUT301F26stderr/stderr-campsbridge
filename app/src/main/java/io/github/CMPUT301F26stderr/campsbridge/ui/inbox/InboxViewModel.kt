package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
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
 * @param loader Shared immutable event/context assembler.
 * @param sortItems Reserved loaded-event ordering; null keeps its dependency explicit.
 * @param countUnread Reserved count over all original receipts; null is never a fabricated zero.
 */
class InboxViewModel(
    private val accountId: String,
    private val notifications: NotificationRepository,
    private val loader: NotificationContentLoader,
    private val sortItems: ((List<InboxItem>) -> List<InboxItem>)? = null,
    private val countUnread: ((List<InboxReceipt>) -> Int)? = null,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            InboxUiState(
                orderingPending = sortItems == null,
                unreadCountPending = countUnread == null,
            ),
        )
    val state = mutableState.asStateFlow()
    private val mutableOpen = MutableStateFlow<InboxOpenRequest?>(null)
    val pendingOpen = mutableOpen.asStateFlow()
    private var source = InboxSourceState.LOADING
    private var hasReceipts = false
    private var receipts = emptyList<InboxReceipt>()
    private var sourceJob: Job? = null
    private var sourceVersion = 0L
    private var nextContentVersion = 0L
    private var nextReadVersion = 0L
    private var closed = false
    private val results = mutableMapOf<String, NotificationContent>()
    private val loading = mutableSetOf<String>()
    private val contentJobs = mutableMapOf<String, Job>()
    private val contentVersions = mutableMapOf<String, Long>()
    private val readJobs = mutableMapOf<String, Job>()
    private val readVersions = mutableMapOf<String, Long>()
    private val readFailures = mutableSetOf<String>()

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

    /** Opens supported loaded context and starts acknowledgement independently.
     * @param notificationId Actual selected event identity.
     */
    fun openItem(notificationId: String) {
        if (closed || mutableOpen.value != null || notificationId in loading) return
        val receipt = receipt(notificationId) ?: return
        val loaded = results[notificationId] as? NotificationContent.Loaded ?: return
        if (loaded.subject !is NotificationSubjectContent.LotteryContent &&
            loaded.subject !is NotificationSubjectContent.ReservationContent
        ) {
            return
        }
        mutableOpen.value = InboxOpenRequest(notificationId, loaded.notification.subject)
        if (receipt.readAt == null) startReadAcknowledgement(notificationId)
    }

    /** Consumes only the matching local navigation request.
     * @param notificationId Event whose route request was delivered.
     */
    fun acknowledgeOpen(notificationId: String) {
        if (!closed && mutableOpen.value?.notificationId == notificationId) mutableOpen.value = null
    }

    /** Explicitly retries a failed first-read acknowledgement without opening any destination.
     * @param notificationId Retained failed receipt identity.
     */
    fun retryReadAcknowledgement(notificationId: String) {
        if (!closed && notificationId in readFailures) startReadAcknowledgement(notificationId)
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
                readVersions.remove(id)
                readJobs.remove(id)?.cancel()
                readFailures.remove(id)
            }
        }
        if (mutableOpen.value?.notificationId?.let { it !in ids } == true) mutableOpen.value = null
        readFailures.removeAll { receipt(it)?.readAt != null }
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
                val result = loader.load(id)
                currentCoroutineContext().ensureActive()
                if (closed || contentVersions[id] != version) return@launch
                results[id] = result
                loading.remove(id)
                publish()
            }
        contentJobs[id] = pending
        return pending
    }

    /** Claims only the account/receipt acknowledgement; navigation has an independent lifetime.
     * @param id Actual current unread receipt.
     */
    private fun startReadAcknowledgement(id: String) {
        val current = receipt(id) ?: return
        if (current.readAt != null || readJobs[id]?.isActive == true) return
        val version = ++nextReadVersion
        readVersions[id] = version
        readFailures.remove(id)
        publish()
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    notifications.markRead(accountId, id)
                    currentCoroutineContext().ensureActive()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!closed && readVersions[id] == version && receipt(id)?.readAt == null &&
                        receipt(id) != null
                    ) {
                        readFailures.add(id)
                    }
                } finally {
                    if (!closed && readVersions[id] == version) {
                        readJobs.remove(id)
                        publish()
                    }
                }
            }
        readJobs[id] = pending
        pending.start()
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
            InboxUiState(
                source,
                if (hasReceipts) sortItems?.invoke(items) ?: items else emptyList(),
                unavailable,
                if (hasReceipts) countUnread?.invoke(receipts) else null,
                orderingPending = sortItems == null,
                unreadCountPending = countUnread == null,
                readFailures = readFailures.toSet(),
            )
    }

    /** Retires account work and pending navigation before cancelling noncooperative callbacks. */
    override fun onCleared() {
        closed = true
        sourceVersion++
        contentVersions.clear()
        readVersions.clear()
        sourceJob?.cancel()
        contentJobs.values.forEach { it.cancel() }
        readJobs.values.forEach { it.cancel() }
        mutableOpen.value = null
        super.onCleared()
    }
}
