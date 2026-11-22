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

enum class ReviewModerationComponent { RATING, TEXT }

enum class ReviewModerationPhase { IDLE, CONFIRMING, REMOVING, FAILED, UNKNOWN, UNAVAILABLE, COMPLETE }

/** Immutable identity and selected component; the other component is never submitted. */
data class ReviewModerationTarget(
    val campsiteId: String,
    val campsiteName: String,
    val authorId: String,
    val authorName: String?,
    val component: ReviewModerationComponent,
    val rating: Long?,
    val text: String?,
)

data class ReviewModerationState(
    val phase: ReviewModerationPhase = ReviewModerationPhase.IDLE,
    val target: ReviewModerationTarget? = null,
    val dialogVisible: Boolean = true,
)

/** One feedback-entry owner for independently confirmed rating and written-text moderation. */
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
    private var submitted = saved.get<Any?>("moderationSubmitted") == true
    private val restored = restore()
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

    /** Freezes the actual row identity; later row selection cannot replace a pending request. */
    fun request(
        review: Review,
        component: ReviewModerationComponent,
        campsiteName: String,
        authorName: String?,
    ) {
        if (closed || !canModerate() || state.value.phase != ReviewModerationPhase.IDLE ||
            review.campsiteId != campsiteId || review.authorId.isBlank() ||
            (component == ReviewModerationComponent.RATING && review.rating == null) ||
            (component == ReviewModerationComponent.TEXT && review.body == null)
        ) {
            return
        }
        submitted = false
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
        retain(target)
    }

    fun confirm() {
        if (closed || state.value.phase != ReviewModerationPhase.CONFIRMING || !canModerate()) return
        execute(retry = false)
    }

    /** Every retry reads the original component before considering another write. */
    fun retry() {
        if (closed || !canModerate() ||
            state.value.phase !in setOf(ReviewModerationPhase.FAILED, ReviewModerationPhase.UNKNOWN)
        ) {
            return
        }
        execute(retry = true)
    }

    fun dismiss() {
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

    fun resume() {
        if (!closed && canModerate()) mutableState.value = state.value.copy(dialogVisible = true)
    }

    private fun execute(retry: Boolean) {
        val target = state.value.target ?: return
        mutableState.value = state.value.copy(phase = ReviewModerationPhase.REMOVING, dialogVisible = true)
        job =
            scope.launch {
                var attempted = false
                try {
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
                    val current = read(target)
                    ensureCurrent()
                    if (current == null) {
                        phase(ReviewModerationPhase.UNAVAILABLE)
                        return@launch
                    }
                    if (absent(current, target)) {
                        if (retry && submitted) complete() else phase(ReviewModerationPhase.UNAVAILABLE)
                        return@launch
                    }
                    if (!matches(current, target) || !canModerate()) {
                        phase(ReviewModerationPhase.UNAVAILABLE)
                        return@launch
                    }
                    // Recheck actual privileges after the potentially suspended feedback read.
                    val latestActor = accounts.get(actorId)
                    ensureCurrent()
                    if (latestActor?.id != actorId || !latestActor.isActive || Role.ADMIN !in latestActor.roles ||
                        !canModerate()
                    ) {
                        phase(ReviewModerationPhase.UNAVAILABLE)
                        return@launch
                    }
                    attempted = true
                    submitted = true
                    retain(target)
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
                    throw cancelled
                } catch (_: Exception) {
                    ensureCurrent()
                    if (attempted) reconcile(target) else phase(ReviewModerationPhase.UNKNOWN)
                }
            }
    }

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

    private suspend fun read(target: ReviewModerationTarget): Review? =
        reviews.observeReviews(target.campsiteId).first().firstOrNull {
            it.campsiteId == target.campsiteId && it.authorId == target.authorId
        }

    private fun absent(
        review: Review,
        target: ReviewModerationTarget,
    ) =
        if (target.component == ReviewModerationComponent.RATING) review.rating == null else review.body == null

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

    private fun complete() {
        phase(ReviewModerationPhase.COMPLETE)
        clearRetained()
        onRemoved()
    }

    private fun phase(phase: ReviewModerationPhase) {
        mutableState.value = state.value.copy(phase = phase)
    }

    private suspend fun ensureCurrent() {
        currentCoroutineContext().ensureActive()
        if (closed) throw CancellationException("Feedback owner closed")
    }

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

    private fun restore(): ReviewModerationTarget? {
        fun string(key: String) =
            saved.get<Any?>(key) as? String
        if (string("moderationActor") != actorId || string("moderationSite") != campsiteId) return null
        val component =
            ReviewModerationComponent.entries.firstOrNull { it.name == string("moderationComponent") } ?: return null
        val author = string("moderationAuthor")?.takeIf { it.isNotBlank() } ?: return null
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

    private fun clearRetained() {
        saved.keys().filter { it.startsWith("moderation") }.forEach { saved.remove<Any?>(it) }
        submitted = false
    }

    /** Invalidates late reads/results before cancelling entry-owned work. */
    fun close() {
        closed = true
        job?.cancel()
    }
}
