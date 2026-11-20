/*
 * Separate winner count and optional waitlist capacity inputs.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField

/** Separate winner count and optional waitlist capacity inputs.
 * @param winnerCount Raw winner-count input.
 * @param waitlistCapacity Raw optional waiting-list capacity.
 * @param winnerError Existing count-parser feedback.
 * @param capacityError Existing optional-capacity feedback.
 * @param onCount Local positive winner-count edit.
 * @param onCapacity Optional registration-limit edit.
 */
@Composable
fun LotteryWinnersStep(
    winnerCount: String,
    waitlistCapacity: String,
    winnerError: PositiveCountInput.Error?,
    capacityError: PositiveCountInput.Error?,
    onCount: (String) -> Unit,
    onCapacity: (String) -> Unit,
) {
    AppTextField(
        winnerCount,
        onCount,
        stringResource(R.string.lottery_winners_step),
        errorMessage =
            if (winnerError != null) {
                stringResource(R.string.lottery_winners_invalid)
            } else {
                null
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        reserveSupportingSpace = true,
        revealError = winnerError != null,
    )
    Text(stringResource(R.string.lottery_winners_hint))
    AppTextField(
        waitlistCapacity,
        onCapacity,
        stringResource(R.string.lottery_capacity_optional),
        errorMessage = if (capacityError != null) stringResource(R.string.lottery_capacity_invalid) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        reserveSupportingSpace = true,
        revealError = winnerError == null && capacityError != null,
    )
    Text(stringResource(R.string.lottery_capacity_hint))
    if (waitlistCapacity.isBlank()) Text(stringResource(R.string.lottery_unlimited))
}
