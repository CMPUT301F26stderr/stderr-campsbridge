/*
 * Connects the saved feedback owner to explicit actions and the shared Back callback.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailUiState

/** Connects the saved feedback owner to explicit actions and the shared Back callback.
 * @param model Entry-scoped feedback/input owner.
 * @param onBack Pops to the retained saved campsite reader.
 */
@Composable
fun CampsiteReviewsRoute(
    model: CampsiteReviewsViewModel,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val moderation = model.reviewModeration
    val moderationState = moderation?.state?.collectAsStateWithLifecycle()?.value
    CampsiteReviewsScreen(
        state,
        onBack = { if (model.requestBack()) onBack() },
        onRetry = model::retry,
        onRetryNames = model::retryNames,
        onRating = model::selectRating,
        onSaveRating = model::saveRating,
        onRetryRating = model::retryRating,
        onText = model::editText,
        onSaveText = model::saveText,
        onConfirmClear = model::confirmClear,
        onDismissClear = model::dismissClear,
        onConfirmDiscard = { if (model.confirmDiscard()) onBack() },
        onDismissDiscard = model::dismissDiscard,
        reviewActions =
            moderation?.let { controller ->
                { review ->
                    ReviewModerationActions(
                        review = review,
                        enabled = moderationState?.phase == ReviewModerationPhase.IDLE,
                        onRemove = { component ->
                            controller.request(
                                review,
                                component,
                                (state.listing as? CampsiteDetailUiState.Content)
                                    ?.campsite
                                    ?.details
                                    ?.name
                                    .orEmpty(),
                                state.authorNames[review.authorId],
                            )
                        },
                    )
                }
            },
    )
    if (moderation != null && moderationState != null) {
        ReviewModerationHost(
            moderationState,
            moderation::confirm,
            moderation::dismiss,
            moderation::retry,
            moderation::resume,
        )
    }
}
