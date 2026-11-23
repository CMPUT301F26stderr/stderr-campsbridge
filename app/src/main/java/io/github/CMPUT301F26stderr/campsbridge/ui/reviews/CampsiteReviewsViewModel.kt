/*
 * Owns one saved campsite's observed feedback and explicit independent author actions.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns one saved campsite's observed feedback and explicit independent author actions.
 * @param accountId Current actor identity.
 * @param campsiteId Saved campsite identity.
 * @param context Workspace presentation, separate from granted account roles.
 * @param campsites Required listing source.
 * @param reviews Feedback source and component edit operations.
 * @param accounts Current capabilities and supplemental author labels.
 * @param savedState Small retained text and interrupted-star primitives.
 */
class CampsiteReviewsViewModel(
    private val accountId: String,
    private val campsiteId: String,
    private val context: Role,
    private val campsites: CampsiteRepository,
    private val reviews: ReviewRepository,
    private val accounts: AccountRepository,
    private val savedState: SavedStateHandle,
    moderation: ModerationRepository? = null,
) : ViewModel() {
    private var closed = false
    private var acknowledgedRating: Long? = null
    private var requestedRating = savedState.get<Long>(RATING_KEY)?.takeIf { it in 1..5 }
    private val mutableState =
        MutableStateFlow(
            CampsiteReviewsUiState(
                textInput = savedState.get<String>(TEXT_KEY).orEmpty(),
                rating =
                    RatingEditState(
                        requestedRating,
                        if (requestedRating ==
                            null
                        ) {
                            RatingSaveStatus.NONE
                        } else {
                            RatingSaveStatus.NOT_SAVED
                        },
                    ),
            ),
        )
    val state = mutableState.asStateFlow()
    private var actor: Account? = null
    private var textTouched = savedState.get<Boolean>(TEXT_EDITED_KEY) == true
    private var textInitialized = false
    private var baselineText: String? = null
    private var leaving = false
    private var siteJob: Job? = null
    private var feedbackJob: Job? = null
    private var accountJob: Job? = null
    private val nameJobs = mutableMapOf<String, Job>()
    private val nameTokens = mutableMapOf<String, Long>()
    private var nextNameToken = 0L
    private val names = mutableMapOf<String, String?>()
    private val failedNames = mutableSetOf<String>()
    private val loadingNames = mutableSetOf<String>()

    /** Shared controller for rating and text actions on this saved feedback page. */
    val reviewModeration =
        moderation?.let {
            ReviewModerationController(
                accountId,
                campsiteId,
                accounts,
                campsites,
                reviews,
                it,
                savedState,
                viewModelScope,
                { !closed && !leaving && state.value.canModerate },
                { if (!closed && !leaving) observeFeedback() },
            )
        }

    init {
        observeListing()
        observeFeedback()
        observeAccount()
    }

    /** Retains a star draft without changing the saved record.
     * @param value Requested stars from 1 to 5.
     */
    fun selectRating(value: Long) {
        if (!state.value.canEdit || state.value.rating.status == RatingSaveStatus.SAVING || value !in 1..5 ||
            closed || leaving
        ) {
            return
        }
        requestedRating = value
        // 选星只改本地草稿，明确点击保存后才写入。
        savedState[RATING_KEY] = value
        mutableState.value = state.value.copy(rating = RatingEditState(value, RatingSaveStatus.DIRTY))
    }

    /** Saves only the current star draft; repeated requests cannot duplicate an in-flight write. */
    fun saveRating() {
        if (closed || leaving || !state.value.canEdit || state.value.rating.status == RatingSaveStatus.SAVING) return
        requestedRating?.let(::writeRating)
    }

    /** Retries the same unconfirmed choice without changing the written draft. */
    fun retryRating() {
        if (state.value.canEdit && state.value.rating.status == RatingSaveStatus.NOT_SAVED && !leaving && !closed) {
            requestedRating?.let(::writeRating)
        }
    }

    /** Retains raw text without submitting it or changing stars.
     * @param value Current multiline input.
     */
    fun editText(value: String) {
        if (!state.value.canEdit || state.value.textSaving || leaving || closed) return
        textTouched = true
        savedState[TEXT_EDITED_KEY] = true
        savedState[TEXT_KEY] = value
        mutableState.value =
            state.value.copy(textInput = value, textFailed = false, textSaved = false, confirmingClear = false)
    }

    /** Saves nonblank text or requests confirmation before clearing saved written feedback. */
    fun saveText() {
        if (!state.value.canEdit || state.value.textSaving || leaving || closed) return
        val body = state.value.textInput.takeIf { it.isNotBlank() }
        if (body == null && state.value.ownReview?.body != null) {
            mutableState.value = state.value.copy(confirmingClear = true)
        } else if (body != null) {
            writeText(body)
        }
    }

    /** Cancels an unsubmitted written-component removal. */
    fun dismissClear() {
        if (closed || leaving) return
        if (!state.value.textSaving) mutableState.value = state.value.copy(confirmingClear = false)
    }

    /** Removes only written feedback after an explicit eligible confirmation. */
    fun confirmClear() {
        if (state.value.canEdit && state.value.confirmingClear && !state.value.textSaving && !leaving &&
            !closed
        ) {
            writeText(null)
        }
    }

    /** Retries failed sources without replaying an author operation. */
    fun retry() {
        if (closed || leaving) return
        if (state.value.listing == CampsiteDetailUiState.Failure) observeListing()
        if (state.value.feedback == FeedbackRead.Failure) observeFeedback()
        if (state.value.accountFailed) observeAccount()
    }

    /** Retries only failed author labels, leaving readable saved feedback in place. */
    fun retryNames() {
        if (closed || leaving) return
        for (id in failedNames.toList()) {
            names.remove(id)
            failedNames.remove(id)
        }
        val rows = (state.value.feedback as? FeedbackRead.Content)?.reviews.orEmpty()
        updateNames(rows.map { it.authorId }.toSet())
    }

    /** Permits one ordinary Back or requests confirmation for dirty star or written input. */
    fun requestBack(): Boolean {
        if (closed || leaving || isSubmitting()) return false
        if (dirtyText() || requestedRating != null) {
            mutableState.value = state.value.copy(confirmingDiscard = true)
            return false
        }
        leaving = true
        return true
    }

    /** Keeps the raw draft after cancelling exit confirmation. */
    fun dismissDiscard() {
        if (closed || leaving) return
        mutableState.value = state.value.copy(confirmingDiscard = false)
    }

    /** Discards local star and written drafts; an already saved rating remains saved. */
    fun confirmDiscard(): Boolean {
        if (!state.value.confirmingDiscard || isSubmitting() || leaving || closed) return false
        requestedRating = null
        savedState.remove<Long>(RATING_KEY)
        val text = baselineText.orEmpty()
        savedState[TEXT_KEY] = text
        mutableState.value =
            state.value.copy(
                textInput = text,
                confirmingDiscard = false,
                rating =
                    RatingEditState(acknowledgedRating ?: state.value.ownReview?.rating),
            )
        leaving = true
        return true
    }

    /** Keeps interrupted stars unconfirmed until an explicit idempotent retry succeeds.
     * @param value Frozen requested value.
     */
    private fun writeRating(value: Long) {
        savedState[RATING_KEY] = value
        mutableState.value = state.value.copy(rating = RatingEditState(value, RatingSaveStatus.SAVING))
        viewModelScope.launch {
            try {
                reviews.setRating(campsiteId, accountId, value)
                currentCoroutineContext().ensureActive()
                if (closed) return@launch
                requestedRating = null
                acknowledgedRating = value
                savedState.remove<Long>(RATING_KEY)
                mutableState.value = state.value.copy(rating = RatingEditState(value, RatingSaveStatus.SAVED))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(rating = RatingEditState(value, RatingSaveStatus.NOT_SAVED))
            }
        }
    }

    /** Freezes one exact body; only the operation response confirms this save.
     * @param body Nonblank raw body, or null for confirmed text removal.
     */
    private fun writeText(body: String?) {
        textTouched = true
        savedState[TEXT_EDITED_KEY] = true
        mutableState.value =
            state.value.copy(textSaving = true, textFailed = false, textSaved = false, confirmingClear = false)
        viewModelScope.launch {
            try {
                reviews.setReviewText(campsiteId, accountId, body)
                currentCoroutineContext().ensureActive()
                baselineText = body
                mutableState.value = state.value.copy(textSaving = false, textSaved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(textSaving = false, textFailed = true)
            }
        }
    }

    /** Observes the actual listing without substituting fixture or copied navigation content. */
    private fun observeListing() {
        siteJob?.cancel()
        mutableState.value = state.value.copy(listing = CampsiteDetailUiState.Loading)
        publish()
        siteJob =
            viewModelScope.launch {
                try {
                    campsites.observe(campsiteId).collect { site ->
                        currentCoroutineContext().ensureActive()
                        val readable =
                            site != null && site.id == campsiteId &&
                                (
                                    site.isPublished || context == Role.ADMIN ||
                                        (context == Role.OWNER && site.ownerId == accountId)
                                )
                        mutableState.value =
                            state.value.copy(
                                listing =
                                    if (readable) {
                                        CampsiteDetailUiState.Content(
                                            checkNotNull(site),
                                        )
                                    } else {
                                        CampsiteDetailUiState.Unavailable
                                    },
                            )
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    mutableState.value = state.value.copy(listing = CampsiteDetailUiState.Failure)
                    publish()
                }
            }
    }

    /** Updates saved rows while keeping explicit editor choices local to this page. */
    private fun observeFeedback() {
        feedbackJob?.cancel()
        mutableState.value = state.value.copy(feedback = FeedbackRead.Loading)
        publish()
        feedbackJob =
            viewModelScope.launch {
                try {
                    reviews.observeReviews(campsiteId).collect { incoming ->
                        currentCoroutineContext().ensureActive()
                        // 后续订阅结果仍是真实记录，不能一直被旧的保存值遮住。
                        acknowledgedRating = null
                        val rows = incoming.filter { it.campsiteId == campsiteId }
                        val own = rows.firstOrNull { it.authorId == accountId }
                        val raw =
                            when {
                                !textInitialized && textTouched -> savedState.get<String>(TEXT_KEY).orEmpty()
                                !textTouched && !state.value.textSaving -> own?.body.orEmpty()
                                else -> state.value.textInput
                            }
                        if (!textInitialized || !textTouched) baselineText = own?.body
                        textInitialized = true
                        mutableState.value =
                            state.value.copy(
                                feedback =
                                    FeedbackRead.Content(
                                        rows.filter {
                                            it.rating != null ||
                                                it.body != null
                                        },
                                    ),
                                ownReview = own,
                                textInput = raw,
                            )
                        savedState[TEXT_KEY] = raw
                        publish()
                        updateNames(rows.filter { it.rating != null || it.body != null }.map { it.authorId }.toSet())
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    mutableState.value = state.value.copy(feedback = FeedbackRead.Failure)
                    publish()
                }
            }
    }

    /** Keeps author actions dependent on actual current capabilities rather than workspace alone. */
    private fun observeAccount() {
        accountJob?.cancel()
        actor = null
        mutableState.value = state.value.copy(accountFailed = false)
        publish()
        accountJob =
            viewModelScope.launch {
                try {
                    accounts.observe(accountId).collect { record ->
                        currentCoroutineContext().ensureActive()
                        actor = record?.takeIf { it.id == accountId && it.isActive }
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    actor = null
                    mutableState.value = state.value.copy(accountFailed = true)
                    publish()
                }
            }
    }

    /** Loads labels independently; failure or absence cannot drop saved feedback.
     * @param ids Actual authors referenced by the visible saved components.
     */
    private fun updateNames(ids: Set<String>) {
        if (closed || leaving) return
        for (id in (names.keys + loadingNames).filter { it !in ids }) {
            nameTokens.remove(id)
            nameJobs.remove(id)?.cancel()
            names.remove(id)
            failedNames.remove(id)
            loadingNames.remove(id)
        }
        for (id in ids.filter { it !in names && it !in loadingNames }) {
            loadingNames.add(id)
            val token = ++nextNameToken
            nameTokens[id] = token
            val pending =
                viewModelScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val account = accounts.get(id)
                        currentCoroutineContext().ensureActive()
                        if (!closed &&
                            nameTokens[id] == token
                        ) {
                            names[id] = account?.takeIf { it.id == id && it.isActive }?.name
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        if (!closed && nameTokens[id] == token) {
                            names[id] = null
                            failedNames.add(id)
                        }
                    } finally {
                        if (!closed && nameTokens[id] == token) {
                            loadingNames.remove(id)
                            nameJobs.remove(id)
                            publish()
                        }
                    }
                }
            nameJobs[id] = pending
            pending.start()
        }
        publish()
    }

    /** Projects eligibility and observed stars without performing any write. */
    private fun publish() {
        if (closed) return
        val current = state.value
        val site = (current.listing as? CampsiteDetailUiState.Content)?.campsite
        val known = current.feedback is FeedbackRead.Content
        mutableState.value =
            current.copy(
                canEdit =
                    context == Role.ENTRANT && actor?.roles?.contains(
                        Role.ENTRANT,
                    ) == true && site?.isPublished == true &&
                        known,
                isAuthor = context == Role.ENTRANT,
                canModerate =
                    context == Role.ADMIN && actor?.roles?.contains(Role.ADMIN) == true && site != null && known,
                rating =
                    current.rating.copy(
                        value = requestedRating ?: acknowledgedRating ?: current.ownReview?.rating,
                    ),
                authorNames = names.toMap(),
                failedNames = failedNames.toSet(),
                loadingNames = loadingNames.toSet(),
            )
    }

    /** Treats blank written input as component absence without trimming nonblank saved content. */
    private fun dirtyText(): Boolean =
        state.value.textInput.takeIf { it.isNotBlank() } != baselineText

    /** Keeps Back input in the shared frame while either independent operation is pending. */
    private fun isSubmitting(): Boolean =
        state.value.textSaving || state.value.rating.status == RatingSaveStatus.SAVING ||
            reviewModeration?.state?.value?.phase == ReviewModerationPhase.REMOVING

    /** Invalidates labels before cancellation so late completions cannot publish into a closed entry. */
    override fun onCleared() {
        closed = true
        reviewModeration?.close()
        leaving = true
        // 先让回调失效，再取消任务，避免 finally 发布过期状态。
        nameTokens.clear()
        siteJob?.cancel()
        feedbackJob?.cancel()
        accountJob?.cancel()
        nameJobs.values.toList().forEach { it.cancel() }
        nameJobs.clear()
        super.onCleared()
    }

    /** Entry-scoped text and unconfirmed-star values, without automatic operation replay. */
    private companion object {
        const val TEXT_KEY = "reviewText"
        const val TEXT_EDITED_KEY = "reviewTextEdited"
        const val RATING_KEY = "pendingRating"
    }
}
