package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationContextResolver
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Reads one sender's immutable events and target context without acknowledging recipients.
 * @param accountId Actual original sender identity for this lifetime.
 * @param notifications Existing sender-scoped event observation.
 * @param loader Shared immutable event/context reader.
 * @param resolver Actual conversation participant relationship.
 * @param savedState Local query primitives.
 */
class SentNotificationsViewModel(
    private val accountId: String,
    private val notifications: NotificationRepository,
    private val loader: NotificationContentLoader,
    private val resolver: ConversationContextResolver,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(SentNotificationsUiState(query = savedState.get<String>(QUERY).orEmpty()))
    val state = mutableState.asStateFlow()
    private var source: Job? = null
    private var sourceVersion = 0L
    private var closed = false
    private var events = emptyList<Notification>()
    private val items = mutableMapOf<String, SentNotificationItem>()
    private val jobs = mutableMapOf<String, Job>()
    private val versions = mutableMapOf<String, Long>()
    private var nextVersion = 0L

    init {
        observe()
    }

    /** Retains local text and filters known events without a write.
     * @param query Requested raw local search.
     */
    fun editQuery(query: String) {
        if (closed) return
        savedState[QUERY] = query
        mutableState.value = state.value.copy(query = query)
        publish()
    }

    /** Retries only a failed primary source. */
    fun retry() {
        if (!closed && state.value.source == InboxSourceState.FAILURE) observe()
    }

    /** Retries unavailable/failed linked context while retaining original event facts.
     * @param id Original immutable event identity.
     */
    fun retryItem(id: String) {
        val item = items[id] ?: return
        if (closed || item.loading ||
            item.subject !in setOf(NotificationSubjectContent.Failure, NotificationSubjectContent.Unavailable)
        ) {
            return
        }
        load(item.event)
    }

    /** Returns a currently resolved destination without receipt/message acknowledgment.
     * @param id Selected original event identity.
     */
    fun targetFor(id: String): SentTarget? =
        if (closed) null else items[id]?.takeUnless { it.loading }?.target

    /** Installs an actor-scoped source without ever observing recipient Inbox receipts. */
    private fun observe() {
        source?.cancel()
        val version = ++sourceVersion
        mutableState.value = state.value.copy(source = InboxSourceState.LOADING)
        source =
            viewModelScope.launch {
                try {
                    notifications.observeSent(accountId).collect { rows ->
                        currentCoroutineContext().ensureActive()
                        if (closed || version != sourceVersion) return@collect
                        events =
                            rows.distinctBy { it.id }.sortedWith(
                                compareByDescending<Notification> { it.createdAt }.thenBy { it.id },
                            )
                        val ids = events.map { it.id }.toSet()
                        for (id in items.keys.toList()) {
                            if (id !in ids) {
                                versions.remove(id)
                                jobs.remove(id)?.cancel()
                                items.remove(id)
                            }
                        }
                        mutableState.value = state.value.copy(source = InboxSourceState.READY)
                        for (event in events) if (items[event.id]?.event != event) load(event)
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!closed &&
                        version == sourceVersion
                    ) {
                        mutableState.value = state.value.copy(source = InboxSourceState.FAILURE)
                    }
                }
            }
    }

    /** Keeps original list facts while independently reading content by its original event ID.
     * @param event Current immutable sender event.
     */
    private fun load(event: Notification) {
        val id = event.id
        val version = ++nextVersion
        versions[id] = version
        jobs[id]?.cancel()
        items[id] = SentNotificationItem(event)
        publish()
        jobs[id] =
            viewModelScope.launch {
                try {
                    val loaded = loader.load(id)
                    currentCoroutineContext().ensureActive()
                    var subject =
                        when (loaded) {
                            NotificationContent.Failure -> {
                                NotificationSubjectContent.Failure
                            }

                            NotificationContent.Unavailable -> {
                                NotificationSubjectContent.Unavailable
                            }

                            is NotificationContent.Loaded -> {
                                if (loaded.notification ==
                                    event
                                ) {
                                    loaded.subject
                                } else {
                                    NotificationSubjectContent.Unavailable
                                }
                            }
                        }
                    var target: SentTarget? =
                        when (val value = subject) {
                            is NotificationSubjectContent.LotteryContent -> {
                                if (accountId in
                                    value.lottery.participantOwnerIds
                                ) {
                                    SentTarget.Lottery(value.lottery.id)
                                } else {
                                    null
                                }
                            }

                            else -> {
                                null
                            }
                        }
                    if (subject is NotificationSubjectContent.MessageContent) {
                        val value = subject
                        try {
                            val context = resolver.resolve(value.reservation, accountId)
                            currentCoroutineContext().ensureActive()
                            if (context == null ||
                                value.message.senderId !in context.participantIds
                            ) {
                                subject =
                                    NotificationSubjectContent.Unavailable
                            } else {
                                target =
                                    SentTarget.Conversation(
                                        context.reservation.id,
                                        if (context.reservation.entrantId ==
                                            accountId
                                        ) {
                                            ReservationContext.ENTRANT
                                        } else {
                                            ReservationContext.OWNER
                                        },
                                    )
                            }
                        } catch (
                            cancelled: CancellationException,
                        ) {
                            throw cancelled
                        } catch (_: Exception) {
                            subject = NotificationSubjectContent.Failure
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    if (!closed && versions[id] == version && items[id]?.event == event) {
                        items[id] = SentNotificationItem(event, subject, loading = false, target = target)
                        publish()
                    }
                } finally {
                    if (versions[id] == version) jobs.remove(id)
                }
            }
    }

    /** Filters presentation facts locally; original event membership and timestamps stay authoritative. */
    private fun publish() {
        if (closed) return
        val query =
            state.value.query
                .trim()
                .lowercase(Locale.ROOT)
        val visible =
            events.mapNotNull { items[it.id] }.filter { item ->
                sequenceOf(
                    item.event.announcement
                        ?.title
                        .orEmpty(),
                    item.event.announcement
                        ?.body
                        .orEmpty(),
                    item.event.subject.id,
                    item.event.type.name,
                    (item.subject as? NotificationSubjectContent.MessageContent)?.message?.body.orEmpty(),
                    (item.subject as? NotificationSubjectContent.LotteryContent)?.lottery?.title.orEmpty(),
                    (item.subject as? NotificationSubjectContent.MessageContent)
                        ?.reservation
                        ?.bookedCampsite
                        ?.name
                        .orEmpty(),
                ).any { it.lowercase(Locale.ROOT).contains(query) }
            }
        mutableState.value = state.value.copy(items = visible)
    }

    /** Invalidates every late source/content callback before releasing this original actor. */
    override fun onCleared() {
        closed = true
        sourceVersion++
        versions.clear()
        source?.cancel()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        super.onCleared()
    }

    /** Retains only raw local query text, not event snapshots or repository objects. */
    private companion object {
        const val QUERY = "sentNotificationsQuery"
    }
}
