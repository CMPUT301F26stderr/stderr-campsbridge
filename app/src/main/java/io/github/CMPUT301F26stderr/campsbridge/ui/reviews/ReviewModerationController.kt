/*
 * Independent saved feedback components available to an administrator.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.lifecycle.SavedStateHandle
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReviewRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Independent saved feedback components available to an administrator. */
enum class ReviewModerationComponent { RATING, TEXT }

// IDLE 没有请求；CONFIRMING 等管理员确认；REMOVING 正在删除；FAILED 删除失败，可以 Retry；
// UNKNOWN 不知道有没有删成功；UNAVAILABLE 原内容变了或没权限；COMPLETE 删除完成。

/** Confirmation, operation and recovery states for one retained target. */
enum class ReviewModerationPhase { IDLE, CONFIRMING, REMOVING, FAILED, UNKNOWN, UNAVAILABLE, COMPLETE }

// 目标只保存选中那一部分的原始值（评分或文字），另一部分是 null，所以不会被一起删掉。

/** Immutable identity and selected component; the other component is never submitted.
 * @param campsiteId Saved listing identity.
 * @param campsiteName Listing label shown in confirmation.
 * @param authorId Saved feedback author identity.
 * @param authorName Optional descriptive author label.
 * @param component Selected independent component.
 * @param rating Original stars for rating removal, otherwise null.
 * @param text Original body for text removal, otherwise null.
 */
data class ReviewModerationTarget(
    val campsiteId: String,
    val campsiteName: String,
    val authorId: String,
    val authorName: String?,
    val component: ReviewModerationComponent,
    val rating: Long?,
    val text: String?,
)

/** Retained removal presentation, including dismissed recovery.
 * @param phase Current operation outcome.
 * @param target Original saved feedback selection.
 * @param dialogVisible Whether confirmation or recovery is open.
 */
data class ReviewModerationState(
    val phase: ReviewModerationPhase = ReviewModerationPhase.IDLE,
    val target: ReviewModerationTarget? = null,
    val dialogVisible: Boolean = true,
)

// 总体流程：管理员点“删除评分”或“删除文字评论” -> request() 记住这条评论原来的样子
// -> confirm() 后先重新读取账号、营地和评论，确认原来的星级/文字没有变
// -> 只删除选中的那一部分，另一部分不动 -> 结果是 COMPLETE、FAILED、UNKNOWN 或 UNAVAILABLE。
// FAILED/UNKNOWN 可以 Retry（重试前也会先重新检查），关掉对话框后可以用 resume() 再打开。
// 目标和“是否已提交”存在 SavedStateHandle 里，页面重建后能恢复，但不会自动再删一次。

/** Owns independently confirmed rating and text removal with explicit recovery.
 * @param actorId Signed-in administrator identity.
 * @param campsiteId Feedback page's saved listing.
 * @param accounts Current administrator capability reads.
 * @param campsites Saved listing reads.
 * @param reviews Original component reads for confirmation and recovery.
 * @param moderation Independent persisted removal operations.
 * @param saved Primitive original-target state retained through recreation.
 * @param scope Feedback entry's coroutine lifetime.
 * @param canModerate Current page permission and lifecycle gate.
 * @param onRemoved Refreshes confirmed saved feedback.
 */
class ReviewModerationController(
    private val actorId: String,
    private val campsiteId: String,
    private val accounts: AccountRepository,
    private val campsites: CampsiteRepository,
    private val reviews: ReviewRepository,
    private val moderation: ModerationRepository,
    private val saved: SavedStateHandle,
    private val scope: CoroutineScope,
    private val canModerate: () -> Boolean,
    private val onRemoved: () -> Unit,
) {
    private var closed = false
    private var job: Job? = null

    // submitted 表示删除请求是否已经发出去；重建后用它判断结果是不是不确定。
    private var submitted = saved.get<Any?>("moderationSubmitted") == true
    private val restored = restore()

    // 初始状态：没有保存的目标 -> IDLE；已经提交过 -> UNKNOWN（不知道成功没有）；否则 -> CONFIRMING。
    private val mutableState =
        MutableStateFlow(
            ReviewModerationState(
                phase =
                    if (restored ==
                        null
                    ) {
                        ReviewModerationPhase.IDLE
                    } else if (submitted) {
                        ReviewModerationPhase.UNKNOWN
                    } else {
                        ReviewModerationPhase.CONFIRMING
                    },
                target = restored,
            ),
        )
    val state = mutableState.asStateFlow()

    /** Freezes the row identity until this request is settled.
     * @param review Actual saved feedback.
     * @param component Selected rating or written text.
     * @param campsiteName Listing label for confirmation.
     * @param authorName Optional author label for confirmation.
     */
    fun request(
        review: Review,
        component: ReviewModerationComponent,
        campsiteName: String,
        authorName: String?,
    ) {
        // 防护条件：已关闭、没有管理权限、已经有请求在处理、评论不属于这个营地、
        // 或者要删的部分本来就没有，这些情况都直接忽略，不改变状态。
        if (closed || !canModerate() || state.value.phase != ReviewModerationPhase.IDLE ||
            review.campsiteId != campsiteId || review.authorId.isBlank() ||
            (component == ReviewModerationComponent.RATING && review.rating == null) ||
            (component == ReviewModerationComponent.TEXT && review.body == null)
        ) {
            return
        }
        submitted = false
        // 冻结目标：记住作者、营地和选中部分的原始值，之后确认和重试都只用这个目标。
        val target =
            ReviewModerationTarget(
                campsiteId,
                campsiteName,
                review.authorId,
                authorName,
                component,
                if (component == ReviewModerationComponent.RATING) review.rating else null,
                if (component == ReviewModerationComponent.TEXT) review.body else null,
            )
        mutableState.value = ReviewModerationState(ReviewModerationPhase.CONFIRMING, target)
        // 存进 SavedStateHandle，页面重建后还能回到这个确认对话框。
        retain(target)
    }

    /** Submits only the original explicitly confirmed component. */
    fun confirm() {
        // 只有在 CONFIRMING 状态才能提交，所以重复点击不会发出第二次删除。
        if (closed || state.value.phase != ReviewModerationPhase.CONFIRMING || !canModerate()) return
        execute(retry = false)
    }

    /** Every retry reads the original component before considering another write. */
    fun retry() {
        // 只有 FAILED 或 UNKNOWN 可以重试；重试也走 execute()，会先重新读取再决定要不要删。
        if (closed || !canModerate() ||
            state.value.phase !in setOf(ReviewModerationPhase.FAILED, ReviewModerationPhase.UNKNOWN)
        ) {
            return
        }
        execute(retry = true)
    }

    /** Closes confirmation or retains a failed request for later recovery. */
    fun dismiss() {
        // 失败或不确定时只隐藏对话框，保留请求，之后可以 resume()；
        // 确认中、完成或不可用时清掉保存的数据，回到 IDLE；正在删除时不能关闭。
        if (closed) return
        when (state.value.phase) {
            ReviewModerationPhase.FAILED, ReviewModerationPhase.UNKNOWN -> {
                mutableState.value =
                    state.value.copy(dialogVisible = false)
            }

            ReviewModerationPhase.CONFIRMING, ReviewModerationPhase.COMPLETE, ReviewModerationPhase.UNAVAILABLE -> {
                clearRetained()
                mutableState.value = ReviewModerationState()
            }

            else -> {
                Unit
            }
        }
    }

    /** Reopens retained recovery without writing. */
    fun resume() {
        // 只重新显示对话框，不会发出任何删除。
        if (!closed && canModerate()) mutableState.value = state.value.copy(dialogVisible = true)
    }

    /** Revalidates the original target before removing its selected component.
     * @param retry Whether a previously submitted request is being recovered.
     */
    private fun execute(retry: Boolean) {
        // 真正执行删除的地方：先改成 REMOVING（按钮会被禁用），再在协程里一步一步检查。
        val target = state.value.target ?: return
        mutableState.value = state.value.copy(phase = ReviewModerationPhase.REMOVING, dialogVisible = true)
        job =
            scope.launch {
                // attempted 表示是否已经调用了删除方法；出错时用它决定要不要重新读取核对。
                var attempted = false
                try {
                    // 检查 1：当前用户还是有效的管理员，营地还在而且没被删除，否则 UNAVAILABLE。
                    val actor = accounts.get(actorId)
                    val site = campsites.get(target.campsiteId)
                    ensureCurrent()
                    if (actor?.id != actorId || !actor.isActive || Role.ADMIN !in actor.roles ||
                        site?.id != campsiteId ||
                        site.removal != null ||
                        !canModerate()
                    ) {
                        phase(ReviewModerationPhase.UNAVAILABLE)
                        return@launch
                    }
                    // 检查 2：重新读取这位作者在这个营地的评论，找不到就 UNAVAILABLE。
                    val current = read(target)
                    ensureCurrent()
                    if (current == null) {
                        phase(ReviewModerationPhase.UNAVAILABLE)
                        return@launch
                    }
                    // 选中的部分已经没了：如果是重试而且之前已提交过，说明上次其实成功了 -> COMPLETE；
                    // 否则不是我们删的 -> UNAVAILABLE。
                    if (absent(current, target)) {
                        if (retry && submitted) complete() else phase(ReviewModerationPhase.UNAVAILABLE)
                        return@launch
                    }
                    // 检查 3：星级或文字必须和确认时一样；变了就不删，避免删掉作者新写的内容。
                    if (!matches(current, target) || !canModerate()) {
                        phase(ReviewModerationPhase.UNAVAILABLE)
                        return@launch
                    }
                    // 检查 4：读取期间权限可能变了，所以删除前再查一次管理员身份。
                    // Recheck actual privileges after the potentially suspended feedback read.
                    val latestActor = accounts.get(actorId)
                    ensureCurrent()
                    if (latestActor?.id != actorId || !latestActor.isActive || Role.ADMIN !in latestActor.roles ||
                        !canModerate()
                    ) {
                        phase(ReviewModerationPhase.UNAVAILABLE)
                        return@launch
                    }
                    // 发出删除前先把 submitted = true 保存起来，这样如果页面重建，就知道结果不确定。
                    attempted = true
                    submitted = true
                    retain(target)
                    // 只调用选中部分对应的删除方法：评分用 removeRating，文字用 removeReviewText。
                    when (target.component) {
                        ReviewModerationComponent.RATING -> {
                            moderation.removeRating(
                                target.campsiteId,
                                target.authorId,
                                actorId,
                            )
                        }

                        ReviewModerationComponent.TEXT -> {
                            // Clear only written feedback; the shared operation preserves saved stars.
                            moderation.removeReviewText(
                                target.campsiteId,
                                target.authorId,
                                actorId,
                            )
                        }
                    }
                    ensureCurrent()
                    complete()
                } catch (cancelled: CancellationException) {
                    // 协程被取消时要继续抛出，不能当成删除失败。
                    throw cancelled
                } catch (_: Exception) {
                    // 已经发出删除后出错：不直接重删，用 reconcile() 读取核对；还没发出就出错 -> UNKNOWN。
                    ensureCurrent()
                    if (attempted) reconcile(target) else phase(ReviewModerationPhase.UNKNOWN)
                }
            }
    }

    // 删除请求报错后调用：只读取不重写。已经没了 -> COMPLETE；原值还在 -> FAILED；
    // 内容变了 -> UNAVAILABLE；连读取也失败 -> UNKNOWN。

    /** Checks an uncertain response without replaying removal.
     * @param target Original selected feedback.
     */
    private suspend fun reconcile(target: ReviewModerationTarget) {
        try {
            val current = read(target)
            ensureCurrent()
            when {
                current == null -> phase(ReviewModerationPhase.UNAVAILABLE)
                absent(current, target) -> complete()
                matches(current, target) -> phase(ReviewModerationPhase.FAILED)
                else -> phase(ReviewModerationPhase.UNAVAILABLE)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ensureCurrent()
            phase(ReviewModerationPhase.UNKNOWN)
        }
    }

    // 从 repository 取一次当前评论列表，找同一营地、同一作者的那条评论。

    /** Reads the original author under the selected listing.
     * @param target Immutable feedback identity.
     */
    private suspend fun read(target: ReviewModerationTarget): Review? =
        reviews.observeReviews(target.campsiteId).first().firstOrNull {
            it.campsiteId == target.campsiteId && it.authorId == target.authorId
        }

    // 判断选中的那一部分现在是不是已经为空。

    /** Checks whether the selected component is already absent.
     * @param review Current saved feedback.
     * @param target Original selected component.
     */
    private fun absent(
        review: Review,
        target: ReviewModerationTarget,
    ) =
        if (target.component == ReviewModerationComponent.RATING) review.rating == null else review.body == null

    // 只比较选中的那一部分；另一部分被改了也不影响这次删除。

    /** Compares only the selected component with its confirmed value.
     * @param review Current saved feedback.
     * @param target Original confirmed value.
     */
    private fun matches(
        review: Review,
        target: ReviewModerationTarget,
    ) =
        if (target.component ==
            ReviewModerationComponent.RATING
        ) {
            review.rating == target.rating
        } else {
            review.body == target.text
        }

    // 成功：状态改成 COMPLETE，清掉保存的数据，并通知页面刷新评论。

    /** Refreshes feedback after confirmed removal and clears retained input. */
    private fun complete() {
        phase(ReviewModerationPhase.COMPLETE)
        clearRetained()
        onRemoved()
    }

    // 只改变阶段，保留原来的目标，方便显示和重试。

    /** Publishes an outcome while preserving the original selection.
     * @param phase New operation outcome.
     */
    private fun phase(phase: ReviewModerationPhase) {
        mutableState.value = state.value.copy(phase = phase)
    }

    // 页面关闭后丢弃迟到的结果，避免关闭后还去更新状态。

    /** Propagates cancellation and rejects late results after entry disposal. */
    private suspend fun ensureCurrent() {
        currentCoroutineContext().ensureActive()
        if (closed) throw CancellationException("Feedback owner closed")
    }

    // 把目标和 submitted 存成简单类型放进 SavedStateHandle，用于重建后恢复。

    /** Saves the original target and submission status for explicit recovery.
     * @param target Confirmed feedback selection.
     */
    private fun retain(target: ReviewModerationTarget) {
        saved["moderationActor"] = actorId
        saved["moderationSite"] = target.campsiteId
        saved["moderationSiteName"] = target.campsiteName
        saved["moderationAuthor"] = target.authorId
        saved["moderationAuthorName"] = target.authorName
        saved["moderationComponent"] = target.component.name
        saved["moderationRating"] = target.rating
        saved["moderationText"] = target.text
        saved["moderationSubmitted"] = submitted
    }

    // 重建时恢复保存的目标：只接受同一个管理员、同一个营地、数据完整的目标，否则当作没有。

    /** Restores only a valid target belonging to this actor and listing. */
    private fun restore(): ReviewModerationTarget? {
        /** Reads a retained string without coercion.
         * @param key Saved field name.
         */
        fun string(key: String) =
            saved.get<Any?>(key) as? String
        // 不是这个管理员或这个营地保存的请求，就不恢复。
        if (string("moderationActor") != actorId || string("moderationSite") != campsiteId) return null
        val component =
            ReviewModerationComponent.entries.firstOrNull { it.name == string("moderationComponent") } ?: return null
        val author = string("moderationAuthor")?.takeIf { it.isNotBlank() } ?: return null
        // 评分必须是 1 到 5，文字不能为空；选中的那一部分无效就放弃恢复。
        val rating = (saved.get<Any?>("moderationRating") as? Long)?.takeIf { it in 1..5 }
        val text = string("moderationText")?.takeIf { it.isNotBlank() }
        if ((component == ReviewModerationComponent.RATING && rating == null) ||
            (component == ReviewModerationComponent.TEXT && text == null)
        ) {
            return null
        }
        return ReviewModerationTarget(
            campsiteId,
            string("moderationSiteName").orEmpty(),
            author,
            string("moderationAuthorName"),
            component,
            rating,
            text,
        )
    }

    // 只删除 moderation 开头的键，不影响页面里其他保存的数据。

    /** Removes only this controller's saved request fields. */
    private fun clearRetained() {
        saved.keys().filter { it.startsWith("moderation") }.forEach { saved.remove<Any?>(it) }
        submitted = false
    }

    // 页面销毁时调用：标记已关闭，并取消正在进行的删除协程。

    /** Invalidates late reads/results before cancelling entry-owned work. */
    fun close() {
        closed = true
        job?.cancel()
    }
}
