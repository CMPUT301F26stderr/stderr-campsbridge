package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryBasicsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryWindowStep
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import java.time.YearMonth

/** Shows grouped details, deadline locks and recovery at normal and doubled scales.
 * @param state Supplied saved input or a required-source outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large lottery editor", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun LotteryDetailsEditPreview(
    @PreviewParameter(LotteryDetailsEditPreviewProvider::class) state: LotteryDetailsEditUiState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        LotteryDetailsEditScreen(state, {}, {}, {}, {})
    }
}

/** Exercises the existing multiline basics controls with long saved descriptions. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large lottery descriptions", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun LotteryDetailsBasicsPreview() {
    val content = LotteryDetailsEditPreviewProvider().values.first() as LotteryDetailsEditUiState.Content
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        LotteryDetailsFieldsScreen(LotteryEditSection.BASICS, content, {}, {}) {
            LotteryBasicsStep(content.draft, false, {}, {})
        }
    }
}

/** Shows the reused date/time fields without an editor model or simulated save. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large lottery schedule", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun LotteryDetailsWindowPreview() {
    val saved = LotteryDetailsEditPreviewProvider().values.first() as LotteryDetailsEditUiState.Content
    val closing = checkNotNull(saved.draft.schedule[LotterySchedulePoint.REGISTRATION_CLOSE])
    val opening = checkNotNull(saved.draft.schedule[LotterySchedulePoint.REGISTRATION_OPEN])
    val content =
        saved.copy(
            draft =
                saved.draft.copy(
                    schedule =
                        saved.draft.schedule +
                            (
                                LotterySchedulePoint.REGISTRATION_CLOSE to
                                    closing.copy(date = opening.date, time = opening.time)
                            ),
                ),
            errors = LotterySetupErrors(windows = LotteryWindowInput.InvalidRegistration),
        )
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        LotteryDetailsFieldsScreen(LotteryEditSection.REGISTRATION, content, {}, {}) {
            LotteryWindowStep(
                content.draft.schedule,
                emptyMap(),
                LotterySchedulePoint.REGISTRATION_OPEN,
                LotterySchedulePoint.REGISTRATION_CLOSE,
                YearMonth.of(2027, 5),
                { _, _ -> },
                { _, _ -> },
            )
        }
    }
}
