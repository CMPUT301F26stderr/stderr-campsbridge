package io.github.CMPUT301F26stderr.campsbridge.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R

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
        text = { Text(description) },
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
            ) { Text(stringResource(R.string.cancel)) }
        },
    )
}
