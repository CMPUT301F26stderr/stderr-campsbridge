package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps.BookingReviewStep
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps.GuestsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps.StayDatesStep
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps.TermsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepProgress
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepScaffold
import java.time.LocalDate
import java.time.YearMonth

/** Four focused prototype steps under one shared frame; state and callbacks contain all behavior.
 * @param state Source-backed draft or explicit read/unavailability result.
 * @param discardPrompt Whether abandonment is awaiting confirmation.
 * @param exitPending Whether actual route navigation is pending.
 * @param onBack Returns within the draft or requests abandonment.
 * @param onNext Validates the step actually displayed by this frame.
 * @param onExit Requests abandonment.
 * @param onDismissDiscard Keeps the existing draft.
 * @param onConfirmDiscard Requests the actual caller destination without writes.
 * @param onMonth Changes only the displayed calendar month.
 * @param onDate Delivers a currently selectable calendar cell.
 * @param onClearDates Clears the chosen range.
 * @param onGuests Changes raw count input.
 * @param onAccept Changes acknowledgement of the exact displayed rules/version.
 * @param onEdit Returns to a step from review.
 * @param onQuote Approves the displayed changed amount.
 * @param onPolicy Opens the existing policy child.
 * @param onRetry Retries one failed read source.
 * @param onRetryVisible Retries displayed inventory independently.
 * @param onRetrySelected Retries selected inventory independently.
 * @param onReservation Reopens actual known consuming history.
 * @param submission Independent command/result state; unknown consumption blocks conflicting input.
 * @param onConfirm Submits only the currently reviewed stay through the parent.
 * @param onRecover Retries only server confirmation of an unresolved result.
 * @param modifier Caller layout.
 */
@Composable
fun BookingScreen(
    state: BookingUiState,
    discardPrompt: Boolean,
    exitPending: Boolean,
    onBack: () -> Unit,
    onNext: (BookingStep) -> Unit,
    onExit: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
    onClearDates: () -> Unit,
    onGuests: (String) -> Unit,
    onAccept: (Boolean, String, String) -> Unit,
    onEdit: (BookingStep) -> Unit,
    onQuote: (Long) -> Unit,
    onPolicy: () -> Unit,
    onRetry: (BookingReadSource) -> Unit,
    onRetryVisible: () -> Unit,
    onRetrySelected: () -> Unit,
    onReservation: () -> Unit,
    submission: BookingSubmission,
    onConfirm: () -> Unit,
    onRecover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocked =
        submission == BookingSubmission.Submitting ||
            submission == BookingSubmission.Recovering || submission == BookingSubmission.Unknown
    BackHandler { if (!blocked) onBack() }
    val enabled = !discardPrompt && !exitPending && !blocked
    if (state is BookingUiState.Content) {
        val step = state.draft.step
        val title = bookingStepTitle(step)
        StepScaffold(
            title,
            stringResource(R.string.onboarding_step, step.ordinal + 1, BookingStep.entries.size),
            onBack,
            { if (step == BookingStep.REVIEW) onConfirm() else onNext(step) },
            modifier,
            nextEnabled = enabled && (step != BookingStep.REVIEW || state.canConfirm),
            isSubmitting = submission == BookingSubmission.Submitting,
            nextLabel =
                stringResource(
                    if (step ==
                        BookingStep.REVIEW
                    ) {
                        R.string.booking_confirm
                    } else {
                        R.string.next
                    },
                ),
            steps = StepProgress(step.ordinal + 1, BookingStep.entries.size),
            onCancel = onExit,
            exitEnabled = !exitPending && !blocked,
        ) {
            BookingSubmissionFeedback(submission, onRecover)
            Text(state.campsite.details.name, style = MaterialTheme.typography.titleMedium)
            state.error?.let { Text(bookingErrorText(it), color = MaterialTheme.colorScheme.error) }
            when (step) {
                BookingStep.DATES -> {
                    StayDatesStep(
                        state,
                        onMonth,
                        onDate,
                        onClearDates,
                        onRetryVisible,
                        onRetrySelected,
                        enabled,
                    )
                }

                BookingStep.GUESTS -> {
                    GuestsStep(state, onGuests, enabled)
                }

                BookingStep.TERMS -> {
                    TermsStep(state, onAccept, onPolicy, enabled)
                }

                BookingStep.REVIEW -> {
                    BookingReviewStep(state, onEdit, onQuote, onPolicy, enabled)
                }
            }
        }
    } else {
        AppScreen(
            stringResource(R.string.booking_title),
            modifier,
            onBack = onBack,
            backEnabled = !exitPending && !blocked,
        ) {
            BookingSubmissionFeedback(submission, onRecover)
            if (!blocked) {
                when (state) {
                    BookingUiState.Loading -> {
                        LoadingContent()
                    }

                    is BookingUiState.Failure -> {
                        FailureContent(
                            stringResource(R.string.booking_read_failed),
                            { onRetry(state.source) },
                            enabled,
                        )
                    }

                    is BookingUiState.Unavailable -> {
                        Text(bookingUnavailableText(state.reason))
                    }

                    is BookingUiState.Reserved -> {
                        Text(stringResource(R.string.booking_already_reserved))
                        AppActionButton(
                            stringResource(R.string.reservation_view),
                            onReservation,
                            enabled = enabled,
                        )
                    }

                    is BookingUiState.Content -> {
                        Unit
                    }
                }
            }
        }
    }
    if (discardPrompt) {
        ConfirmationDialog(
            stringResource(R.string.booking_discard_title),
            stringResource(R.string.booking_discard_body),
            onConfirmDiscard,
            onDismissDiscard,
            onDismissDiscard,
            confirmLabel = stringResource(R.string.booking_discard),
        )
    }
}

/** Shows transaction outcome independently of source failure, without inventing saved success.
 * @param submission Current guarded command outcome.
 * @param onRecover Explicit read-only retry while consumption remains unknown.
 */
@Composable
private fun BookingSubmissionFeedback(
    submission: BookingSubmission,
    onRecover: () -> Unit,
) {
    when (submission) {
        BookingSubmission.Idle -> {
            Unit
        }

        BookingSubmission.Submitting -> {
            LoadingContent(stringResource(R.string.booking_submitting))
        }

        BookingSubmission.Recovering -> {
            LoadingContent(stringResource(R.string.booking_recovering))
        }

        BookingSubmission.Unknown -> {
            Text(stringResource(R.string.booking_recovery_unknown))
            AppActionButton(stringResource(R.string.booking_check_result), onRecover)
        }

        BookingSubmission.Absent -> {
            Text(stringResource(R.string.booking_recovery_absent))
        }

        is BookingSubmission.Rejected -> {
            Text(
                stringResource(
                    when (submission.reason) {
                        PersistenceFailure.Reason.CONFLICT -> R.string.booking_conflict

                        PersistenceFailure.Reason.STALE_QUOTE -> R.string.booking_rejected_quote

                        PersistenceFailure.Reason.WINDOW_CLOSED -> R.string.booking_closed

                        PersistenceFailure.Reason.NOT_FOUND,
                        PersistenceFailure.Reason.INACTIVE,
                        -> R.string.booking_site_unavailable

                        PersistenceFailure.Reason.NOT_ALLOWED -> R.string.booking_not_winner

                        else -> R.string.booking_rejected
                    },
                ),
            )
        }
    }
}

/** Resolves one actual wizard heading; no screenshot text is hardcoded in content.
 * @param step Current single-site wizard position.
 */
@Composable
internal fun bookingStepTitle(step: BookingStep): String =
    stringResource(
        when (step) {
            BookingStep.DATES -> R.string.booking_dates_title
            BookingStep.GUESTS -> R.string.booking_guests_title
            BookingStep.TERMS -> R.string.booking_terms_title
            BookingStep.REVIEW -> R.string.booking_review_title
        },
    )

/** Resolves explicit advancement feedback with the existing typed rule/parser outcomes.
 * @param error Current user-correctable or pending-source outcome.
 */
@Composable
private fun bookingErrorText(error: BookingStepError): String =
    stringResource(
        when (error) {
            is BookingStepError.Dates -> {
                when (error.reason) {
                    StaySelectionInput.MISSING_CHECK_IN -> R.string.booking_arrival_missing
                    StaySelectionInput.MISSING_CHECK_OUT -> R.string.booking_checkout_missing
                    StaySelectionInput.INVALID_RANGE -> R.string.booking_range_invalid
                    StaySelectionInput.PAST_CHECK_IN -> R.string.booking_past_arrival
                    StaySelectionInput.UNAVAILABLE_NIGHTS -> R.string.booking_nights_unavailable
                    StaySelectionInput.VALID -> error("Valid stay is not error feedback")
                }
            }

            is BookingStepError.Guests -> {
                when (error.reason) {
                    PositiveCountInput.Error.REQUIRED -> R.string.booking_guests_required
                    PositiveCountInput.Error.INVALID_WHOLE_NUMBER -> R.string.booking_guests_invalid
                    PositiveCountInput.Error.EXCEEDS_MAXIMUM -> R.string.booking_guests_excessive
                }
            }

            BookingStepError.EligibilityPending -> {
                R.string.booking_eligibility_pending
            }

            BookingStepError.InventoryUnknown -> {
                R.string.booking_inventory_unknown
            }

            BookingStepError.AgreementRequired -> {
                R.string.booking_agreement_required
            }

            BookingStepError.QuoteOverflow -> {
                R.string.booking_quote_overflow
            }
        },
    )

/** Resolves confirmed unavailability independently of source error or missing inventory.
 * @param reason Current lottery/account/site/window fact.
 */
@Composable
private fun bookingUnavailableText(reason: BookingUnavailableReason): String =
    stringResource(
        when (reason) {
            BookingUnavailableReason.LOTTERY_UNAVAILABLE -> R.string.booking_lottery_unavailable
            BookingUnavailableReason.LOTTERY_CANCELLED -> R.string.booking_lottery_cancelled
            BookingUnavailableReason.DRAW_PENDING -> R.string.booking_draw_pending
            BookingUnavailableReason.NOT_A_WINNER -> R.string.booking_not_winner
            BookingUnavailableReason.CAMPSITE_UNAVAILABLE -> R.string.booking_site_unavailable
            BookingUnavailableReason.OFFER_UNAVAILABLE -> R.string.booking_offer_unavailable
            BookingUnavailableReason.BOOKING_NOT_OPEN -> R.string.booking_not_open
            BookingUnavailableReason.BOOKING_CLOSED -> R.string.booking_closed
        },
    )
