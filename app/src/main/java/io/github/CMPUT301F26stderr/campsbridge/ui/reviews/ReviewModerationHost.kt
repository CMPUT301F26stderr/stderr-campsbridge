package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Offers independent destructive actions for the components present in a saved row.
 * @param review Actual saved feedback.
 * @param enabled Whether the shared operation is idle.
 * @param onRemove Requests confirmation for one selected component.
 */
@Composable
fun ReviewModerationActions(
    review: Review,
    enabled: Boolean,
    onRemove: (ReviewModerationComponent) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        for (component in ReviewModerationComponent.entries) {
            if (component == ReviewModerationComponent.RATING && review.rating == null) continue
            if (component == ReviewModerationComponent.TEXT && review.body == null) continue
            TextButton(
                onClick = { onRemove(component) },
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    stringResource(
                        if (component ==
                            ReviewModerationComponent.RATING
                        ) {
                            R.string.moderation_remove_rating
                        } else {
                            R.string.moderation_remove_text
                        },
                    ),
                )
            }
        }
    }
}

/** Shows the original target and confirmed or recoverable removal outcome.
 * @param state Retained target and operation phase.
 * @param onConfirm Confirms the selected component removal.
 * @param onDismiss Cancels confirmation or closes settled feedback.
 * @param onRetry Rechecks the original component before retrying.
 * @param onResume Reopens a dismissed recovery dialog.
 */
@Composable
fun ReviewModerationHost(
    state: ReviewModerationState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onResume: () -> Unit,
) {
    val target = state.target ?: return
    if (!state.dialogVisible) {
        TextButton(onClick = onResume) { Text(stringResource(R.string.review_moderation_resume)) }
        return
    }
    val rating = target.component == ReviewModerationComponent.RATING
    val title = stringResource(if (rating) R.string.moderation_rating_question else R.string.moderation_text_question)
    val busy = state.phase == ReviewModerationPhase.REMOVING
    if (state.phase == ReviewModerationPhase.CONFIRMING || busy) {
        ConfirmationDialog(
            title = title,
            description =
                stringResource(
                    if (rating) R.string.moderation_rating_body else R.string.moderation_text_body,
                    target.authorName ?: target.authorId,
                ),
            onConfirm = onConfirm,
            onCancel = onDismiss,
            onDismiss = onDismiss,
            isSubmitting = busy,
            confirmLabel = stringResource(R.string.remove),
            destructive = true,
            progressDescription = stringResource(R.string.moderation_progress),
            supportingContent = { ReviewModerationIdentity(target) },
        )
        return
    }
    val retryable = state.phase == ReviewModerationPhase.FAILED || state.phase == ReviewModerationPhase.UNKNOWN
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                ReviewModerationIdentity(target)
                Text(
                    stringResource(
                        when (state.phase) {
                            ReviewModerationPhase.FAILED -> R.string.review_moderation_failed
                            ReviewModerationPhase.UNKNOWN -> R.string.review_moderation_unknown
                            ReviewModerationPhase.COMPLETE -> R.string.review_moderation_done
                            else -> R.string.review_moderation_unavailable
                        },
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = if (retryable) onRetry else onDismiss) {
                Text(
                    stringResource(
                        if (retryable) R.string.review_moderation_retry else R.string.review_moderation_close,
                    ),
                )
            }
        },
        dismissButton = {
            if (retryable) {
                TextButton(
                    onClick = onDismiss,
                ) { Text(stringResource(R.string.review_moderation_close)) }
            }
        },
    )
}

/** Identifies the selected saved feedback without implying removal of its other component.
 * @param target Original campsite, author and selected stars or text.
 */
@Composable
private fun ReviewModerationIdentity(target: ReviewModerationTarget) {
    Text(stringResource(R.string.review_moderation_site, target.campsiteName))
    Text(stringResource(R.string.review_moderation_site_id, target.campsiteId))
    target.authorName?.let { Text(it) }
    Text(stringResource(R.string.review_moderation_author, target.authorId))
    if (target.component == ReviewModerationComponent.RATING) {
        Text(stringResource(R.string.review_moderation_stars, target.rating ?: 0))
    } else {
        target.text?.let { Text(it) }
    }
}
