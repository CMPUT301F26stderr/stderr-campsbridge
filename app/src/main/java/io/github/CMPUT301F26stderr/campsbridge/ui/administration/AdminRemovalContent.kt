package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Pure confirmation/progress content; required copy is supplied by the inspection owner. */
@Composable
fun AdminRemovalContent(
    state: AdminRemovalUiState,
    warnings: AdminRemovalWarnings,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val request = state.request ?: return
    if (!state.dialogVisible || state.phase == AdminRemovalPhase.IDLE ||
        state.phase == AdminRemovalPhase.ROOT_HANDOFF
    ) {
        return
    }
    val target = request.target
    val title =
        stringResource(
            when (target) {
                is AdminRemovalTarget.Account -> R.string.admin_remove_account
                is AdminRemovalTarget.Campsite -> R.string.admin_remove_campsite
                is AdminRemovalTarget.Image -> R.string.admin_remove_image
            },
        )
    if (state.phase == AdminRemovalPhase.CONFIRMING) {
        ConfirmationDialog(
            title = title,
            description =
                when (target) {
                    is AdminRemovalTarget.Account -> warnings.account
                    is AdminRemovalTarget.Campsite -> warnings.campsite
                    is AdminRemovalTarget.Image -> warnings.image
                },
            onConfirm = onConfirm,
            onCancel = onDismiss,
            onDismiss = onDismiss,
            confirmLabel = stringResource(R.string.admin_removal_confirm),
            destructive = true,
            supportingContent = { AdminRemovalIdentity(target) },
        )
        return
    }
    val busy = state.phase == AdminRemovalPhase.REMOVING || state.phase == AdminRemovalPhase.CHECKING
    val canClose = state.phase == AdminRemovalPhase.COMPLETE || state.phase == AdminRemovalPhase.UNAVAILABLE
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                AdminRemovalIdentity(target)
                when (state.phase) {
                    AdminRemovalPhase.REMOVING -> {
                        LoadingContent(stringResource(R.string.admin_removal_working))
                    }

                    AdminRemovalPhase.CHECKING -> {
                        LoadingContent(stringResource(R.string.admin_removal_checking))
                    }

                    AdminRemovalPhase.FAILED -> {
                        Text(stringResource(R.string.admin_removal_failed))
                    }

                    AdminRemovalPhase.UNKNOWN -> {
                        Text(stringResource(R.string.admin_removal_unknown))
                    }

                    AdminRemovalPhase.CLEANUP_PENDING -> {
                        Text(
                            stringResource(
                                if (target is AdminRemovalTarget.Image) {
                                    R.string.admin_removal_image_cleanup_pending
                                } else {
                                    R.string.admin_removal_record_cleanup_pending
                                },
                            ),
                        )
                    }

                    AdminRemovalPhase.COMPLETE -> {
                        Text(stringResource(R.string.admin_removal_complete))
                    }

                    AdminRemovalPhase.UNAVAILABLE -> {
                        Text(
                            stringResource(
                                when (state.problem) {
                                    AdminRemovalProblem.MISSING -> R.string.admin_removal_missing
                                    AdminRemovalProblem.CHANGED -> R.string.admin_removal_changed
                                    else -> R.string.admin_removal_denied
                                },
                            ),
                        )
                    }

                    else -> {
                        Unit
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { if (canClose) onDismiss() else onRetry() }) {
                Text(
                    stringResource(
                        when {
                            canClose -> R.string.admin_removal_close
                            state.phase == AdminRemovalPhase.UNKNOWN -> R.string.admin_removal_check_status
                            state.phase == AdminRemovalPhase.CLEANUP_PENDING -> R.string.admin_removal_retry_cleanup
                            else -> R.string.retry
                        },
                    ),
                )
            }
        },
        dismissButton = {
            if (!busy && !canClose) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.admin_removal_close))
                }
            }
        },
    )
}

/** The confirmation always displays the original identity, not the current selection. */
@Composable
private fun AdminRemovalIdentity(target: AdminRemovalTarget) {
    when (target) {
        is AdminRemovalTarget.Account -> {
            Text(target.name.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_provided))
            Text(stringResource(R.string.admin_removal_target_id, target.id))
        }

        is AdminRemovalTarget.Campsite -> {
            Text(target.name.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_provided))
            Text(stringResource(R.string.admin_removal_target_id, target.id))
        }

        is AdminRemovalTarget.Image -> {
            Text(
                stringResource(
                    if (target.target.type ==
                        ImageTargetType.ACCOUNT
                    ) {
                        R.string.admin_target_account
                    } else {
                        R.string.admin_target_campsite
                    },
                ),
            )
            Text(stringResource(R.string.admin_removal_target_id, target.target.id))
            Text(stringResource(R.string.admin_removal_image_reference, target.blobId))
        }
    }
}

@Composable
private fun RemovalPreview(
    phase: AdminRemovalPhase,
    target: AdminRemovalTarget,
) {
    val warnings =
        AdminRemovalWarnings(
            stringResource(R.string.admin_remove_profile_body, "owner/one"),
            stringResource(R.string.admin_remove_site_body, "site/one"),
            stringResource(R.string.admin_remove_image_body, "owner/one"),
        )
    CampsbridgeTheme {
        AdminRemovalContent(
            AdminRemovalUiState(
                phase,
                AdminRemovalRequest("preview-admin", target),
                committed =
                    phase == AdminRemovalPhase.CLEANUP_PENDING,
            ),
            warnings,
            {},
            {},
            {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun AdminRemovalConfirmationPreview() {
    RemovalPreview(AdminRemovalPhase.CONFIRMING, AdminRemovalTarget.Account("owner/one", "Taylor Owner"))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800, fontScale = 1.5f)
@Composable
private fun AdminRemovalPendingImageCleanupPreview() {
    RemovalPreview(
        AdminRemovalPhase.CLEANUP_PENDING,
        AdminRemovalTarget.Image(ImageTarget(ImageTargetType.ACCOUNT, "owner/one"), "original-blob"),
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun AdminRemovalUnknownPreview() {
    RemovalPreview(AdminRemovalPhase.UNKNOWN, AdminRemovalTarget.Campsite("site/one", "River Camp"))
}
