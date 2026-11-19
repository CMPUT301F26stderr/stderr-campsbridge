package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
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
) : ViewModel() {
    private val mutableState = MutableStateFlow(CampsiteReviewsUiState())
    val state = mutableState.asStateFlow()

    /** Retains a local star choice.
     * @param value Tentative stars.
     */
    fun selectRating(value: Long) =
        Unit

    /** Saves the chosen stars explicitly. */
    fun saveRating() =
        Unit

    /** Retries an interrupted star save. */
    fun retryRating() =
        Unit

    /** Retains written input.
     * @param value Raw draft.
     */
    fun editText(value: String) =
        Unit

    /** Saves only written feedback. */
    fun saveText() =
        Unit

    /** Keeps the saved written component. */
    fun dismissClear() =
        Unit

    /** Confirms written removal. */
    fun confirmClear() =
        Unit

    /** Retries failed required sources. */
    fun retry() =
        Unit

    /** Retries supplemental author labels. */
    fun retryNames() =
        Unit

    /** Requests exit with draft protection. */
    fun requestBack(): Boolean =
        false

    /** Keeps local input. */
    fun dismissDiscard() =
        Unit

    /** Discards local input. */
    fun confirmDiscard(): Boolean =
        false
}
