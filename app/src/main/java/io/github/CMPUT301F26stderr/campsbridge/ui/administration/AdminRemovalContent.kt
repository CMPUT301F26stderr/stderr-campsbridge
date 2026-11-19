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
    if (state.phase == AdminRemovalPhase.IDLE || state.phase == AdminRemovalPhase.ROOT_HANDOFF) return
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
        onDismissRequest = { if (canClose) onDismiss() },
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

