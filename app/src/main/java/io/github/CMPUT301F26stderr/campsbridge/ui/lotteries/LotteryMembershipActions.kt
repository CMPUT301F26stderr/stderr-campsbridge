package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.InlineNotice
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.NoticeTone

/** Emits shared action/notice controls into the overview and owns no persistence or local success.
 * @param state Current eligible actions, command progress and confirmation from the owner.
 * @param onJoin Explicit join/rejoin request.
 * @param onRequestLeave Opens eligible withdrawal confirmation.
 * @param onDismissLeave Cancels an unsubmitted confirmation.
 * @param onConfirmLeave Submits only after current eligibility is rechecked.
 */
@Composable
internal fun LotteryMembershipActions(
    state: LotteryDetailUiState.Content,
    onJoin: () -> Unit,
    onRequestLeave: () -> Unit,
    onDismissLeave: () -> Unit,
    onConfirmLeave: () -> Unit,
) {
    state.membershipError?.let { failure ->
        InlineNotice(
            stringResource(R.string.lottery_membership_update_failed),
            stringResource(
                when (failure) {
                    LotteryMembershipActionError.FULL -> R.string.lottery_waitlist_full
                    LotteryMembershipActionError.CLOSED -> R.string.lottery_membership_closed
                    LotteryMembershipActionError.FAILED -> R.string.lottery_membership_retry
                },
            ),
            tone = NoticeTone.ERROR,
        )
    }
    if (state.canJoin || state.membershipAction == LotteryMembershipAction.JOIN) {
        AppActionButton(
            stringResource(R.string.lottery_join_waitlist),
            onJoin,
            Modifier.fillMaxWidth(),
            enabled = state.canJoin && state.membershipAction == null,
            isSubmitting = state.membershipAction == LotteryMembershipAction.JOIN,
            progressDescription = stringResource(R.string.lottery_joining_waitlist),
        )
    }
    if (state.canLeave || state.membershipAction == LotteryMembershipAction.LEAVE) {
        AppActionButton(
            stringResource(R.string.lottery_leave_waitlist),
            onRequestLeave,
            Modifier.fillMaxWidth(),
            enabled = state.canLeave && state.membershipAction == null,
            isSubmitting = state.membershipAction == LotteryMembershipAction.LEAVE,
            progressDescription = stringResource(R.string.lottery_leaving_waitlist),
            style = ActionStyle.SECONDARY,
        )
    }
    if (state.isConfirmingLeave) {
        ConfirmationDialog(
            stringResource(R.string.lottery_leave_title),
            stringResource(R.string.lottery_leave_consequence),
            onConfirmLeave,
            onDismissLeave,
            onDismissLeave,
            isSubmitting = state.membershipAction == LotteryMembershipAction.LEAVE,
            confirmLabel = stringResource(R.string.lottery_leave_confirm),
            destructive = true,
        )
    }
}
