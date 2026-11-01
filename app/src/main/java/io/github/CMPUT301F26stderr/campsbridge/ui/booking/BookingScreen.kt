package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps.BookingReviewStep
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps.GuestsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps.StayDatesStep
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps.TermsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
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

/** Four stateless wizard steps under the shared frame; the caller owns every effect.
 * @param state Parent-supplied draft and source outcome.
 * @param onBack Requests Back from the parent.
 * @param onNext Reports the displayed step.
 * @param onCancel Requests draft abandonment.
 * @param onMonth Requests a displayed calendar month.
 * @param onDate Reports a selectable date.
 * @param onClearDates Requests clearing endpoints.
 * @param onGuests Reports raw guest input.
 * @param onAccept Reports acceptance and the exact displayed rules/policy.
 * @param onEdit Requests one review section.
 * @param onQuote Reports the displayed amount for approval.
 * @param onPolicy Requests the packaged policy reader.
 * @param onRetry Reports the failed source to retry.
 * @param onRetryVisible Requests the visible inventory retry.
 * @param onRetrySelected Requests the selected inventory retry.
 * @param onReservation Reports an existing saved reservation identity.
 * @param onConfirm Requests confirmation from the parent.
 * @param modifier Caller layout.
 * @param photo Independently supplied campsite photo state.
 * @param onRetryPhoto Requests photo-source retry.
 * @param isSubmitting Whether draft actions and exits are blocked.
 * @param discardPrompt Whether abandonment awaits confirmation.
 * @param onDismissDiscard Keeps the parent-owned draft.
 * @param onConfirmDiscard Confirms abandoning the parent-owned draft.
 */
@Composable
fun BookingScreen(
    state: BookingUiState,
    onBack: () -> Unit,
    onNext: (BookingStep) -> Unit,
    onCancel: () -> Unit,
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
    onReservation: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    photo: ImageSlotState = ImageSlotState(loadState = ImageLoadState.READY),
    onRetryPhoto: () -> Unit = {},
    isSubmitting: Boolean = false,
    discardPrompt: Boolean = false,
    onDismissDiscard: () -> Unit = {},
    onConfirmDiscard: () -> Unit = {},
) {
    val enabled = !isSubmitting && !discardPrompt
    BackHandler { if (enabled) onBack() }
    if (state is BookingUiState.Content) {
        val step = state.draft.step
        val review = step == BookingStep.REVIEW
        val conflict =
            review && state.error ==
                BookingStepError.Dates(StaySelectionInput.UNAVAILABLE_NIGHTS)
        StepScaffold(
            title = bookingStepTitle(step),
            progress =
                stringResource(
                    R.string.onboarding_step,
                    step.ordinal + 1,
                    BookingStep.entries.size,
                ),
            onBack = onBack,
            onNext = {
                when {
                    conflict -> onEdit(BookingStep.DATES)
                    review -> onConfirm()
                    else -> onNext(step)
                }
            },
            modifier = modifier,
            nextEnabled = enabled && (!review || conflict || state.canConfirm),
            isSubmitting = isSubmitting,
            nextLabel =
                stringResource(
                    when {
                        isSubmitting -> R.string.booking_submitting
                        conflict -> R.string.booking_choose_dates
                        review -> R.string.booking_confirm
                        else -> R.string.next
                    },
                ),
            steps = StepProgress(step.ordinal + 1, BookingStep.entries.size),
            onCancel = onCancel,
            exitEnabled = enabled,
        ) {
            if (!review) {
                Text(
                    state.campsite.details.name,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!conflict) {
                state.error?.let {
                    Text(bookingErrorText(it), color = MaterialTheme.colorScheme.error)
                }
            }
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
                    BookingReviewStep(
                        state,
                        onEdit,
                        onQuote,
                        onPolicy,
                        enabled,
                        photo,
                        onRetryPhoto,
                    )
                }
            }
        }
    } else {
        AppScreen(
            stringResource(R.string.booking_title),
            modifier,
            onBack = onBack,
            backEnabled = enabled,
        ) {
            if (isSubmitting) {
                LoadingContent(stringResource(R.string.booking_submitting))
            } else {
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
                            { onReservation(state.reservationId) },
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
            isSubmitting = isSubmitting,
            confirmLabel = stringResource(R.string.booking_discard),
            cancelLabel = stringResource(R.string.booking_keep_draft),
        )
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
