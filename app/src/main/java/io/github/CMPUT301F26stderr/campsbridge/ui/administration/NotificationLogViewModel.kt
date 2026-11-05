package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns admin log filters and independent original-event and recipient reads.
 * @param notifications All events and recorded recipients.
 * @param loader Common immutable event/context reader.
 * @param savedState Small retained filters and selection.
 */
class NotificationLogViewModel(
    private val notifications: NotificationRepository,
    private val loader: NotificationContentLoader,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            NotificationLogUiState(
                query = savedState.get<String>("notificationLogQuery") ?: "",
                type =
                    NotificationType.entries.firstOrNull {
                        it.name ==
                            savedState.get<String>(
                                "notificationLogType",
                            )
                    },
            ),
        )
    val state = mutableState.asStateFlow()
    private var events: List<Notification>? = null
    private var failed = false
    private var listJob: Job? = null
    private var listVersion = 0L
    private var closed = false

    private val mutableDetail = MutableStateFlow(NotificationLogDetailUiState())
    val detail = mutableDetail.asStateFlow()
    private var detailJob: Job? = null
    private var detailVersion = 0L
    val audience = MutableStateFlow<NotificationLogAudienceState>(NotificationLogAudienceState.Idle).asStateFlow()

    init {
        observeEvents()
        selectEvent(savedState.get<String>("notificationLogSelection"))
    }

    /** Retains raw query input.
     * @param query User-entered text.
     */
    fun editQuery(query: String) {
        if (closed || query == state.value.query) return
        savedState["notificationLogQuery"] = query
        mutableState.value = state.value.copy(query = query)
        publishList()
    }

    /** Selects a category without restarting the source.
     * @param type Category, or null for all.
     */
    fun selectType(type: NotificationType?) {
        if (closed || type == state.value.type) return
        savedState["notificationLogType"] = type?.name
        mutableState.value = state.value.copy(type = type)
        publishList()
    }

    /** Retries the failed all-event source. */
    fun retry() {
        if (!closed && (failed || (events == null && listJob?.isActive != true))) observeEvents()
    }

    /** Selects the actual navigation identity.
     * @param id Event ID, or null to retire selection.
     */
    fun selectEvent(id: String?) {
        if (closed || id == detail.value.id) return
        detailVersion++
        detailJob?.cancel()
        detailJob = null
        setAudienceVisible(false)
        savedState["notificationLogSelection"] = id
        mutableDetail.value =
            NotificationLogDetailUiState(
                id = id,
                event = events?.firstOrNull { it.id == id },
            )
        if (id != null) loadDetail(id)
    }

    /** Retries only selected-event content. */
    fun retryDetail() {
        val id = detail.value.id ?: return
        if (!closed && detailJob?.isActive != true) loadDetail(id)
    }

    /** Starts or retires the audience page's read.
     * @param visible Whether the selected audience page is displayed.
     */
    fun setAudienceVisible(visible: Boolean) {}

    /** Retries only the current audience query. */
    fun retryAudience() {}

    /** Keeps known immutable facts while the shared reader resolves optional context.
     * @param id Exact currently selected original event identity.
     */
    private fun loadDetail(id: String) {
        val version = ++detailVersion
        detailJob?.cancel()
        mutableDetail.value = detail.value.copy(content = null, isLoading = true)
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val content = loader.load(id)
                    currentCoroutineContext().ensureActive()
                    if (closed || detailVersion != version || detail.value.id != id) return@launch
                    mutableDetail.value =
                        NotificationLogDetailUiState(
                            id = id,
                            event =
                                when (content) {
                                    is NotificationContent.Loaded -> content.notification
                                    NotificationContent.Failure -> detail.value.event
                                    NotificationContent.Unavailable -> null
                                },
                            content = content,
                        )
                } finally {
                    if (detailVersion == version) detailJob = null
                }
            }
        detailJob = pending
        pending.start()
    }

    /** Installs one all-event source before its first potentially synchronous emission. */
    private fun observeEvents() {
        val version = ++listVersion
        listJob?.cancel()
        failed = false
        events = null
        publishList()
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    notifications.observeAll().collect { rows ->
                        currentCoroutineContext().ensureActive()
                        if (closed || listVersion != version) return@collect
                        events = rows.distinctBy { it.id }
                        failed = false
                        publishList()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!closed && listVersion == version) {
                        events = null
                        failed = true
                        publishList()
                    }
                }
            }
        listJob = pending
        pending.start()
    }

    /** Projects retained filters against immutable stored facts without more repository reads. */
    private fun publishList() {
        if (closed) return
        val current = state.value
        val query = current.query.trim()
        val outcome =
            events?.let { rows ->
                NotificationLogEvents.Content(
                    rows
                        .filter { event ->
                            (current.type == null || current.type == event.type) &&
                                sequenceOf(
                                    event.id,
                                    event.senderId.orEmpty(),
                                    event.subject.id,
                                    event.type.name.replace('_', ' '),
                                    event.announcement?.title.orEmpty(),
                                    event.announcement?.body.orEmpty(),
                                ).any { it.contains(query, ignoreCase = true) }
                        }.sortedWith(compareByDescending<Notification> { it.createdAt }.thenBy { it.id }),
                )
            } ?: if (failed) NotificationLogEvents.Failure else NotificationLogEvents.Loading
        mutableState.value = current.copy(events = outcome)
    }

    /** Invalidates callbacks before releasing graph-owned observations. */
    override fun onCleared() {
        closed = true
        listVersion++
        listJob?.cancel()
        detailVersion++
        detailJob?.cancel()
        super.onCleared()
    }
}
