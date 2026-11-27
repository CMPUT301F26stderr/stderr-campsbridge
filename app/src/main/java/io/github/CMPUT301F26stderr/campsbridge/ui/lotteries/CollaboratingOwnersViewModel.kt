/*
 * Owns one organizer's complete local membership set and explicit saved-lottery action.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.util.Locale

// 这个 ViewModel 是"合作营地主"页面的控制器 (LOT4: 把一个抽签分享给其他营地主)。
// 主要流程: 读取抽签、offers 和所有账号 -> 用户勾选或取消营地主 (只改本地状态)
// -> 点 Save 时, 把完整的成员集合一次交给 repository 保存。
// 只有抽签的组织者 (organizer) 能编辑; 已经有营地放进抽签的营地主不能被移除。
// 本地输入存在 SavedStateHandle 里, 屏幕旋转或进程重建后不会丢失。

/** Owns one organizer's complete local membership set and explicit saved-lottery action.
 * @param lotteryId Existing saved lottery identity.
 * @param actorId Actual account opening this picker.
 * @param accounts Current owner choices and retained profile labels.
 * @param campsites Required allocation-ownership facts.
 * @param lotteries Saved membership, offers and coordinated replacement.
 * @param clock Published registration boundary.
 * @param savedState Small query and desired-membership primitives.
 */
class CollaboratingOwnersViewModel(
    val lotteryId: String,
    private val actorId: String,
    private val accounts: AccountRepository,
    private val campsites: CampsiteRepository,
    private val lotteries: LotteryRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    // UI 状态: 从 SavedStateHandle 恢复搜索文字、已勾选的营地主和上次是否正在保存。
    private val mutableState =
        MutableStateFlow(
            CollaboratingOwnersUiState(
                query = savedState.get<String>("ownerQuery").orEmpty(),
                selectedOwnerIds = savedState.get<ArrayList<String>>("ownerIds").orEmpty().toSet(),
                saveFailed = savedState.get<Boolean>("attempting") == true,
            ),
        )
    val state = mutableState.asStateFlow()

    // completion 不为空表示保存成功, 页面看到后会返回抽签详情。
    private val mutableCompletion = MutableStateFlow(lotteryId.takeIf { savedState.get<Boolean>("completed") == true })
    val completion = mutableCompletion.asStateFlow()
    private val mutableDiscard = MutableStateFlow(false)
    val discardPrompt = mutableDiscard.asStateFlow()
    private val mutableExit = MutableStateFlow(false)
    val exitRequested = mutableExit.asStateFlow()

    // retainLocal 为 true 表示用户改过勾选, 之后刷新数据时不能用已保存的成员覆盖它。
    private var retainLocal = savedState.get<Boolean>("ownerSelection") == true
    private var profileRecords = emptyList<Account>()
    private var allocatedOwners = emptySet<String>()
    private var allocatedSitesPublished = true
    private var reading: Job? = null

    init {
        retry()
    }

    /** Restarts required reads while keeping the local membership and search. */
    fun retry() {
        // 创建时和点 Retry 时调用: 保存中不重读; 否则显示加载中, 重新订阅数据, 本地勾选不变。
        if (state.value.isSaving) return
        publish(state.value.copy(targetLoad = LotteryTargetLoad.LOADING))
        observeContext()
    }

    /** Gives ordinary observation one lifetime, separate from an explicit write and its refresh. */
    private fun observeContext() {
        // 同时监听抽签、offers 和账号列表, 任何一个变化都重新读取完整数据。
        // 读取出错就显示 FAILURE (页面给出 Retry 按钮); 取消异常要继续抛出, 不能当成失败。
        reading?.cancel()
        reading =
            viewModelScope.launch {
                try {
                    combine(lotteries.observe(lotteryId), lotteries.observeOffers(lotteryId), accounts.observeAll()) {
                        _,
                        _,
                        profiles,
                        ->
                        profiles
                    }.collectLatest { profiles ->
                        // Listener snapshots invalidate context; cached absence cannot remove a saved member.
                        refreshContext(profiles)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    publish(state.value.copy(targetLoad = LotteryTargetLoad.FAILURE))
                }
            }
    }

    /** Retains local search without changing membership.
     * @param value Raw search text.
     */
    fun setQuery(value: String) {
        // 只修改搜索文字, 不改变成员; 保存中不允许修改。
        if (state.value.isSaving) return
        publish(state.value.copy(query = value))
    }

    /** Changes only one permitted local checkbox value.
     * @param ownerId Target account identity.
     * @param selected Requested membership value.
     */
    fun setOwnerSelected(
        ownerId: String,
        selected: Boolean,
    ) {
        // 勾选或取消一个营地主, 只改本地状态, 不写 Firestore。
        // 不能修改的行 (组织者本人、有营地的成员、无效账号) 会被直接忽略。
        refreshTime()
        val current = state.value
        if (current.owners.none { it.id == ownerId && it.canChange }) return
        if ((ownerId in current.selectedOwnerIds) == selected) return
        // 记住用户改过, 之后数据刷新也会保留这个本地勾选。
        retainLocal = true
        publish(
            current.copy(
                selectedOwnerIds =
                    if (selected) {
                        current.selectedOwnerIds + ownerId
                    } else {
                        current.selectedOwnerIds -
                            ownerId
                    },
                saveFailed = false,
            ),
        )
    }

    /** Submits the complete desired membership through the existing coordinated operation. */
    fun save() {
        // 点 Save: 把完整的期望成员集合交给 repository.updateParticipants 一次保存。
        // canSave 为 false 或已经保存成功时直接返回, 防止错误的保存或重复保存。
        refreshTime()
        val current = state.value
        if (!current.canSave || completion.value != null) return
        val desired = current.selectedOwnerIds.toSet()
        // 保存期间先停止普通监听, 避免旧数据覆盖正在保存的状态。
        reading?.cancel()
        reading = null
        savedState["attempting"] = true
        publish(current.copy(isSaving = true, saveFailed = false, contextChanged = false))
        viewModelScope.launch {
            try {
                lotteries.updateParticipants(lotteryId, actorId, desired)
                currentCoroutineContext().ensureActive()
                // 保存成功: 用保存后的成员更新状态, 再发出 completion 让页面返回。
                retainLocal = false
                savedState["attempting"] = false
                savedState["completed"] = true
                publish(
                    state.value.copy(
                        lottery = state.value.lottery?.copy(participantOwnerIds = desired),
                        selectedOwnerIds = desired,
                        saveFailed = false,
                        contextChanged = false,
                    ),
                )
                mutableCompletion.value = lotteryId
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                // 保存失败: CONFLICT 表示别人同时改了数据, INACTIVE 表示抽签条件已经变化。
                // 这两种情况都重新读取最新数据, 但保留用户的本地勾选。
                val conflict = failure is PersistenceFailure && failure.reason == PersistenceFailure.Reason.CONFLICT
                val inactive = failure is PersistenceFailure && failure.reason == PersistenceFailure.Reason.INACTIVE
                if (conflict || inactive) {
                    retainLocal = true
                    try {
                        refreshContext(profileRecords)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        publish(state.value.copy(targetLoad = LotteryTargetLoad.FAILURE))
                    }
                }
                // 冲突时提示用户先检查新数据再保存; 其他错误显示保存失败, 用户可以再点 Save 重试。
                publish(state.value.copy(contextChanged = conflict, saveFailed = !conflict))
            } finally {
                // 无论成功还是失败都结束 saving 状态; 没有成功就重新开始监听数据。
                if (viewModelScope.isActive) {
                    publish(state.value.copy(isSaving = false))
                    if (completion.value == null) observeContext()
                }
            }
        }
    }

    /** Rechecks the published registration boundary without writing or dropping input. */
    fun refreshTime() {
        // 用当前时间重新计算权限 (例如报名开始后不能再改), 不写数据, 也不丢输入。
        publish(state.value)
    }

    /** Supplies one resumed-route delay until published membership freezes. */
    fun millisUntilFreeze(): Long? {
        // 已发布的抽签在报名开始后成员就不能改; 这里算出还剩多少毫秒, 页面到时会刷新。
        val current = state.value
        val lottery = current.lottery ?: return null
        if (!current.canEdit || lottery.publishedAt == null) return null
        val remaining = Duration.between(clock.instant(), lottery.configuration.registrationOpensAt)
        return (remaining.toMillis() + if (remaining.nano % 1_000_000 == 0) 0 else 1).coerceAtLeast(1)
    }

    /** Requests confirmation only when leaving would discard local membership edits. */
    fun requestExit() {
        // 按返回时: 有没保存的修改就弹出"放弃修改"确认框, 没有修改就直接退出。
        val current = state.value
        if (current.isSaving) return
        if (current.selectedOwnerIds != current.savedOwnerIds) mutableDiscard.value = true else mutableExit.value = true
    }

    /** Keeps the local picker when discard confirmation is cancelled. */
    fun dismissDiscard() {
        mutableDiscard.value = false
    }

    /** Abandons local membership after explicit confirmation without writing. */
    fun confirmDiscard() {
        // 用户确认放弃: 不写任何数据, 直接退出页面。
        if (state.value.isSaving) return
        mutableDiscard.value = false
        mutableExit.value = true
    }

    /** Consumes one explicit return to the saved reader. */
    fun acknowledgeExit() {
        mutableExit.value = false
    }

    /** Consumes a confirmed membership-save return once. */
    fun acknowledgeCompletion() {
        savedState["completed"] = false
        mutableCompletion.value = null
    }

    /** Reads membership and allocation ownership without comparing an editor revision.
     * @param profiles Latest readable profile table for labels and active new choices.
     */
    private suspend fun refreshContext(profiles: List<Account>) {
        // 读取最新的抽签和 offers, 算出哪些营地主有营地在这个抽签里 (这些人不能被移除)。
        val lottery = lotteries.get(lotteryId)
        currentCoroutineContext().ensureActive()
        // 抽签不存在、当前用户不是成员、或抽签已取消时, 显示 UNAVAILABLE。
        if (lottery == null || lottery.id != lotteryId || actorId !in lottery.participantOwnerIds ||
            lottery.cancellation != null
        ) {
            publish(state.value.copy(lottery = lottery, targetLoad = LotteryTargetLoad.UNAVAILABLE))
            return
        }
        val offers = lotteries.getOffers(lotteryId)
        val sites = offers.map { checkNotNull(campsites.get(it.campsiteId)) }
        currentCoroutineContext().ensureActive()
        profileRecords = profiles
        allocatedOwners = sites.map { it.ownerId }.toSet()
        allocatedSitesPublished = sites.all { it.isPublished }
        // 用户改过勾选或正在保存时, 保留本地选择 (组织者一定包含在内); 否则用已保存的成员。
        val desired =
            if (retainLocal || state.value.isSaving) {
                state.value.selectedOwnerIds + lottery.ownerId
            } else {
                lottery.participantOwnerIds
            }
        publish(state.value.copy(lottery = lottery, targetLoad = LotteryTargetLoad.READY, selectedOwnerIds = desired))
    }

    /** Derives current permissions and row facts from confirmed context, then retains only primitives.
     * @param value Latest local input, saved context and operation outcome.
     */
    private fun publish(value: CollaboratingOwnersUiState) {
        // 每次状态变化都经过这里: 根据最新数据重新计算 canEdit 和 canSave, 并存进 SavedStateHandle。
        val lottery = value.lottery
        val members = lottery?.participantOwnerIds.orEmpty()
        // 只有组织者能编辑; 抽签不能已取消; 已发布的抽签只能在报名开始之前修改。
        val canEdit =
            value.targetLoad == LotteryTargetLoad.READY && lottery != null &&
                lottery.ownerId == actorId && actorId in members && lottery.cancellation == null &&
                (lottery.publishedAt == null || clock.instant() < lottery.configuration.registrationOpensAt)
        val profiles = profileRecords.associateBy { it.id }
        // 第一次分享 (原来只有组织者一个成员) 时, 抽签里的营地必须都已发布。
        val needsPublication = members.size == 1 && value.selectedOwnerIds.size > 1 && !allocatedSitesPublished
        val validAdditions = (value.selectedOwnerIds - members).all { validOwner(profiles[it]) }
        val owners = choices(value, profiles, canEdit)
        // canSave 条件: 可编辑、不在保存中、新加的人都是有效营地主、组织者还在、
        // 有营地的营地主一个都没被去掉。任何一条不满足, Save 按钮就不可用。
        val next =
            value.copy(
                savedOwnerIds = members,
                owners = owners,
                canEdit = canEdit,
                canSave =
                    canEdit && !value.isSaving && !needsPublication && validAdditions &&
                        lottery.ownerId in value.selectedOwnerIds &&
                        value.selectedOwnerIds.containsAll(
                            allocatedOwners,
                        ),
                needsPublishedCampsites = needsPublication,
            )
        // 把本地输入存进 SavedStateHandle, 进程被系统回收后也能恢复。
        savedState["ownerQuery"] = next.query
        savedState["ownerIds"] = ArrayList(next.selectedOwnerIds)
        savedState["ownerSelection"] = retainLocal
        mutableState.value = next
    }

    /** Keeps saved members first and filters only additional active owners by the local search.
     * @param state Current desired membership and operation feedback.
     * @param profiles Profile table keyed by stable identity.
     * @param editable Whether the actual organizer can change this saved context.
     */
    private fun choices(
        state: CollaboratingOwnersUiState,
        profiles: Map<String, Account>,
        editable: Boolean,
    ): List<CollaboratingOwnerChoice> {
        // 生成列表的每一行: 已保存的成员排在前面 (组织者第一), 搜索只过滤新加的营地主。
        // 每行的 canChange 决定复选框能不能点: 组织者和有营地的成员不能取消勾选。
        val lottery = state.lottery
        val retained = lottery?.participantOwnerIds.orEmpty() + state.selectedOwnerIds
        val query = state.query.trim()
        val order =
            compareBy<String> { if (it == lottery?.ownerId) 0 else 1 }
                .thenBy { profiles[it]?.name?.lowercase(Locale.ROOT).orEmpty() }
                .thenBy { it }
        val current = lottery?.participantOwnerIds.orEmpty().sortedWith(order)
        val additional =
            profiles.values
                .filter {
                    validOwner(it) && it.id !in retained &&
                        (it.name.contains(query, true) || it.id.contains(query, true))
                }.map { it.id }
        val ids = current + ((state.selectedOwnerIds - current.toSet()) + additional).sortedWith(order)
        // 按名字分组, 名字重复的营地主会显示 ID, 方便区分。
        val names =
            profiles.values
                .filter {
                    validOwner(
                        it,
                    ) || it.id in retained
                }.groupBy { it.name.lowercase(Locale.ROOT) }
        return ids.map { id ->
            val profile = profiles[id]
            val selected = id in state.selectedOwnerIds
            CollaboratingOwnerChoice(
                id,
                profile?.takeIf { it.isActive }?.name,
                isOrganizer = id == lottery?.ownerId,
                hasAllocations = id in allocatedOwners,
                canChange =
                    editable && !state.isSaving && id != lottery?.ownerId &&
                        (!selected || id !in allocatedOwners) && (selected || id in current || validOwner(profile)),
                showId = profile != null && names[profile.name.lowercase(Locale.ROOT)].orEmpty().size > 1,
            )
        }
    }

    // 只有活跃 (没有被删除) 并且有 OWNER 角色的账号, 才能被新加为合作营地主。

    /** Accepts only active profiles carrying owner capability as new choices.
     * @param account Current readable profile, or null when unavailable.
     */
    private fun validOwner(account: Account?): Boolean =
        account?.isActive == true && Role.OWNER in account.roles
}
