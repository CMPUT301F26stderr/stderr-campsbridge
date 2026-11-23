/*
 * Owns one participating owner's local announcement and retained explicit send request.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

/** Owns one participating owner's local announcement and retained explicit send request.
 * @param ownerId Original actor for this controller lifetime.
 * @param lotteryId Saved audience source, never reconstructed by the UI.
 * @param lotteries Actual saved context observation.
 * @param accounts Current actor availability and capabilities.
 * @param notifications Existing retained-event announcement/read operations.
 * @param savedState Raw input and complete frozen request primitives.
 * @param newId Allocates an ID only for a new explicit valid Send.
 */
class AnnouncementComposeViewModel(
    private val ownerId: String,
    private val lotteryId: String,
    private val lotteries: LotteryRepository,
    private val accounts: AccountRepository,
    private val notifications: NotificationRepository,
    private val savedState: SavedStateHandle,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val restored =
        savedState.get<String>(ID)?.let { id ->
            val lottery = savedState.get<String>(LOTTERY)
            val actor = savedState.get<String>(SENDER)
            val title = savedState.get<String>(REQUEST_TITLE)
            val body = savedState.get<String>(REQUEST_BODY)
            if (lottery == null || actor == null || title.isNullOrBlank() || body.isNullOrBlank()) {
                null
            } else {
                AnnouncementAttempt(id, lottery, actor, title, body)
            }
        }
    private val mutableState =
        MutableStateFlow(
            AnnouncementComposeUiState(
                title = restored?.title ?: savedState.get<String>(TITLE).orEmpty(),
                body = restored?.body ?: savedState.get<String>(BODY).orEmpty(),
                attempt = restored,
                phase =
                    when {
                        restored == null -> AnnouncementPhase.IDLE
                        restored.senderId != ownerId || restored.lotteryId != lotteryId -> AnnouncementPhase.CONFLICT
                        else -> AnnouncementPhase.RETRY
                    },
                sent = savedState.get<Boolean>(SENT) == true,
            ),
        )
    val state = mutableState.asStateFlow()
    private var source: Job? = null
    private var sourceVersion = 0L
    private var leaving = false

    init {
        observeContext()
    }

    /** Retains an editable title without writing.
     * @param title Requested raw title.
     */
    fun editTitle(title: String) {
        if (!canEdit()) return
        savedState[TITLE] = title
        mutableState.value = state.value.copy(title = title, titleInvalid = false)
    }

    /** Retains an editable multiline body without writing.
     * @param body Requested raw body.
     */
    fun editBody(body: String) {
        if (!canEdit()) return
        savedState[BODY] = body
        mutableState.value = state.value.copy(body = body, bodyInvalid = false)
    }

    /** Freezes one valid explicit request before submitting it. */
    fun send() {
        val current = state.value
        if (!current.canSend || !current.hasRecipients || current.attempt != null || current.sent || leaving) return
        mutableState.value = current.copy(titleInvalid = current.title.isBlank(), bodyInvalid = current.body.isBlank())
        if (current.title.isBlank() || current.body.isBlank()) return
        val request = AnnouncementAttempt(newId(), lotteryId, ownerId, current.title, current.body)
        savedState[ID] = request.id
        savedState[LOTTERY] = request.lotteryId
        savedState[SENDER] = request.senderId
        savedState[REQUEST_TITLE] = request.title
        savedState[REQUEST_BODY] = request.body
        mutableState.value = state.value.copy(attempt = request)
        submit(request)
    }

    /** Reconciles the frozen request before an explicit same-ID retry. */
    fun retrySend() {
        val current = state.value
        val request = current.attempt ?: return
        if (!current.canSend || leaving ||
            current.phase !in setOf(AnnouncementPhase.RETRY, AnnouncementPhase.ABSENT)
        ) {
            return
        }
        mutableState.value = current.copy(phase = AnnouncementPhase.CHECKING)
        viewModelScope.launch { reconcile(request, resendWhenAbsent = true) }
    }

    /** Explicitly abandons an absent/conflicting request before making a new one. */
    fun editAttempt() {
        if (leaving || state.value.phase !in setOf(AnnouncementPhase.ABSENT, AnnouncementPhase.CONFLICT)) return
        clearAttempt()
        mutableState.value = state.value.copy(attempt = null, phase = AnnouncementPhase.IDLE)
    }

    /** Refreshes failed context reads without replaying a send. */
    fun retry() {
        if (!pending() && !leaving && state.value.read != AnnouncementRead.READY) observeContext()
    }

    /** Applies pending/dirty-form rules to shared Back input. */
    fun requestBack(): Boolean {
        if (pending() || leaving) return false
        if (state.value.title.isNotBlank() || state.value.body.isNotBlank() || state.value.attempt != null) {
            mutableState.value = state.value.copy(confirmingDiscard = true)
            return false
        }
        leaving = true
        return true
    }

    /** Keeps the unsent local form. */
    fun dismissDiscard() {
        mutableState.value = state.value.copy(confirmingDiscard = false)
    }

    /** Abandons only local input and returns through its caller. */
    fun confirmDiscard(): Boolean {
        if (pending() || leaving || !state.value.confirmingDiscard) return false
        clearAttempt()
        savedState[TITLE] = ""
        savedState[BODY] = ""
        mutableState.value =
            state.value.copy(
                title = "",
                body = "",
                attempt = null,
                phase = AnnouncementPhase.IDLE,
                confirmingDiscard = false,
            )
        leaving = true
        return true
    }

    /** Consumes a confirmed return effect once after the child resumes. */
    fun consumeSent(): Boolean {
        if (!state.value.sent || leaving) return false
        savedState[SENT] = false
        mutableState.value = state.value.copy(sent = false)
        leaving = true
        return true
    }

    /** Observes the fixed actor, saved lottery and current nonwithdrawn announcement audience. */
    private fun observeContext() {
        source?.cancel()
        val version = ++sourceVersion
        mutableState.value = state.value.copy(read = AnnouncementRead.LOADING, canSend = false)
        source =
            viewModelScope.launch {
                try {
                    combine(
                        lotteries.observe(lotteryId),
                        accounts.observe(ownerId),
                        lotteries.observeEntries(lotteryId),
                    ) { lottery, account, entries ->
                        Triple(lottery, account, entries.any { it.state != EntryState.WITHDRAWN })
                    }.collect { (lottery, account, hasRecipients) ->
                        currentCoroutineContext().ensureActive()
                        if (version != sourceVersion) return@collect
                        val allowed =
                            lottery?.id == lotteryId && lottery.publishedAt != null &&
                                ownerId in lottery.participantOwnerIds && account?.id == ownerId && account.isActive &&
                                Role.OWNER in account.roles
                        mutableState.value =
                            state.value.copy(
                                read = if (allowed) AnnouncementRead.READY else AnnouncementRead.UNAVAILABLE,
                                lottery = lottery?.takeIf { it.id == lotteryId },
                                canSend = allowed,
                                hasRecipients = hasRecipients,
                            )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version ==
                        sourceVersion
                    ) {
                        mutableState.value =
                            state.value.copy(read = AnnouncementRead.FAILURE, canSend = false)
                    }
                }
            }
    }

    /** Sends the frozen tuple, then reconciles any uncertain outcome without changing its identity.
     * @param request Complete original explicit request.
     */
    private fun submit(request: AnnouncementAttempt) {
        if (request.senderId != ownerId || request.lotteryId != lotteryId) {
            mutableState.value = state.value.copy(phase = AnnouncementPhase.CONFLICT)
            return
        }
        mutableState.value = state.value.copy(phase = AnnouncementPhase.SENDING)
        viewModelScope.launch {
            try {
                check(
                    notifications.announce(
                        request.id,
                        request.lotteryId,
                        request.senderId,
                        Announcement(request.title, request.body),
                    ) == request.id,
                )
                currentCoroutineContext().ensureActive()
                complete(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(phase = AnnouncementPhase.CHECKING)
                reconcile(request, resendWhenAbsent = false)
            }
        }
    }

    /** Compares every request fact before confirming storage or allowing same-ID replay.
     * @param request Complete retained tuple.
     * @param resendWhenAbsent Only explicit Retry may resubmit a confirmed absent event.
     */
    private suspend fun reconcile(
        request: AnnouncementAttempt,
        resendWhenAbsent: Boolean,
    ) {
        if (request.senderId != ownerId || request.lotteryId != lotteryId) {
            mutableState.value = state.value.copy(phase = AnnouncementPhase.CONFLICT)
            return
        }
        try {
            val saved = notifications.get(request.id)
            currentCoroutineContext().ensureActive()
            if (state.value.attempt != request) return
            when {
                saved == null -> {
                    mutableState.value = state.value.copy(phase = AnnouncementPhase.ABSENT)
                    if (resendWhenAbsent && state.value.canSend && state.value.hasRecipients) submit(request)
                }

                saved.id == request.id && saved.type == NotificationType.OWNER_ANNOUNCEMENT &&
                    saved.senderId == request.senderId && saved.subject.type == SubjectType.LOTTERY &&
                    saved.subject.id == request.lotteryId &&
                    saved.announcement == Announcement(request.title, request.body) -> {
                    complete(request)
                }

                else -> {
                    mutableState.value = state.value.copy(phase = AnnouncementPhase.CONFLICT)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            if (state.value.attempt == request) mutableState.value = state.value.copy(phase = AnnouncementPhase.RETRY)
        }
    }

    /** Confirms only the exact stored request, without claiming fan-out, banners or read receipts.
     * @param request Original event submission whose result was established.
     */
    private fun complete(request: AnnouncementAttempt) {
        if (state.value.attempt != request) return
        clearAttempt()
        savedState[TITLE] = ""
        savedState[BODY] = ""
        savedState[SENT] = true
        mutableState.value =
            state.value.copy(title = "", body = "", attempt = null, phase = AnnouncementPhase.IDLE, sent = true)
    }

    /** Clears only retained local request identity, never the stored event or its deliveries. */
    private fun clearAttempt() {
        for (key in listOf(ID, LOTTERY, SENDER, REQUEST_TITLE, REQUEST_BODY)) savedState.remove<String>(key)
    }

    /** Keeps every local field locked until its complete retained request is explicitly released. */
    private fun canEdit(): Boolean =
        state.value.canSend && state.value.attempt == null && !state.value.sent && !leaving

    /** Guards all Back and duplicate submission paths during the actual operation/read. */
    private fun pending(): Boolean =
        state.value.phase in setOf(AnnouncementPhase.SENDING, AnnouncementPhase.CHECKING)

    /** Small retained primitives only; application records stay in repository observations. */
    private companion object {
        const val ID = "announcementAttemptId"
        const val LOTTERY = "announcementAttemptLottery"
        const val SENDER = "announcementAttemptSender"
        const val REQUEST_TITLE = "announcementAttemptTitle"
        const val REQUEST_BODY = "announcementAttemptBody"
        const val TITLE = "announcementTitle"
        const val BODY = "announcementBody"
        const val SENT = "announcementSent"
    }
}
