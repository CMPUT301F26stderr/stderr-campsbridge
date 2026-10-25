package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepProgress
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryBasicsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryCampsiteStep
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryReviewStep
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryWindowStep
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryWinnersStep
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Six ordinary step contents in the shared form frame; persistence is connected in M3a.2.
 * @param state Local parent input and read outcomes.
 * @param initialMonth Site-clock month for an unselected native date.
 * @param onBack Moves within this parent or requests abandonment.
 * @param onNext Validates the current step.
 * @param onCancel Requests explicit abandonment.
 * @param onDismissDiscard Retains current input.
 * @param onConfirmDiscard Abandons this local form.
 * @param onTitle Changes raw title text.
 * @param onDescription Changes raw optional description.
 * @param onChooseCampsite Selects one actual published identity.
 * @param onAllocate Opens this parent's allocation child.
 * @param onRetryChoices Retries the independent campsite source.
 * @param onDate Confirms one local schedule date.
 * @param onTime Confirms one local schedule time.
 * @param onWinners Changes raw positive winner-count input.
 * @param onEdit Returns to the requested step from review.
 * @param onConfirmZone Accepts displayed local times in the selected site's current zone.
 * @param modifier Caller layout.
 */
@Composable
fun LotterySetupScreen(
    state: LotterySetupUiState,
    initialMonth: YearMonth?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
    onChooseCampsite: (String) -> Unit,
    onAllocate: () -> Unit,
    onRetryChoices: () -> Unit,
    onDate: (LotterySchedulePoint, LocalDate) -> Unit,
    onTime: (LotterySchedulePoint, LocalTime) -> Unit,
    onWinners: (String) -> Unit,
    onEdit: (LotteryStep) -> Unit,
    onConfirmZone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val step = state.draft.step
    val review = step == LotteryStep.REVIEW
    StepScaffold(
        stringResource(R.string.lottery_create),
        stringResource(R.string.onboarding_step, step.ordinal + 1, LotteryStep.entries.size),
        onBack,
        onNext,
        modifier,
        nextEnabled = !review && !state.discardPrompt,
        nextLabel = stringResource(if (review) R.string.lottery_publish else R.string.next),
        steps = StepProgress(step.ordinal + 1, LotteryStep.entries.size),
        onCancel = onCancel,
        secondaryLabel = if (review) stringResource(R.string.save_draft) else null,
        onSecondaryAction = if (review) ({}) else null,
        secondaryEnabled = false,
    ) {
        Text(lotteryStepTitle(step), style = MaterialTheme.typography.headlineSmall)
        when (step) {
            LotteryStep.BASICS -> {
                LotteryBasicsStep(
                    state.draft,
                    state.errors.titleRequired,
                    onTitle,
                    onDescription,
                )
            }

            LotteryStep.SITE -> {
                LotteryCampsiteStep(state, onChooseCampsite, onAllocate, onRetryChoices)
            }

            LotteryStep.REGISTRATION -> {
                LotteryWindowStep(
                    state,
                    LotterySchedulePoint.REGISTRATION_OPEN,
                    LotterySchedulePoint.REGISTRATION_CLOSE,
                    initialMonth,
                    onDate,
                    onTime,
                )
            }

            LotteryStep.BOOKING -> {
                LotteryWindowStep(
                    state,
                    LotterySchedulePoint.BOOKING_OPEN,
                    LotterySchedulePoint.BOOKING_CLOSE,
                    initialMonth,
                    onDate,
                    onTime,
                )
            }

            LotteryStep.WINNERS -> {
                LotteryWinnersStep(state, onWinners)
            }

            LotteryStep.REVIEW -> {
                LotteryReviewStep(state, onEdit, onConfirmZone)
            }
        }
    }
    if (state.discardPrompt) {
        ConfirmationDialog(
            stringResource(R.string.lottery_discard_title),
            stringResource(R.string.lottery_discard_body),
            onConfirmDiscard,
            onDismissDiscard,
            onDismissDiscard,
            confirmLabel = stringResource(R.string.lottery_discard_confirm),
        )
    }
}

/** Resolves one step's title without copying wizard position into another navigation destination.
 * @param step Current position in the actual six-step flow.
 */
@Composable
private fun lotteryStepTitle(step: LotteryStep): String =
    stringResource(
        when (step) {
            LotteryStep.BASICS -> R.string.lottery_basics
            LotteryStep.SITE -> R.string.lottery_site_step
            LotteryStep.REGISTRATION -> R.string.lottery_registration_step
            LotteryStep.BOOKING -> R.string.lottery_booking_step
            LotteryStep.WINNERS -> R.string.lottery_winners_title
            LotteryStep.REVIEW -> R.string.lottery_review_step
        },
    )

/** Supplies all six content states without constructing repositories or simulating a save. */
class LotterySetupPreviewProvider : PreviewParameterProvider<LotterySetupUiState> {
    override val values: Sequence<LotterySetupUiState> =
        LotteryStep.entries.asSequence().map { step ->
            val site = previewCampsite()
            LotterySetupUiState(
                draft =
                    LotteryDraft(
                        step = step,
                        title = "May weekends 2027",
                        description = "Quiet riverside camping for the May weekends.",
                        campsiteId = site.id,
                        nights = setOf(LocalDate.of(2027, 5, 14), LocalDate.of(2027, 5, 15)),
                        schedule =
                            LotterySchedulePoint.entries
                                .mapIndexed { index, point ->
                                    point to
                                        LotteryLocalDateTime(
                                            LocalDate.of(2027, 5, 5 + index),
                                            LocalTime.of(9, 0),
                                        )
                                }.toMap(),
                        scheduleZone = site.details.timeZone,
                        maximumWinnersText = "2",
                    ),
                choices = LotteryCampsiteChoices.Content(listOf(site)),
                errors =
                    if (step == LotteryStep.REVIEW) {
                        LotterySetupErrors(windowsPending = true)
                    } else {
                        LotterySetupErrors()
                    },
            )
        }
}

/** Passive themed previews cover each step, including the explicit pending review state.
 * @param state Supplied realistic step input.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun LotterySetupPreview(
    @PreviewParameter(LotterySetupPreviewProvider::class)
    state: LotterySetupUiState,
) {
    io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme {
        LotterySetupScreen(
            state,
            YearMonth.of(2027, 5),
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            { _, _ -> },
            { _, _ -> },
            {},
            {},
            {},
        )
    }
}
