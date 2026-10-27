package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.InlineNotice
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.NoticeTone

/** Renders actual owner command feedback without owning completion or draw eligibility.
 * @param state Confirmed parent facts and the current manual call projection.
 * @param onDraw Explicit eligible fallback or retry.
 */
@Composable
internal fun LotteryDrawActions(
    state: LotteryDetailUiState.Content,
    onDraw: () -> Unit,
) {
    if (state.drawFailed) {
        InlineNotice(
            stringResource(R.string.lottery_draw_failed),
            stringResource(R.string.lottery_draw_retry),
            tone = NoticeTone.ERROR,
        )
    }
    if (state.canDraw || state.isDrawing) {
        AppActionButton(
            stringResource(R.string.lottery_run_draw),
            onDraw,
            Modifier.fillMaxWidth(),
            enabled = state.canDraw && !state.isDrawing,
            isSubmitting = state.isDrawing,
            progressDescription = stringResource(R.string.lottery_drawing),
        )
    }
}
