/*
 * Reads saved messages with descriptive context; never sends or acknowledges participant content.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 整体流程（AD6 管理员消息日志，只读）：
// 1. observe() 持续观察 repository 里所有已保存的消息。
// 2. 每条消息用 loadContext() 找到它的预订、营地（真正的 owner）和双方账号名字，判断消息方向。
// 3. editQuery() 只在本地用搜索词过滤，不重新读取；某一行上下文读取失败时可以单独 retryContext()。
// 4. conversation() 返回选中消息所在预订的完整对话，不受搜索影响。
// 5. 这里从来不发送消息，也不标记已读，只读取数据。

/** Reads saved messages with descriptive context; never sends or acknowledges participant content.
 * @param messages Existing saved all-message observation.
 * @param bookings Saved reservation context.
 * @param campsites Actual campsite owner facts.
 * @param accounts Independent supplemental profile labels.
 * @param saved Retained local query only.
 */
class MessageLogViewModel(
    private val messages: MessageRepository,
    private val bookings: BookingRepository,
    private val campsites: CampsiteRepository,
    private val accounts: AccountRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    // state 给界面用：读取状态、搜索词和过滤后的行；搜索词从 SavedStateHandle 恢复。
    private val mutableState = MutableStateFlow(MessageLogUiState(query = saved.get<String>(QUERY).orEmpty()))
    val state = mutableState.asStateFlow()

    // savedRows 是没有经过搜索过滤的全部行，详情页的对话历史从这里取。
    private val mutableSavedRows = MutableStateFlow<List<MessageLogRow>>(emptyList())
    val savedRows = mutableSavedRows.asStateFlow()

    // records 按消息 id 保存每一行；contextJobs/contextTokens 记录每行正在进行的上下文读取，
    // token 用来丢掉过期的结果。source/sourceToken 对主观察做同样的事。
    private val records = linkedMapOf<String, MessageLogRow>()
    private val contextJobs = mutableMapOf<String, Job>()
    private val contextTokens = mutableMapOf<String, Long>()
    private var nextToken = 0L
    private var source: Job? = null
    private var sourceToken = 0L

    init {
        // 创建 ViewModel 时马上开始观察所有已保存的消息。
        observe()
    }

    /** Retains and applies local text search without restarting observation.
     * @param query Raw search input.
     */
    fun editQuery(query: String) {
        // 搜索词存进 SavedStateHandle（屏幕重建后还在），然后只在本地重新过滤，不重新读 repository。
        saved[QUERY] = query
        mutableState.value = state.value.copy(query = query)
        project()
    }

    /** Retries a failed primary observation without writing. */
    fun retry() {
        // 只有主读取失败时才重新观察；正在加载或已经成功时，按 Retry 什么都不做。
        if (state.value.read == MessageLogRead.FAILURE) observe()
    }

    /** Retries only one failed linked context.
     * @param id Original saved message identity.
     */
    fun retryContext(id: String) {
        // 只重新读取这一行的关联信息；找不到这一行、没有失败或正在加载时直接忽略。
        val row = records[id] ?: return
        if (row.contextFailed && !row.isLoading) loadContext(row.message)
    }

    // find 在全部记录里按 id 查找，不受搜索过滤影响，所以详情页一直能找到选中的消息。

    /** Resolves a selected saved record independently of list filtering.
     * @param id Original message identity.
     */
    fun find(id: String): MessageLogRow? =
        records[id]

    /** Reads only the selected record's saved reservation history, independently of search.
     * @param id Original selected message identity; a missing target exposes no other history.
     */
    fun conversation(id: String): List<MessageLogRow> {
        // 在没有过滤的全部行里找选中的消息；找不到就返回空列表，不会显示别人的对话。
        val rows = savedRows.value
        val selected = rows.firstOrNull { it.message.id == id } ?: return emptyList()
        // reservationId 相同的消息就是同一段对话，按时间从早到晚排序（时间相同按 id）。
        return rows
            .filter { it.message.reservationId == selected.message.reservationId }
            .sortedWith(compareBy<MessageLogRow> { it.message.createdAt }.thenBy { it.message.id })
    }

    /** Observes actual messages; a source failure never becomes an empty successful result. */
    private fun observe() {
        // 取消旧的观察，用新的 token 标记这一次；旧观察迟到的结果会被忽略。
        source?.cancel()
        val token = ++sourceToken
        mutableState.value = state.value.copy(read = MessageLogRead.LOADING)
        source =
            viewModelScope.launch {
                try {
                    messages.observeAll().collect { values ->
                        currentCoroutineContext().ensureActive()
                        if (token != sourceToken) return@collect
                        // repository 里已经没有的消息：从 records 删除，并取消它的上下文读取。
                        val ids = values.map { it.id }.toSet()
                        for (id in records.keys.toList().filter { it !in ids }) {
                            records.remove(id)
                            contextTokens.remove(id)
                            contextJobs.remove(id)?.cancel()
                        }
                        // 新消息，或者预订/发送者变了：重新读取上下文。
                        // 只是其他内容变了：保留已经读到的上下文，只换掉消息本身。
                        for (message in values) {
                            val previous = records[message.id]
                            if (previous == null || previous.message.reservationId != message.reservationId ||
                                previous.message.senderId != message.senderId
                            ) {
                                records[message.id] = MessageLogRow(message)
                                loadContext(message)
                            } else {
                                records[message.id] = previous.copy(message = message)
                            }
                        }
                        // 读取成功（就算是空列表）就是 READY，然后用当前搜索词重新过滤。
                        mutableState.value = state.value.copy(read = MessageLogRead.READY)
                        project()
                    }
                } catch (
                    cancelled: CancellationException,
                ) {
                    // 协程被取消不是读取失败，要继续往外抛。
                    throw cancelled
                } catch (_: Exception) {
                    // 真正的读取失败显示 FAILURE，不会变成“没有消息”；已经读到的行保留。
                    currentCoroutineContext().ensureActive()
                    if (token ==
                        sourceToken
                    ) {
                        mutableState.value = state.value.copy(read = MessageLogRead.FAILURE)
                    }
                }
            }
    }

    /** Resolves stay, actual owner and profile labels independently of participant authorization.
     * @param message Original saved record; missing linked facts cannot remove it.
     */
    private fun loadContext(message: Message) {
        // 取消这条消息旧的上下文读取，给它新的 token，并先把这一行标成“加载中”。
        contextJobs.remove(message.id)?.cancel()
        val token = ++nextToken
        contextTokens[message.id] = token
        records[message.id] = records.getValue(message.id).copy(isLoading = true, contextFailed = false)
        project()
        contextJobs[message.id] =
            viewModelScope.launch {
                // 第一步：按 reservationId 读取预订，takeIf 确认返回的 id 对得上。
                // 每一步出错只记下 failed = true，消息本身不会被删掉。
                var failed = false
                val reservation =
                    try {
                        bookings.get(message.reservationId)?.takeIf { it.id == message.reservationId }
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                        null
                    }
                // 第二步：用预订里的 campsiteId 读取营地，得到真正的 owner。
                val site =
                    try {
                        reservation?.let {
                            campsites.get(it.campsiteId)?.takeIf { site ->
                                site.id == it.campsiteId
                            }
                        }
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                        null
                    }
                // 发送者是 owner 就是 owner 发给 camper，是 entrant 就是反方向；
                // 缺少预订或 owner，或者发送者两者都不是，方向就是 UNAVAILABLE。
                val ownerId = site?.ownerId
                val direction =
                    when {
                        reservation != null && ownerId != null && message.senderId == ownerId -> {
                            MessageLogDirection.OWNER_TO_ENTRANT
                        }

                        reservation != null && ownerId != null && message.senderId == reservation.entrantId -> {
                            MessageLogDirection.ENTRANT_TO_OWNER
                        }

                        else -> {
                            MessageLogDirection.UNAVAILABLE
                        }
                    }
                // 接收者就是对话的另一方。
                val recipient =
                    when (direction) {
                        MessageLogDirection.OWNER_TO_ENTRANT -> reservation?.entrantId
                        MessageLogDirection.ENTRANT_TO_OWNER -> ownerId
                        MessageLogDirection.UNAVAILABLE -> null
                    }
                // 第三步：读取发送者和接收者的账号名字；名字只是补充显示，空名字当作没有。
                val senderLabel =
                    try {
                        accounts
                            .get(message.senderId)
                            ?.takeIf { it.id == message.senderId }
                            ?.name
                            ?.takeIf { it.isNotBlank() }
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                        null
                    }
                val recipientLabel =
                    try {
                        recipient?.let { id ->
                            accounts
                                .get(id)
                                ?.takeIf { it.id == id }
                                ?.name
                                ?.takeIf { it.isNotBlank() }
                        }
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                        null
                    }
                // 只有最新一次读取可以写回结果，过期的结果直接丢掉；
                // 写回时用 records 里最新的消息内容，然后重新过滤。
                currentCoroutineContext().ensureActive()
                if (contextTokens[message.id] == token) {
                    val actual = records[message.id]?.message ?: return@launch
                    records[message.id] =
                        MessageLogRow(
                            actual,
                            reservation,
                            ownerId,
                            senderLabel,
                            recipient,
                            recipientLabel,
                            direction,
                            false,
                            failed,
                        )
                    contextJobs.remove(message.id)
                    project()
                }
            }
    }

    /** Filters descriptive facts while preserving the original source order and identities. */
    private fun project() {
        // 全部行按时间从新到旧排序（时间相同按 id），先存进不过滤的 savedRows。
        val query = state.value.query.trim()
        val allRows =
            records.values
                .sortedWith(
                    compareByDescending<MessageLogRow> { it.message.createdAt }.thenBy { it.message.id },
                )
        mutableSavedRows.value = allRows
        // 搜索词去掉首尾空格，不区分大小写，匹配 id、正文、预订、参与者、名字、营地名和方向；
        // 搜索词为空时显示全部行。
        val rows =
            allRows.filter { row ->
                query.isBlank() ||
                    listOfNotNull(
                        row.message.id,
                        row.message.body,
                        row.message.reservationId,
                        row.message.senderId,
                        row.recipientId,
                        row.ownerId,
                        row.senderLabel,
                        row.recipientLabel,
                        row.reservation?.bookedCampsite?.name,
                        row.direction.name.replace('_', ' '),
                        when (row.direction) {
                            MessageLogDirection.OWNER_TO_ENTRANT -> "owner to camper"
                            MessageLogDirection.ENTRANT_TO_OWNER -> "camper to owner"
                            MessageLogDirection.UNAVAILABLE -> null
                        },
                    ).any { it.contains(query, ignoreCase = true) }
            }
        mutableState.value = state.value.copy(rows = rows)
    }

    /** Only the local search input is restored; saved records stay repository-owned. */
    private companion object {
        const val QUERY = "adminMessageLogQuery"
    }
}
