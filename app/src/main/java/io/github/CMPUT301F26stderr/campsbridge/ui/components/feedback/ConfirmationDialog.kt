/*
 * Separates confirmation, explicit cancellation, and outside/Back dismissal.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Separates confirmation, explicit cancellation, and outside/Back dismissal.
 * @param title Localized question.
 * @param description Localized consequence.
 * @param onConfirm Confirmed operation.
 * @param onCancel Explicit cancellation.
 * @param onDismiss Outside/Back dismissal.
 * @param isSubmitting Blocks dismissal and repeated actions while pending.
 * @param confirmLabel Localized action, defaulting to a generic confirmation.
 * @param destructive Uses the shared error emphasis for destructive confirmation.
 * @param cancelLabel Explicit nonmutating alternative, defaulting to Cancel.
 * @param supportingContent Optional caller-owned input/feedback below the consequence.
 * @param progressDescription Visible and announced status while confirmation is pending.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    description: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    isSubmitting: Boolean = false,
    confirmLabel: String = stringResource(R.string.confirm),
    destructive: Boolean = false,
    cancelLabel: String = stringResource(R.string.cancel),
    progressDescription: String = stringResource(R.string.working),
    supportingContent: (@Composable () -> Unit)? = null,
) {
    val confirmColor =
        if (destructive) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                Text(description)
                if (isSubmitting) {
                    Row(
                        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(progressDescription)
                    }
                }
                supportingContent?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSubmitting,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = confirmColor,
                    ),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isSubmitting,
            ) { Text(cancelLabel) }
        },
    )
}
