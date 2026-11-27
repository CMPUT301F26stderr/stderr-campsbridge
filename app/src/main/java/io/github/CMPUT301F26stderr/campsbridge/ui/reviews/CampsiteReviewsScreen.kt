/*
 * Displays saved feedback and separate author actions without owning persistence.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.RatingState
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components.ratingLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.summarizeRatings
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.reviews.components.RatingInput
import io.github.CMPUT301F26stderr.campsbridge.ui.reviews.components.ReviewActionStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.reviews.components.ReviewRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Displays saved feedback and separate author actions without owning persistence.
 * @param state Current saved sources, raw text and independent operation feedback.
 * @param onBack Requests ordinary Back or dirty-text confirmation.
 * @param onRetry Retries failed required sources without replaying writes.
 * @param onRetryNames Retries supplemental author labels.
 * @param onRating Retains a local star draft.
 * @param onSaveRating Saves only the chosen stars.
 * @param onRetryRating Retries the retained unconfirmed value.
 * @param onText Retains raw multiline input locally.
 * @param onSaveText Requests Post/Save or confirmed text clearing.
 * @param onConfirmClear Removes only the author's written component.
 * @param onDismissClear Cancels unsubmitted text clearing.
 * @param onConfirmDiscard Discards only local written input and returns.
 * @param onDismissDiscard Keeps the written draft.
 * @param reviewActions Optional admin-owned actions for each actual saved record.
 */
@Composable
fun CampsiteReviewsScreen(
    state: CampsiteReviewsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryNames: () -> Unit,
    onRating: (Long) -> Unit,
    onSaveRating: () -> Unit,
    onRetryRating: () -> Unit,
    onText: (String) -> Unit,
    onSaveText: () -> Unit,
    onConfirmClear: () -> Unit,
    onDismissClear: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    reviewActions: @Composable ((Review) -> Unit)? = null,
) {
    val pendingRating = state.rating.status == RatingSaveStatus.SAVING
    AppScreen(
        stringResource(R.string.reviews_page_title),
        onBack = onBack,
        backEnabled = !pendingRating && !state.textSaving,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when (val listing = state.listing) {
                CampsiteDetailUiState.Loading -> {
                    LoadingContent()
                }

                CampsiteDetailUiState.Failure -> {
                    FailureContent(stringResource(R.string.campsite_detail_failure), onRetry)
                }

                CampsiteDetailUiState.Unavailable -> {
                    Text(stringResource(R.string.campsite_unavailable))
                }

                is CampsiteDetailUiState.Content -> {
                    Text(listing.campsite.details.name, style = MaterialTheme.typography.headlineSmall)
                    val rating =
                        when (val feedback = state.feedback) {
                            FeedbackRead.Loading -> RatingState.Loading
                            FeedbackRead.Failure -> RatingState.Failure
                            is FeedbackRead.Content -> RatingState.Ready(summarizeRatings(feedback.reviews))
                        }
                    Text(ratingLabel(rating), style = MaterialTheme.typography.titleMedium)
                    if (state.accountFailed) FailureContent(stringResource(R.string.reviews_account_failed), onRetry)
                    if (state.isAuthor) {
                        SummarySection(stringResource(R.string.reviews_your_rating)) {
                            RatingInput(state.rating.value, onRating, enabled = state.canEdit && !pendingRating)
                            when (state.rating.status) {
                                RatingSaveStatus.NONE -> {}

                                RatingSaveStatus.DIRTY -> {
                                    AppActionButton(
                                        stringResource(R.string.reviews_rating_save),
                                        onSaveRating,
                                        Modifier.fillMaxWidth(),
                                        enabled = state.canEdit,
                                    )
                                }

                                RatingSaveStatus.SAVING -> {
                                    ReviewActionStatus(stringResource(R.string.reviews_rating_saving))
                                }

                                RatingSaveStatus.SAVED -> {
                                    ReviewActionStatus(stringResource(R.string.reviews_rating_saved))
                                }

                                RatingSaveStatus.NOT_SAVED -> {
                                    ReviewActionStatus(stringResource(R.string.reviews_rating_failed))
                                    TextButton(
                                        onRetryRating,
                                        enabled = state.canEdit,
                                    ) { Text(stringResource(R.string.reviews_rating_retry)) }
                                }
                            }
                        }
                        SummarySection(stringResource(R.string.reviews_your_text)) {
                            AppTextField(
                                state.textInput,
                                onText,
                                stringResource(R.string.reviews_text_label),
                                Modifier.testTag("review-text"),
                                enabled =
                                    state.canEdit && !state.textSaving,
                                singleLine = false,
                                minLines = 3,
                                maxLines = 6,
                            )
                            if (state.textFailed) ReviewActionStatus(stringResource(R.string.reviews_text_failed))
                            if (state.textSaved) ReviewActionStatus(stringResource(R.string.reviews_text_saved))
                            AppActionButton(
                                stringResource(
                                    when {
                                        state.textSaving -> R.string.reviews_text_saving
                                        state.textFailed -> R.string.reviews_text_retry
                                        state.ownReview?.body != null -> R.string.reviews_text_save
                                        else -> R.string.reviews_text_post
                                    },
                                ),
                                onSaveText,
                                Modifier.fillMaxWidth(),
                                enabled =
                                    state.canEdit && (state.textInput.isNotBlank() || state.ownReview?.body != null),
                                isSubmitting = state.textSaving,
                                progressDescription = stringResource(R.string.reviews_text_saving),
                            )
                        }
                    }
                    Text(stringResource(R.string.reviews_saved_feedback), style = MaterialTheme.typography.titleLarge)
                    if (state.failedNames.isNotEmpty()) {
                        FailureContent(
                            stringResource(R.string.reviews_names_failed),
                            onRetryNames,
                        )
                    }
                    when (val feedback = state.feedback) {
                        FeedbackRead.Loading -> {
                            LoadingContent()
                        }

                        FeedbackRead.Failure -> {
                            FailureContent(stringResource(R.string.reviews_load_failed), onRetry)
                        }

                        is FeedbackRead.Content -> {
                            if (feedback.reviews.isEmpty()) EmptyContent(stringResource(R.string.reviews_empty))
                            for (review in feedback.reviews) {
                                ReviewRow(
                                    review,
                                    if (review.authorId in
                                        state.loadingNames
                                    ) {
                                        stringResource(R.string.reviews_author_loading)
                                    } else {
                                        state.authorNames[review.authorId]
                                    },
                                    actions = if (state.canModerate) reviewActions else null,
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.confirmingClear) {
        ConfirmationDialog(
            stringResource(R.string.reviews_clear_title),
            stringResource(R.string.reviews_clear_body),
            onConfirmClear,
            onDismissClear,
            onDismissClear,
            confirmLabel = stringResource(R.string.reviews_clear_confirm),
            destructive = true,
        )
    }
    if (state.confirmingDiscard) {
        ConfirmationDialog(
            stringResource(R.string.reviews_discard_title),
            stringResource(R.string.reviews_discard_all_body),
            onConfirmDiscard,
            onDismissDiscard,
            onDismissDiscard,
            confirmLabel = stringResource(R.string.booking_discard),
            cancelLabel = stringResource(R.string.booking_keep_draft),
            destructive = true,
        )
    }
}
