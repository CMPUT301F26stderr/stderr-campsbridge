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
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
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
    state: LotteryMembershipUiState,
    onJoin: () -> Unit,
    onRequestLeave: () -> Unit,
    onDismissLeave: () -> Unit,
    onConfirmLeave: () -> Unit,
) {
    state.error?.let { failure ->
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
    if (state.canJoin || state.action == LotteryMembershipAction.JOIN) {
        AppActionButton(
            stringResource(R.string.lottery_join_waitlist),
            onJoin,
            Modifier.fillMaxWidth(),
            enabled = state.canJoin && state.action == null,
            isSubmitting = state.action == LotteryMembershipAction.JOIN,
            progressDescription = stringResource(R.string.lottery_joining_waitlist),
        )
    }
    if (state.canLeave || state.action == LotteryMembershipAction.LEAVE) {
        AppActionButton(
            stringResource(R.string.lottery_leave_waitlist),
            onRequestLeave,
            Modifier.fillMaxWidth(),
            enabled = state.canLeave && state.action == null,
            isSubmitting = state.action == LotteryMembershipAction.LEAVE,
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
            isSubmitting = state.action == LotteryMembershipAction.LEAVE,
            confirmLabel = stringResource(R.string.lottery_leave_confirm),
            destructive = true,
        )
    }
}

/** Displays own-entry knowledge independently from shared command feedback.
 * @param membership Current display-safe own-entry outcome.
 * @param onRetry Explicit source retry, never a command replay.
 */
@Composable
internal fun LotteryMembershipReadContent(
    membership: LotteryMembershipRead,
    onRetry: () -> Unit,
) {
    when (membership) {
        LotteryMembershipRead.NotApplicable -> {
            Unit
        }

        LotteryMembershipRead.Loading -> {
            Text(stringResource(R.string.lottery_membership_loading))
        }

        LotteryMembershipRead.Failure -> {
            FailureContent(
                stringResource(R.string.lottery_membership_failed),
                onRetry,
            )
        }

        is LotteryMembershipRead.Content -> {
            Text(lotteryMembershipLabel(membership.status))
        }
    }
}

/** Localizes only confirmed or explicitly pending own participation facts.
 * @param status Display-safe status supplied by the controller.
 */
@Composable
internal fun lotteryMembershipLabel(status: LotteryMembershipStatus): String =
    stringResource(
        when (status) {
            LotteryMembershipStatus.NOT_JOINED -> R.string.lottery_not_joined
            LotteryMembershipStatus.WAITING -> R.string.lottery_membership_waiting
            LotteryMembershipStatus.WITHDRAWN -> R.string.lottery_membership_withdrawn
            LotteryMembershipStatus.RESULTS_PENDING -> R.string.lottery_draw_pending
            LotteryMembershipStatus.WON -> R.string.lottery_membership_won
            LotteryMembershipStatus.LOST -> R.string.lottery_membership_lost
            LotteryMembershipStatus.DECLINED -> R.string.lottery_membership_declined
        },
    )
