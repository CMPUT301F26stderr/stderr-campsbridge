package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Both independent components use the same retained target and confirmation host. */
@Composable
fun ReviewModerationActions(
    controller: ReviewModerationController,
    review: Review,
    campsiteName: String,
    authorName: String?,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    for (component in ReviewModerationComponent.entries) {
        if (component == ReviewModerationComponent.RATING && review.rating == null) continue
        if (component == ReviewModerationComponent.TEXT && review.body == null) continue
        TextButton(
            onClick = { controller.request(review, component, campsiteName, authorName) },
            enabled = state.phase == ReviewModerationPhase.IDLE,
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

@Composable
fun ReviewModerationHost(controller: ReviewModerationController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val target = state.target ?: return
    if (!state.dialogVisible) {
        TextButton(onClick = controller::resume) { Text(stringResource(R.string.review_moderation_resume)) }
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
            onConfirm = controller::confirm,
            onCancel = controller::dismiss,
            onDismiss = controller::dismiss,
            isSubmitting = busy,
            confirmLabel = stringResource(R.string.review_moderation_confirm),
            destructive = true,
            progressDescription = stringResource(R.string.moderation_progress),
            supportingContent = { ReviewModerationIdentity(target) },
        )
        return
    }
    val retryable = state.phase == ReviewModerationPhase.FAILED || state.phase == ReviewModerationPhase.UNKNOWN
    AlertDialog(
        onDismissRequest = controller::dismiss,
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
            TextButton(onClick = if (retryable) controller::retry else controller::dismiss) {
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
                    onClick = controller::dismiss,
                ) { Text(stringResource(R.string.review_moderation_close)) }
            }
        },
    )
}

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
