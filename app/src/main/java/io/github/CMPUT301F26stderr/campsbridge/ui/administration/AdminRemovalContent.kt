/*
 * Presents the original target's confirmation, progress and settled feedback.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Presents the original target's confirmation, progress and settled feedback.
 * @param state Retained request and repository-confirmed outcome.
 * @param onConfirm Confirms the displayed target.
 * @param onRetry Retries or checks the same original request.
 * @param onDismiss Closes feedback without cancelling committed cleanup.
 */
@Composable
fun AdminRemovalContent(
    state: AdminRemovalUiState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val target = state.request?.target ?: return
    if (state.phase == AdminRemovalPhase.IDLE || state.phase == AdminRemovalPhase.ROOT_HANDOFF) return
    val name =
        when (target) {
            is AdminRemovalTarget.Account -> target.id
            is AdminRemovalTarget.Campsite -> target.id
            is AdminRemovalTarget.Image -> target.target.id
        }
    val title =
        when (target) {
            is AdminRemovalTarget.Account -> {
                when (state.phase) {
                    AdminRemovalPhase.CONFIRMING -> R.string.admin_remove_profile_question
                    AdminRemovalPhase.REMOVING, AdminRemovalPhase.CHECKING -> R.string.account_removal_progress
                    AdminRemovalPhase.COMPLETE -> R.string.admin_remove_profile_done
                    else -> R.string.admin_remove_retry_title
                }
            }

            is AdminRemovalTarget.Campsite -> {
                when (state.phase) {
                    AdminRemovalPhase.CONFIRMING -> R.string.admin_remove_site_question
                    AdminRemovalPhase.REMOVING, AdminRemovalPhase.CHECKING -> R.string.admin_remove_site_progress
                    AdminRemovalPhase.COMPLETE -> R.string.admin_remove_site_done
                    else -> R.string.admin_remove_retry_title
                }
            }

            is AdminRemovalTarget.Image -> {
                when (state.phase) {
                    AdminRemovalPhase.CONFIRMING -> R.string.admin_remove_image_question
                    AdminRemovalPhase.REMOVING, AdminRemovalPhase.CHECKING -> R.string.admin_remove_image_progress
                    AdminRemovalPhase.COMPLETE -> R.string.admin_remove_image_done
                    else -> R.string.admin_remove_retry_title
                }
            }
        }
    if (state.phase == AdminRemovalPhase.CONFIRMING) {
        val body =
            when (target) {
                is AdminRemovalTarget.Account -> R.string.admin_remove_profile_body
                is AdminRemovalTarget.Campsite -> R.string.admin_remove_site_body
                is AdminRemovalTarget.Image -> R.string.admin_remove_image_body
            }
        ConfirmationDialog(
            stringResource(title),
            stringResource(body, name),
            onConfirm,
            onDismiss,
            onDismiss,
            confirmLabel = stringResource(R.string.remove),
            destructive = true,
        )
        return
    }
    val pending = state.phase in setOf(AdminRemovalPhase.REMOVING, AdminRemovalPhase.CHECKING)
    val complete = state.phase == AdminRemovalPhase.COMPLETE
    val closeOnly = complete || state.phase == AdminRemovalPhase.UNAVAILABLE
    AlertDialog(
        onDismissRequest = { if (!pending) onDismiss() },
        title = { Text(stringResource(title)) },
        text = {
            if (pending) {
                LoadingContent(
                    if (state.phase == AdminRemovalPhase.CHECKING) {
                        stringResource(R.string.admin_removal_checking)
                    } else {
                        stringResource(R.string.admin_remove_wait, name)
                    },
                )
            } else {
                Text(
                    when {
                        state.cleanupBlobId != null -> {
                            stringResource(R.string.admin_remove_image_cleanup)
                        }

                        state.phase == AdminRemovalPhase.CLEANUP_PENDING -> {
                            stringResource(R.string.admin_removal_record_cleanup_pending)
                        }

                        state.phase == AdminRemovalPhase.UNKNOWN -> {
                            stringResource(R.string.admin_removal_unknown)
                        }

                        state.phase == AdminRemovalPhase.UNAVAILABLE -> {
                            stringResource(
                                when (state.problem) {
                                    AdminRemovalProblem.MISSING -> R.string.admin_removal_missing
                                    AdminRemovalProblem.CHANGED -> R.string.admin_removal_changed
                                    else -> R.string.admin_removal_denied
                                },
                            )
                        }

                        else -> {
                            stringResource(
                                if (complete) R.string.admin_remove_confirmed else R.string.admin_remove_failed,
                                name,
                            )
                        }
                    },
                    Modifier.verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            if (!pending) {
                TextButton(if (closeOnly) onDismiss else onRetry) {
                    Text(
                        stringResource(
                            when {
                                closeOnly -> R.string.notification_entry_close
                                state.phase == AdminRemovalPhase.UNKNOWN -> R.string.admin_removal_check_status
                                else -> R.string.admin_remove_retry
                            },
                        ),
                    )
                }
            }
        },
        dismissButton = {
            if (!pending && !closeOnly) {
                TextButton(onDismiss) { Text(stringResource(R.string.notification_entry_close)) }
            }
        },
    )
}

/** Shows one passive request without constructing an operation owner.
 * @param phase Confirmation, progress or settled feedback.
 * @param target Original inspected identity.
 */
@Composable
private fun RemovalPreview(
    phase: AdminRemovalPhase,
    target: AdminRemovalTarget,
) {
    CampsbridgeTheme {
        AdminRemovalContent(
            AdminRemovalUiState(
                phase,
                AdminRemovalRequest("preview-admin", target),
                committed =
                    phase == AdminRemovalPhase.CLEANUP_PENDING,
                cleanupBlobId =
                    (target as? AdminRemovalTarget.Image)?.blobId?.takeIf {
                        phase == AdminRemovalPhase.CLEANUP_PENDING
                    },
            ),
            {},
            {},
            {},
        )
    }
}

/** Shows a profile confirmation using the original target. */
@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun AdminRemovalConfirmationPreview() {
    RemovalPreview(AdminRemovalPhase.CONFIRMING, AdminRemovalTarget.Account("owner/one", "Taylor Owner"))
}

/** Shows detached-image cleanup awaiting retry. */
@Preview(showBackground = true, widthDp = 390, heightDp = 800, fontScale = 1.5f)
@Composable
private fun AdminRemovalPendingImageCleanupPreview() {
    RemovalPreview(
        AdminRemovalPhase.CLEANUP_PENDING,
        AdminRemovalTarget.Image(ImageTarget(ImageTargetType.ACCOUNT, "owner/one"), "original-blob"),
    )
}

/** Shows an unresolved request with a status-check action. */
@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun AdminRemovalUnknownPreview() {
    RemovalPreview(AdminRemovalPhase.UNKNOWN, AdminRemovalTarget.Campsite("site/one", "River Camp"))
}

/** Shows the image removal question and its consequence. */
@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun AdminImageRemovalConfirmationPreview() {
    RemovalPreview(
        AdminRemovalPhase.CONFIRMING,
        AdminRemovalTarget.Image(ImageTarget(ImageTargetType.CAMPSITE, "preview-campsite"), "preview-blob"),
    )
}

/** Shows image removal while the repository owns the operation. */
@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun AdminImageRemovalProgressPreview() {
    RemovalPreview(
        AdminRemovalPhase.REMOVING,
        AdminRemovalTarget.Image(ImageTarget(ImageTargetType.CAMPSITE, "preview-campsite"), "preview-blob"),
    )
}

/** Shows confirmed image removal with Close. */
@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun AdminImageRemovalCompletePreview() {
    RemovalPreview(
        AdminRemovalPhase.COMPLETE,
        AdminRemovalTarget.Image(ImageTarget(ImageTargetType.CAMPSITE, "preview-campsite"), "preview-blob"),
    )
}

/** Shows retryable image removal without claiming success. */
@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun AdminImageRemovalFailurePreview() {
    RemovalPreview(
        AdminRemovalPhase.FAILED,
        AdminRemovalTarget.Image(ImageTarget(ImageTargetType.CAMPSITE, "preview-campsite"), "preview-blob"),
    )
}
