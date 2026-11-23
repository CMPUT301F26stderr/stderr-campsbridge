/*
 * Owns one participant conversation, explicit retained-ID sends and visible recipient reads.
 */

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

// 这个 ViewModel 管理一个预订里露营者和营地主人之间的对话（MSG1/MSG2）。
// 流程：先读取预订并确认当前账户是参与者，再监听双方资料和消息列表。
// 发送时先生成一个固定的消息 ID 并保存到 SavedStateHandle，失败后 Retry 用同一个 ID，
// 所以不会出现重复消息；确认失败时先按 ID 查询 repository，再决定完成、重发或报冲突。
// 只有页面真正可见时，才把对方发来的未读消息标记为已读；失败的已读要用户点 Retry。

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

    // 进程重建后从 SavedStateHandle 恢复冻结的发送请求，状态设为 RETRY，等用户点重试，不会自动发送。
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

    // contextVersion 用来丢掉旧上下文的迟到回调；confirmedReads/pendingReads 防止重复标记已读。
    private var contextVersion = 0L
    private var visible = false
    private var leaving = false
    private val confirmedReads = mutableSetOf<String>()
    private val pendingReads = mutableSetOf<String>()

    // 创建时马上开始读取预订和参与者信息。
    init {
        observeContext()
    }

    // 用户输入草稿：已有冻结的发送请求或不能发送时忽略输入，草稿存进 SavedStateHandle。

    /** Retains raw multiline text only when no frozen attempt owns the composer.
     * @param body Requested raw draft.
     */
    fun edit(body: String) {
        if (state.value.attempt != null || !state.value.canSend || leaving || closed) return
        savedState[DRAFT_KEY] = body
        mutableState.value = state.value.copy(input = body)
    }

    // 用户点 Send：空白内容、正在发送、或页面已关闭时直接返回，所以连点两次也只发一次。

    /** Allocates one ID and freezes the complete request before any suspension. */
    fun send() {
        val current = state.value
        if (!current.canSend || current.attempt != null || current.input.isBlank() || leaving || closed) return
        // 冻结正文和消息 ID，重试复用同一请求，避免重复消息。
        val request = MessageAttempt(newId(), reservationId, accountId, current.input)
        retain(request)
        submit(request)
    }

    // 用户点重试发送：只在 RETRY 或 ABSENT 状态可用，先按原 ID 查询，确认不存在才用同一个 ID 重发。

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

    // 用户点 Edit：放弃确认不存在或有冲突的请求，文字留在输入框，下次发送会用新的 ID。

    /** Explicitly abandons a confirmed-absent or conflicting request while preserving its text. */
    fun editAttempt() {
        if (state.value.phase !in setOf(MessageSendPhase.ABSENT, MessageSendPhase.CONFLICT) || leaving || closed) return
        clearAttempt()
        mutableState.value = state.value.copy(attempt = null, phase = MessageSendPhase.IDLE)
    }

    // 加载失败后的 Retry：重新读取预订或消息列表，但不会重发保存着的消息。

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

    // 页面路由告诉 ViewModel 页面是否可见；变成可见时才去标记已读。

    /** Opens/closes only the visible recipient acknowledgement boundary.
     * @param active This actual route is visible and resumed.
     */
    fun setVisible(active: Boolean) {
        if (closed || leaving) return
        visible = active
        if (active) acknowledgeVisible()
    }

    // 已读标记失败后用户点 Retry：清空失败列表，只在页面可见时重新标记。

    /** Retries only failed recipient reads while this same conversation is visible. */
    fun retryReads() {
        if (closed || leaving || !visible) return
        mutableState.value = state.value.copy(failedReadIds = emptySet())
        acknowledgeVisible()
    }

    // 返回键：发送或检查中不能离开；有草稿或未完成请求时先弹出确认丢弃的对话框。

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

    // 确认丢弃：只清掉本地草稿和恢复数据，已经保存的消息不受影响。

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

    // 真正调用 repository 发送。成功后清空输入；失败时不能假设没保存（可能只是确认丢了），
    // 所以进入 CHECKING 并去查询这条 ID。

    /** Submits the original frozen tuple; failure reconciles rather than fabricating a saved row.
     * @param request Original explicit message attempt.
     */
    private fun submit(request: MessageAttempt) {
        // 恢复的请求不属于当前账户或当前预订时，不能发送，显示冲突。
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
                // 对方账户被停用、无权限或预订不存在：以后都不能发送，但历史仍然可读。
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

    // 按消息 ID 查询保存结果：不存在→ABSENT（只有用户点重试才重发），内容完全一样→完成，
    // 内容不同→CONFLICT（不覆盖已保存内容），查询本身失败→RETRY。

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
            // 等待期间请求已经被放弃或替换，就忽略这次结果。
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

    // 发送已确认：清掉请求和草稿。消息列表不在这里添加，要等 repository 监听推送过来。

    /** Clears only the exact confirmed attempt, leaving the observed message list untouched.
     * @param request Original operation whose completion was proved.
     */
    private fun complete(request: MessageAttempt) {
        if (state.value.attempt != request) return
        clearAttempt()
        savedState[DRAFT_KEY] = ""
        mutableState.value = state.value.copy(input = "", attempt = null, phase = MessageSendPhase.IDLE)
    }

    // 在发送前把完整请求（ID、预订、发送人、正文）存进 SavedStateHandle，进程重建后还能重试。

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

    // 只删除本地保存的请求数据，不删除任何已保存的消息。

    /** Removes only local attempt primitives, never saved messages or Inbox receipts. */
    private fun clearAttempt() {
        for (key in listOf(ID_KEY, STAY_KEY, SENDER_KEY, BODY_KEY)) savedState.remove<String>(key)
    }

    // 监听预订：resolver 判断当前账户是不是露营者或营地主人。不是参与者或预订不存在→UNAVAILABLE，
    // 读取失败→FAILURE，这两种都不会显示成空对话。参与者变化时重新监听资料和消息。

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

    // 停掉旧的资料和消息监听，并让 contextVersion 加一，使旧回调失效；草稿不受影响。

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

    // 监听双方账户资料：账户不存在或已停用就记为 null，名字显示不出来但历史仍可读。

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

    // 监听消息列表：只保留这个预订、由双方发的消息，按时间排序。读取失败时设置 messagesFailed，
    // 但保留已经显示的历史，和“没有消息”区分开。

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

    // 计算能不能发送：预订已加载且双方账户都有效才可以发送；不能发送时历史照样显示。

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

    // 标记已读：只处理对方发来的、还没读的消息，而且页面必须可见。

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
        // 跳过自己发的、已读的、已确认、正在处理或失败等 Retry 的消息，避免重复写入。
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
                    // 失败的 ID 记下来，界面显示 Retry，不会自动反复重试。
                    mutableState.value = state.value.copy(failedReadIds = state.value.failedReadIds + message.id)
                } finally {
                    pendingReads.remove(message.id)
                }
            }
        }
    }

    // 发送中或检查中算“进行中”，这时不能返回或重新加载。

    /** Shared Back/field guards depend on the explicit command stage. */
    private fun isPending(): Boolean =
        state.value.phase == MessageSendPhase.SENDING || state.value.phase == MessageSendPhase.CHECKING

    // ViewModel 销毁时：设置 closed，之后所有操作和迟到回调都被忽略，并停止所有监听。

    /** Invalidates callbacks before releasing this entry's readers. */
    override fun onCleared() {
        closed = true
        leaving = true
        visible = false
        sourceJob?.cancel()
        retireContext()
        super.onCleared()
    }

    // SavedStateHandle 里用的 key：一个草稿，加上冻结请求的四个字段。

    /** Primitive entry-owned drafts and the complete frozen request, never automatic replay. */
    private companion object {
        const val DRAFT_KEY = "messageDraft"
        const val ID_KEY = "messageAttemptId"
        const val STAY_KEY = "messageAttemptReservation"
        const val SENDER_KEY = "messageAttemptSender"
        const val BODY_KEY = "messageAttemptBody"
    }
}
