/*
 * Displays cancellation confirmation, progress and recoverable failure independently of saved-record reads.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Shows command feedback independently of the ordinary saved-record read outcome.
 * @param state Confirmation/command projection from the retained parent.
 * @param context Actual actor workspace for progress wording.
 * @param onCheck Reads an uncertain result without repeating the write.
 */
@Composable
internal fun CancellationContent(
    state: CancellationUiState,
    context: ReservationContext,
    onCheck: () -> Unit,
) {
    when (state.phase) {
        CancellationPhase.CANCELLING -> {
            if (state.prompt == null) {
                LoadingContent(stringResource(cancellationProgress(context)))
            }
        }

        CancellationPhase.RECOVERING -> {
            LoadingContent(stringResource(R.string.reservation_checking))
        }

        CancellationPhase.UNKNOWN -> {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(stringResource(R.string.reservation_cancellation_unknown))
                AppActionButton(stringResource(R.string.reservation_check_action), onCheck)
            }
        }

        CancellationPhase.IDLE -> {
            Unit
        }
    }
}

/** Maps known noncommit failures to useful local feedback without exposing repository messages.
 * @param reason Typed rejection from the atomic operation.
 */
internal fun cancellationFailureMessage(reason: PersistenceFailure.Reason): Int =
    when (reason) {
        PersistenceFailure.Reason.NOT_ALLOWED -> {
            R.string.reservation_cancellation_not_allowed
        }

        PersistenceFailure.Reason.NOT_FOUND, PersistenceFailure.Reason.INACTIVE -> {
            R.string.reservation_cancellation_unavailable
        }

        PersistenceFailure.Reason.WINDOW_CLOSED -> {
            R.string.reservation_cancellation_cutoff
        }

        PersistenceFailure.Reason.CONFLICT, PersistenceFailure.Reason.INVALID_STATE -> {
            R.string.reservation_cancellation_conflict
        }

        else -> {
            R.string.reservation_cancellation_failed
        }
    }

/** Selects progress wording without relying on the actor who eventually saved cancellation.
 * @param context Caller workspace for the current explicit operation.
 */
internal fun cancellationProgress(context: ReservationContext): Int =
    if (context == ReservationContext.OWNER) R.string.reservation_rejecting else R.string.reservation_cancelling

/** Labels the explicit command for the current workspace.
 * @param context Entrant cancellation or owner rejection.
 */
internal fun cancellationAction(context: ReservationContext): Int =
    if (context == ReservationContext.OWNER) R.string.reservation_reject_action else R.string.reservation_cancel_action
