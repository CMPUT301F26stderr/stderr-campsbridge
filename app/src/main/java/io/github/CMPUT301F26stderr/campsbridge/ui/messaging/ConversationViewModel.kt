package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

/** Owns one participant conversation, explicit retained-ID sends and visible recipient reads.
 * @param accountId Actual actor, fixed for this model lifetime.
 * @param reservationId Saved conversation identity.
 * @param bookings Saved stay source.
 * @param resolver Actual campsite-owner relationship resolver shared with notification entry.
 * @param messages Saved content and atomic send/read operations.
 * @param accounts Participant availability and independent labels.
 * @param savedState Small draft/frozen request primitives.
 * @param newId Allocates a message ID only for a new explicit valid send.
 */
class ConversationViewModel(
    private val accountId: String,
    private val reservationId: String,
    private val bookings: BookingRepository,
    private val resolver: ConversationContextResolver,
    private val messages: MessageRepository,
    private val accounts: AccountRepository,
    private val savedState: SavedStateHandle,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private var closed = false
    private val restored =
        savedState.get<String>(ID_KEY)?.let { id ->
            val stay = savedState.get<String>(STAY_KEY)
            val sender = savedState.get<String>(SENDER_KEY)
            val body = savedState.get<String>(BODY_KEY)
            if (stay == null || sender == null || body.isNullOrBlank()) null else MessageAttempt(id, stay, sender, body)
        }
    private val mutableState =
        MutableStateFlow(
            ConversationUiState(
                input = restored?.body ?: savedState.get<String>(DRAFT_KEY).orEmpty(),
                attempt = restored,
                phase = if (restored == null) MessageSendPhase.IDLE else MessageSendPhase.RETRY,
            ),
        )
    val state = mutableState.asStateFlow()
    private var sourceJob: Job? = null
    private var messageJob: Job? = null
    private val profileJobs = mutableMapOf<String, Job>()
    private val profiles = mutableMapOf<String, Account?>()
    private val failedProfiles = mutableSetOf<String>()
    private var contextVersion = 0L
    private var visible = false
    private var leaving = false
    private val confirmedReads = mutableSetOf<String>()
    private val pendingReads = mutableSetOf<String>()

    init {
        observeContext()
    }

    /** Retains raw multiline text only when no frozen attempt owns the composer.
     * @param body Requested raw draft.
     */
    fun edit(body: String) {
        if (state.value.attempt != null || !state.value.canSend || leaving || closed) return
        savedState[DRAFT_KEY] = body
        mutableState.value = state.value.copy(input = body)
    }

    /** Allocates one ID and freezes the complete request before any suspension. */
    fun send() {
        val current = state.value
        if (!current.canSend || current.attempt != null || current.input.isBlank() || leaving || closed) return
        // 冻结正文和消息 ID，重试复用同一请求，避免重复消息。
        val request = MessageAttempt(newId(), reservationId, accountId, current.input)
        retain(request)
        submit(request)
    }

    /** Reconciles before an explicit same-ID retry; failed reads never authorize a fresh send. */
    fun retrySend() {
        val current = state.value
        val request = current.attempt ?: return
        if (!current.canSend || current.phase !in setOf(MessageSendPhase.RETRY, MessageSendPhase.ABSENT) ||
            leaving || closed
        ) {
            return
        }
        mutableState.value = current.copy(phase = MessageSendPhase.CHECKING)
        viewModelScope.launch { reconcile(request, resendWhenAbsent = true) }
    }

    /** Explicitly abandons a confirmed-absent or conflicting request while preserving its text. */
    fun editAttempt() {
        if (state.value.phase !in setOf(MessageSendPhase.ABSENT, MessageSendPhase.CONFLICT) || leaving || closed) return
        clearAttempt()
        mutableState.value = state.value.copy(attempt = null, phase = MessageSendPhase.IDLE)
    }

    /** Refreshes required context or failed message reads without replaying a retained send. */
    fun retry() {
        if (isPending() || leaving || closed) return
        if (state.value.read != ConversationRead.READY || state.value.sendingUnavailable ||
            !state.value.canSend
        ) {
            observeContext()
        } else if (state.value.messagesFailed) {
            observeMessages()
        }
    }

    /** Opens/closes only the visible recipient acknowledgement boundary.
     * @param active This actual route is visible and resumed.
     */
    fun setVisible(active: Boolean) {
        if (closed || leaving) return
        visible = active
        if (active) acknowledgeVisible()
    }

    /** Retries only failed recipient reads while this same conversation is visible. */
    fun retryReads() {
        if (closed || leaving || !visible) return
        mutableState.value = state.value.copy(failedReadIds = emptySet())
        acknowledgeVisible()
    }

    /** Blocks pending commands or asks to discard an unsent local draft before leaving. */
    fun requestBack(): Boolean {
        if (isPending() || leaving || closed) return false
        if (state.value.input.isNotBlank() || state.value.attempt != null) {
            mutableState.value = state.value.copy(confirmingDiscard = true)
            return false
        }
        leaving = true
        return true
    }

    /** Cancels unsubmitted draft abandonment. */
    fun dismissDiscard() {
        if (closed || leaving) return
        mutableState.value = state.value.copy(confirmingDiscard = false)
    }

    /** Discards local text/recovery state only; confirmed history remains repository-owned. */
    fun confirmDiscard(): Boolean {
        if (!state.value.confirmingDiscard || isPending() || leaving || closed) return false
        clearAttempt()
        savedState[DRAFT_KEY] = ""
        mutableState.value =
            state.value.copy(input = "", attempt = null, phase = MessageSendPhase.IDLE, confirmingDiscard = false)
        leaving = true
        return true
    }

    /** Submits the original frozen tuple; failure reconciles rather than fabricating a saved row.
     * @param request Original explicit message attempt.
     */
    private fun submit(request: MessageAttempt) {
        if (request.senderId != accountId || request.reservationId != reservationId) {
            mutableState.value = state.value.copy(phase = MessageSendPhase.CONFLICT)
            return
        }
        mutableState.value = state.value.copy(phase = MessageSendPhase.SENDING)
        viewModelScope.launch {
            try {
                check(messages.send(request.id, request.reservationId, request.senderId, request.body) == request.id)
                currentCoroutineContext().ensureActive()
                complete(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                val reason = (failure as? PersistenceFailure)?.reason
                if (reason in
                    setOf(
                        PersistenceFailure.Reason.INACTIVE,
                        PersistenceFailure.Reason.NOT_ALLOWED,
                        PersistenceFailure.Reason.NOT_FOUND,
                    )
                ) {
                    mutableState.value =
                        state.value.copy(
                            sendingUnavailable = true,
                            canSend = false,
                            availability = ConversationAvailability.UNAVAILABLE,
                        )
                }
                mutableState.value = state.value.copy(phase = MessageSendPhase.CHECKING)
                reconcile(request, resendWhenAbsent = false)
            }
        }
    }

    /** Compares the entire original request, preserving saved timestamps/read history.
     * @param request Retained immutable attempt.
     * @param resendWhenAbsent An explicit Retry may submit after a confirmed missing record.
     */
    private suspend fun reconcile(
        request: MessageAttempt,
        resendWhenAbsent: Boolean,
    ) {
        try {
            val saved = messages.get(request.id)
            currentCoroutineContext().ensureActive()
            if (state.value.attempt != request) return
            when {
                request.senderId != accountId || request.reservationId != reservationId -> {
                    mutableState.value =
                        state.value.copy(phase = MessageSendPhase.CONFLICT)
                }

                saved == null -> {
                    mutableState.value = state.value.copy(phase = MessageSendPhase.ABSENT)
                    if (resendWhenAbsent && state.value.canSend) submit(request)
                }

                saved.id == request.id && saved.reservationId == request.reservationId &&
                    saved.senderId == request.senderId &&
                    saved.body == request.body -> {
                    complete(request)
                }

                else -> {
                    mutableState.value = state.value.copy(phase = MessageSendPhase.CONFLICT)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            if (state.value.attempt == request) mutableState.value = state.value.copy(phase = MessageSendPhase.RETRY)
        }
    }

    /** Clears only the exact confirmed attempt, leaving the observed message list untouched.
     * @param request Original operation whose completion was proved.
     */
    private fun complete(request: MessageAttempt) {
        if (state.value.attempt != request) return
        clearAttempt()
        savedState[DRAFT_KEY] = ""
        mutableState.value = state.value.copy(input = "", attempt = null, phase = MessageSendPhase.IDLE)
    }

    /** Retains small complete request primitives for explicit recovery after reconstruction.
     * @param request Original valid Send request.
     */
    private fun retain(request: MessageAttempt) {
        savedState[ID_KEY] = request.id
        savedState[STAY_KEY] = request.reservationId
        savedState[SENDER_KEY] = request.senderId
        savedState[BODY_KEY] = request.body
        savedState[DRAFT_KEY] = request.body
        mutableState.value = state.value.copy(attempt = request)
    }

    /** Removes only local attempt primitives, never saved messages or Inbox receipts. */
    private fun clearAttempt() {
        for (key in listOf(ID_KEY, STAY_KEY, SENDER_KEY, BODY_KEY)) savedState.remove<String>(key)
    }

    /** Observes the saved stay and resolves its immutable campsite ownership before entry. */
    private fun observeContext() {
        sourceJob?.cancel()
        retireContext()
        mutableState.value =
            state.value.copy(
                read = ConversationRead.LOADING,
                context = null,
                canSend = false,
                sendingUnavailable = false,
            )
        sourceJob =
            viewModelScope.launch {
                try {
                    bookings.observe(reservationId).collectLatest { reservation ->
                        val next =
                            reservation?.takeIf { it.id == reservationId }?.let {
                                resolver.resolve(
                                    it,
                                    accountId,
                                )
                            }
                        currentCoroutineContext().ensureActive()
                        if (next == null) {
                            retireContext()
                            mutableState.value =
                                state.value.copy(read = ConversationRead.UNAVAILABLE, context = null, canSend = false)
                        } else {
                            val changed = state.value.context?.participantIds != next.participantIds
                            mutableState.value = state.value.copy(read = ConversationRead.READY, context = next)
                            if (changed) {
                                retireContext()
                                observeProfiles(next)
                                observeMessages()
                            }
                            publishAvailability()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    retireContext()
                    mutableState.value =
                        state.value.copy(read = ConversationRead.FAILURE, context = null, canSend = false)
                }
            }
    }

    /** Releases dependent observations without discarding local composition/recovery state. */
    private fun retireContext() {
        contextVersion++
        messageJob?.cancel()
        messageJob = null
        profileJobs.values.forEach { it.cancel() }
        profileJobs.clear()
        profiles.clear()
        failedProfiles.clear()
    }

    /** Observes both actual participants; missing names keep readable saved history.
     * @param context Actual two-person saved relationship.
     */
    private fun observeProfiles(context: ConversationContext) {
        val version = contextVersion
        for (id in context.participantIds) {
            profileJobs[id] =
                viewModelScope.launch {
                    try {
                        accounts.observe(id).collect { account ->
                            currentCoroutineContext().ensureActive()
                            if (version != contextVersion) return@collect
                            profiles[id] = account?.takeIf { it.id == id && it.isActive }
                            failedProfiles.remove(id)
                            publishAvailability()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        if (version == contextVersion) {
                            profiles[id] = null
                            failedProfiles.add(id)
                            publishAvailability()
                        }
                    }
                }
        }
    }

    /** Observes authoritative ordered rows; primary failures remain distinct from an empty thread. */
    private fun observeMessages() {
        messageJob?.cancel()
        val version = contextVersion
        mutableState.value = state.value.copy(messagesLoading = true, messagesFailed = false)
        messageJob =
            viewModelScope.launch {
                try {
                    messages.observeConversation(reservationId).collect { rows ->
                        currentCoroutineContext().ensureActive()
                        if (version != contextVersion) return@collect
                        val context = state.value.context ?: return@collect
                        mutableState.value =
                            state.value.copy(
                                messages =
                                    rows
                                        .filter {
                                            it.reservationId == reservationId &&
                                                it.senderId in context.participantIds
                                        }.sortedBy { it.createdAt },
                                messagesLoading = false,
                                messagesFailed = false,
                            )
                        acknowledgeVisible()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version ==
                        contextVersion
                    ) {
                        mutableState.value =
                            state.value.copy(messagesLoading = false, messagesFailed = true)
                    }
                }
            }
    }

    /** Derives current sending eligibility separately from readable historical messages. */
    private fun publishAvailability() {
        val context = state.value.context
        val availability =
            when {
                context == null -> ConversationAvailability.LOADING

                context.participantIds.any { it in failedProfiles } -> ConversationAvailability.FAILURE

                context.participantIds.any { it !in profiles } -> ConversationAvailability.LOADING

                state.value.sendingUnavailable ||
                    context.participantIds.any {
                        profiles[it]?.isActive != true
                    }
                -> ConversationAvailability.UNAVAILABLE

                else -> ConversationAvailability.AVAILABLE
            }
        mutableState.value =
            state.value.copy(
                canSend =
                    state.value.read == ConversationRead.READY && availability == ConversationAvailability.AVAILABLE,
                availability = availability,
                labels = profiles.mapValues { it.value?.name },
            )
        acknowledgeVisible()
    }

    /** Acknowledges only received unread records when the actual participant route is visible. */
    private fun acknowledgeVisible() {
        val current = state.value
        val context = current.context ?: return
        // 只有实际可见的收件人页面确认已读，发送人不修改这个时间。
        if (closed || leaving || !visible || current.read != ConversationRead.READY ||
            profiles[accountId]?.isActive != true
        ) {
            return
        }
        for (message in current.messages) {
            if (message.senderId == accountId ||
                message.senderId !in context.participantIds || message.readAt != null ||
                message.id in confirmedReads ||
                message.id in pendingReads ||
                message.id in current.failedReadIds
            ) {
                continue
            }
            pendingReads.add(message.id)
            viewModelScope.launch {
                try {
                    messages.markRead(message.id, accountId)
                    currentCoroutineContext().ensureActive()
                    confirmedReads.add(message.id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    mutableState.value = state.value.copy(failedReadIds = state.value.failedReadIds + message.id)
                } finally {
                    pendingReads.remove(message.id)
                }
            }
        }
    }

    /** Shared Back/field guards depend on the explicit command stage. */
    private fun isPending(): Boolean =
        state.value.phase == MessageSendPhase.SENDING || state.value.phase == MessageSendPhase.CHECKING

    /** Invalidates callbacks before releasing this entry's readers. */
    override fun onCleared() {
        closed = true
        leaving = true
        visible = false
        sourceJob?.cancel()
        retireContext()
        super.onCleared()
    }

    /** Primitive entry-owned drafts and the complete frozen request, never automatic replay. */
    private companion object {
        const val DRAFT_KEY = "messageDraft"
        const val ID_KEY = "messageAttemptId"
        const val STAY_KEY = "messageAttemptReservation"
        const val SENDER_KEY = "messageAttemptSender"
        const val BODY_KEY = "messageAttemptBody"
    }
}
