package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog

/** Presents explicit decline/recovery controls independently of the saved entry source.
 * @param state Reader-owned eligibility and command progress.
 * @param onRequest Opens confirmation.
 * @param onDismiss Keeps the unused opportunity.
 * @param onConfirm Submits the confirmed decline.
 * @param onRetry Retries the same failed target explicitly.
 */
@Composable
internal fun LotteryDeclineActions(
    state: LotteryDeclineUiState,
    onRequest: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
) {
    if (state.failed) Text(stringResource(R.string.lottery_decline_failed))
    if (state.isAwaitingEntry) Text(stringResource(R.string.lottery_decline_updating))
    if (state.canDecline || state.isSubmitting || state.failed) {
        AppActionButton(
            stringResource(
                when {
                    state.isSubmitting -> R.string.lottery_declining
                    state.failed -> R.string.lottery_decline_retry
                    else -> R.string.lottery_decline_action
                },
            ),
            if (state.failed) onRetry else onRequest,
            Modifier.fillMaxWidth(),
            enabled = state.canDecline,
            isSubmitting = state.isSubmitting,
            progressDescription = stringResource(R.string.lottery_declining),
            style = ActionStyle.SECONDARY,
        )
    }
    if (state.isConfirming) {
        ConfirmationDialog(
            stringResource(R.string.lottery_decline_title),
            stringResource(R.string.lottery_decline_body),
            onConfirm,
            onDismiss,
            onDismiss,
            confirmLabel = stringResource(R.string.lottery_decline_confirm),
            cancelLabel = stringResource(R.string.lottery_decline_keep),
            destructive = true,
        )
    }
}

/** Gates navigation from confirmed reader facts while an explicit decline owns the win.
 * @param state Saved reader and authoritative consumption outcome.
 * @param decline Independent pending or acknowledged decline.
 */
internal fun winningOpportunityIsAvailable(
    state: LotteryDetailUiState,
    decline: LotteryDeclineUiState,
): Boolean =
    state is LotteryDetailUiState.Content && state.opportunity == LotteryOpportunityRead.Unused &&
        state.lifecycle == LotteryLifecycle.BOOKING_OPEN && !decline.isSubmitting && !decline.isAwaitingEntry
