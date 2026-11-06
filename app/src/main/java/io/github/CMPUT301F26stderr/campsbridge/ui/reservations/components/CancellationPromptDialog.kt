/*
 * Reuses the shared scrollable confirmation and field controls for both actor roles.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.CancellationPhase
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.CancellationUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.cancellationAction
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.cancellationFailureMessage
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.cancellationProgress
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Reuses the shared scrollable confirmation and field controls for both actor roles.
 * @param record Readable saved stay; absent if its source failed during a held command.
 * @param context Actual caller workspace.
 * @param state Retained input and command feedback.
 * @param onReason Edits idle optional input.
 * @param onConfirm Explicit guarded operation.
 * @param onDismiss Nonmutating Keep, outside click or system Back.
 */
@Composable
internal fun CancellationPromptDialog(
    record: Reservation?,
    context: ReservationContext,
    state: CancellationUiState,
    onReason: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val prompt = state.prompt ?: return
    val submitting = state.phase == CancellationPhase.CANCELLING
    if (!submitting && (state.phase != CancellationPhase.IDLE || !state.canRequest)) return
    val owner = context == ReservationContext.OWNER
    ConfirmationDialog(
        title =
            stringResource(
                if (owner) R.string.reservation_reject_question else R.string.reservation_cancel_question,
            ),
        description =
            if (owner && record != null) {
                stringResource(R.string.reservation_reject_consequence, record.entrantId)
            } else if (owner) {
                stringResource(R.string.reservation_reject_without_record)
            } else {
                stringResource(R.string.reservation_cancel_consequence)
            },
        onConfirm = onConfirm,
        onCancel = onDismiss,
        onDismiss = onDismiss,
        isSubmitting = submitting,
        confirmLabel = stringResource(cancellationAction(context)),
        cancelLabel = stringResource(R.string.reservation_keep_action),
        destructive = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(stringResource(R.string.reservation_refund_consequence))
            AppTextField(
                prompt.reason,
                onReason,
                stringResource(R.string.reservation_reason_label),
                enabled = !submitting,
                singleLine = false,
                minLines = 2,
            )
            state.failure?.let { reason ->
                Text(stringResource(cancellationFailureMessage(reason)), color = MaterialTheme.colorScheme.error)
            }
            if (state.notCancelled) Text(stringResource(R.string.reservation_still_active))
            if (submitting) Text(stringResource(cancellationProgress(context)))
        }
    }
}
