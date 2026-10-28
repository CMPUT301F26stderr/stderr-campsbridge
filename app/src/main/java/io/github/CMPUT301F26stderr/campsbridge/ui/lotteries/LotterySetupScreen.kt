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
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepAction
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

/** Six step contents with explicit save progress, retry and authoritative target restoration.
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
 * @param onSaveDraft Saves valid reviewed input under its retained identity.
 * @param onPublish Publishes valid reviewed input after the current-clock check.
 * @param onRetrySave Retries retained intent or resolves uncertain publication.
 * @param onRetryLoad Retries only a failed authoritative target read.
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
    onSaveDraft: () -> Unit = {},
    onPublish: () -> Unit = {},
    onRetrySave: () -> Unit = {},
    onRetryLoad: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val submitting = state.savePhase != LotterySavePhase.IDLE
    if (state.targetLoad != LotteryTargetLoad.READY || state.publicationPending) {
        AppScreen(
            stringResource(R.string.lottery_create),
            modifier,
            onBack = onBack,
            backEnabled = !submitting && !state.publicationPending,
        ) {
            when {
                state.publicationPending -> {
                    Text(stringResource(R.string.lottery_publication_recovery))
                    if (submitting) {
                        LoadingContent()
                    } else {
                        FailureContent(
                            stringResource(R.string.lottery_recovery_failed),
                            onRetrySave,
                        )
                    }
                }

                state.targetLoad == LotteryTargetLoad.LOADING -> {
                    LoadingContent()
                }

                state.targetLoad == LotteryTargetLoad.FAILURE -> {
                    FailureContent(stringResource(R.string.lottery_load_failed), onRetryLoad)
                }

                else -> {
                    Text(stringResource(R.string.lottery_unavailable))
                }
            }
        }
        if (state.discardPrompt) LotteryDiscardDialog(onConfirmDiscard, onDismissDiscard)
        return
    }
    val canEdit = !submitting && !state.discardPrompt
    val step = state.draft.step
    val review = step == LotteryStep.REVIEW
    StepScaffold(
        stringResource(R.string.lottery_create),
        stringResource(R.string.onboarding_step, step.ordinal + 1, LotteryStep.entries.size),
        onBack,
        { if (review) onPublish() else onNext() },
        modifier,
        nextEnabled = canEdit && (!review || state.configuration != null),
        isSubmitting = submitting,
        nextLabel = stringResource(if (review) R.string.lottery_publish else R.string.next),
        steps = StepProgress(step.ordinal + 1, LotteryStep.entries.size),
        onCancel = onCancel,
        secondaryLabel = if (review) stringResource(R.string.save_draft) else null,
        onSecondaryAction = if (review) onSaveDraft else null,
        secondaryEnabled = canEdit && state.configuration != null,
        submittingAction =
            if (state.saveAction == LotterySaveAction.DRAFT && review) {
                StepAction.SECONDARY
            } else {
                StepAction.PRIMARY
            },
    ) {
        Text(lotteryStepTitle(step), style = MaterialTheme.typography.headlineSmall)
        if (submitting) {
            Text(
                stringResource(
                    when (state.savePhase) {
                        LotterySavePhase.SAVING_DETAILS -> R.string.lottery_saving_details
                        LotterySavePhase.SAVING_OFFER -> R.string.lottery_saving_offer
                        LotterySavePhase.PUBLISHING -> R.string.lottery_publishing
                        else -> R.string.lottery_recovering
                    },
                ),
            )
        }
        if (state.saveFailure != null) {
            FailureContent(
                stringResource(R.string.lottery_save_failed),
                onRetrySave,
                retryEnabled = !submitting && state.configuration != null,
            )
        }
        if (state.publicationTooLate) Text(stringResource(R.string.lottery_publication_too_late))
        if (review && state.choices == LotteryCampsiteChoices.Failure) {
            FailureContent(
                stringResource(R.string.lottery_site_failure),
                onRetryChoices,
                !submitting,
            )
        }
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
                LotteryReviewStep(state, onEdit, onConfirmZone, enabled = canEdit)
            }
        }
    }
    if (state.discardPrompt) LotteryDiscardDialog(onConfirmDiscard, onDismissDiscard)
}

/** Confirms local abandonment independently of loading and source failures.
 * @param onConfirm Explicit abandonment action.
 * @param onDismiss Retains input and closes this prompt.
 */
@Composable
private fun LotteryDiscardDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmationDialog(
        stringResource(R.string.lottery_discard_title),
        stringResource(R.string.lottery_discard_body),
        onConfirm,
        onDismiss,
        onDismiss,
        confirmLabel = stringResource(R.string.lottery_discard_confirm),
    )
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
