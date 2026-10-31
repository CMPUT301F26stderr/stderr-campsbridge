package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationEntry
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Retains one root tap and resolves its saved target only for a matching ready session.
 * @param content Shared immutable event/context reader; it never acknowledges receipts.
 * @param savedState Primitive pending/consumed identity values across root recreation.
 */
class NotificationEntryViewModel(
    private val content: NotificationContentLoader,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private var pending = restorePending()
    private val mutableState =
        MutableStateFlow<NotificationEntryUiState>(
            pending?.let { NotificationEntryUiState.Waiting(it) } ?: NotificationEntryUiState.None,
        )
    val state = mutableState.asStateFlow()
    private var session: SessionState = SessionState.Initializing
    private var readJob: Job? = null
    private var readVersion = 0L
    private var closed = false

    /** Retains the newest valid typed tap without switching identity or navigating.
     * @param entry Decoded original account/event/subject envelope.
     */
    fun accept(entry: NotificationEntry) {
        if (closed || !entry.isValid() || pending == entry || isConsumed(entry)) return
        retireRead()
        pending = entry
        savedState[ACCOUNT] = entry.accountId
        savedState[EVENT] = entry.eventId
        savedState[SUBJECT_TYPE] = entry.subject.type.name
        savedState[SUBJECT_ID] = entry.subject.id
        mutableState.value = NotificationEntryUiState.Waiting(entry)
        updateSession(session)
    }

    /** Supplies root session facts without creating an independent account observation.
     * @param session Actual bootstrap/workspace outcome.
     */
    fun updateSession(session: SessionState) {
        if (closed) return
        val old = this.session as? SessionState.Ready
        this.session = session
        val entry = pending ?: return
        val ready = session as? SessionState.Ready
        when {
            ready == null -> {
                retireRead()
                mutableState.value = NotificationEntryUiState.Waiting(entry)
            }

            ready.account.id != entry.accountId -> {
                retireRead()
                mutableState.value = NotificationEntryUiState.WrongAccount(entry)
            }

            old == null || old.account.id != ready.account.id ||
                old.account.roles != ready.account.roles ||
                state.value is NotificationEntryUiState.Waiting ||
                state.value is NotificationEntryUiState.WrongAccount -> {
                resolve(entry, ready.account)
            }
        }
    }

    /** Explicitly retries the same failed/unavailable event for the current matching account. */
    fun retry() {
        if (closed) return
        if (state.value !is NotificationEntryUiState.Failure &&
            state.value !is NotificationEntryUiState.Unavailable
        ) {
            return
        }
        val entry = pending ?: return
        val ready = session as? SessionState.Ready ?: return
        if (ready.account.id == entry.accountId) resolve(entry, ready.account)
    }

    /** Dismisses pending recovery and invalidates old reads without changing shared data. */
    fun dismiss() {
        if (!closed) pending?.let(::finish)
    }

    /** Consumes only the exact ready tap after the route has actually opened its saved target.
     * @param entry Exact envelope acknowledged by the resumed navigation host.
     */
    fun consume(entry: NotificationEntry) {
        if (closed) return
        val target = state.value as? NotificationEntryUiState.Ready ?: return
        val ready = session as? SessionState.Ready ?: return
        if (target.entry == entry && pending == entry && ready.account.id == entry.accountId &&
            target.workspace in ready.account.roles
        ) {
            finish(entry)
        }
    }

    /** Starts one cancellable event/context read after synchronously exposing its loading guard.
     * @param entry Exact pending envelope; no record snapshot is saved.
     * @param account Matching ready account used to resolve actual presentation capabilities.
     */
    private fun resolve(
        entry: NotificationEntry,
        account: Account,
    ) {
        retireRead()
        val version = readVersion
        mutableState.value = NotificationEntryUiState.Loading(entry)
        readJob =
            viewModelScope.launch {
                try {
                    val loaded = content.load(entry.eventId)
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent(version, entry, account)) return@launch
                    mutableState.value = project(entry, account, loaded)
                } catch (cancelled: CancellationException) {
                    if (isCurrent(version, entry, account)) {
                        mutableState.value = NotificationEntryUiState.Waiting(entry)
                    }
                    throw cancelled
                }
            }
    }

    /** Maps saved facts to one registered actor workspace without granting roles or writing.
     * @param entry Envelope checked against the original immutable event's subject.
     * @param account Actual matching account/capabilities.
     * @param loaded Independent event and subject read outcome.
     */
    private fun project(
        entry: NotificationEntry,
        account: Account,
        loaded: NotificationContent,
    ): NotificationEntryUiState {
        if (loaded == NotificationContent.Failure) return NotificationEntryUiState.Failure(entry)
        if (loaded !is NotificationContent.Loaded || loaded.notification.subject != entry.subject) {
            return NotificationEntryUiState.Unavailable(entry)
        }
        val workspace =
            when (val subject = loaded.subject) {
                NotificationSubjectContent.Failure -> {
                    return NotificationEntryUiState.Failure(entry)
                }

                is NotificationSubjectContent.LotteryContent -> {
                    when {
                        subject.lottery.ownerId == account.id && Role.OWNER in account.roles -> {
                            Role.OWNER
                        }

                        Role.ENTRANT in account.roles -> {
                            Role.ENTRANT
                        }

                        else -> {
                            null
                        }
                    }
                }

                is NotificationSubjectContent.ReservationContent -> {
                    when {
                        subject.reservation.entrantId == account.id &&
                            Role.ENTRANT in account.roles -> {
                            Role.ENTRANT
                        }

                        subject.reservation.entrantId != account.id &&
                            Role.OWNER in account.roles -> {
                            Role.OWNER
                        }

                        else -> {
                            null
                        }
                    }
                }

                NotificationSubjectContent.Unavailable, NotificationSubjectContent.Unsupported -> {
                    null
                }
            }
        return workspace?.let { NotificationEntryUiState.Ready(entry, it) }
            ?: NotificationEntryUiState.Unavailable(entry)
    }

    /** Verifies that a late result still belongs to the current ready identity/capabilities.
     * @param version Read generation captured before suspension.
     * @param entry Pending identity captured before suspension.
     * @param account Original matching capabilities, independent of selected workspace.
     */
    private fun isCurrent(
        version: Long,
        entry: NotificationEntry,
        account: Account,
    ): Boolean {
        val current = session as? SessionState.Ready
        return !closed && version == readVersion && pending == entry &&
            current != null && current.account.id == account.id &&
            current.account.roles == account.roles
    }

    /** Cancels and invalidates an old read without changing saved identity or shared records. */
    private fun retireRead() {
        readVersion++
        readJob?.cancel()
        readJob = null
    }

    /** Records one consumed/dismissed pair and removes all pending primitives.
     * @param entry Exact entry whose navigation or dismissal was acknowledged.
     */
    private fun finish(entry: NotificationEntry) {
        retireRead()
        savedState[CONSUMED_ACCOUNT] = entry.accountId
        savedState[CONSUMED_EVENT] = entry.eventId
        clearPending()
        pending = null
        mutableState.value = NotificationEntryUiState.None
    }

    /** Detects consecutive replay without adding a permanent navigation-history database.
     * @param entry Typed envelope to compare with the last acknowledged account/event pair.
     */
    private fun isConsumed(entry: NotificationEntry): Boolean =
        savedState.get<Any?>(CONSUMED_ACCOUNT) == entry.accountId &&
            savedState.get<Any?>(CONSUMED_EVENT) == entry.eventId

    /** Restores only complete valid strings; corrupt/unsupported state starts no read. */
    private fun restorePending(): NotificationEntry? {
        val account = savedState.get<Any?>(ACCOUNT) as? String
        val event = savedState.get<Any?>(EVENT) as? String
        val typeName = savedState.get<Any?>(SUBJECT_TYPE) as? String
        val id = savedState.get<Any?>(SUBJECT_ID) as? String
        val type = SubjectType.entries.firstOrNull { it.name == typeName }
        val entry =
            if (account != null && event != null && type != null && id != null) {
                NotificationEntry(
                    account,
                    event,
                    NotificationSubject(type, id),
                ).takeIf { it.isValid() }
            } else {
                null
            }
        if (entry == null || isConsumed(entry)) {
            clearPending()
            return null
        }
        return entry
    }

    /** Deletes pending keys rather than serializing null/whole record values. */
    private fun clearPending() {
        for (key in listOf(ACCOUNT, EVENT, SUBJECT_TYPE, SUBJECT_ID)) savedState.remove<String>(key)
    }

    /** Rejects all public callbacks and late reads once this root lifetime is released. */
    override fun onCleared() {
        closed = true
        retireRead()
        super.onCleared()
    }

    /** Stable primitive root-state keys; complete application records never enter this handle. */
    companion object {
        private const val ACCOUNT = "notificationEntryAccount"
        private const val EVENT = "notificationEntryEvent"
        private const val SUBJECT_TYPE = "notificationEntrySubjectType"
        private const val SUBJECT_ID = "notificationEntrySubjectId"
        private const val CONSUMED_ACCOUNT = "notificationEntryConsumedAccount"
        private const val CONSUMED_EVENT = "notificationEntryConsumedEvent"
    }
}
