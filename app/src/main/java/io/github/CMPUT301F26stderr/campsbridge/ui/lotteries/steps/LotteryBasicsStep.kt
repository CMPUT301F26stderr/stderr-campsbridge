package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDraft

/** Exact editable basics without a screenshot-only character limit.
 * @param draft Current raw input.
 * @param titleRequired Explicit Next feedback.
 * @param onTitle Local title edit.
 * @param onDescription Local optional description edit.
 */
@Composable
fun LotteryBasicsStep(
    draft: LotteryDraft,
    titleRequired: Boolean,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
) {
    AppTextField(
        draft.title,
        onTitle,
        stringResource(R.string.lottery_title),
        errorMessage = if (titleRequired) stringResource(R.string.lottery_title_required) else null,
    )
    AppTextField(
        draft.description,
        onDescription,
        stringResource(R.string.lottery_description),
        singleLine = false,
        minLines = 3,
    )
}
