/*
 * Edits maximum winners and optional waitlist capacity.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupUiState

/** Edits maximum winners and optional waitlist capacity.
 * @param state Current raw count and explicit feedback.
 * @param onCount Local positive-count text edit.
 * @param onCapacity Local optional capacity text edit.
 */
@Composable
fun LotteryWinnersStep(
    state: LotterySetupUiState,
    onCount: (String) -> Unit,
    onCapacity: (String) -> Unit = {},
) {
    AppTextField(
        state.draft.maximumWinnersText,
        onCount,
        stringResource(R.string.lottery_winners_step),
        errorMessage =
            if (state.errors.winnerCount != null) {
                stringResource(R.string.lottery_winners_invalid)
            } else {
                null
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        reserveSupportingSpace = true,
        revealError = state.errors.winnerCount != null,
    )
    Text(stringResource(R.string.lottery_winners_hint))
    AppTextField(
        state.draft.waitlistCapacityText,
        onCapacity,
        stringResource(R.string.lottery_capacity_optional),
        errorMessage =
            if (state.errors.waitlistCapacity != null) {
                stringResource(R.string.lottery_capacity_invalid)
            } else {
                null
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        reserveSupportingSpace = true,
        revealError = state.errors.winnerCount == null && state.errors.waitlistCapacity != null,
    )
    Text(stringResource(R.string.lottery_capacity_hint))
}
