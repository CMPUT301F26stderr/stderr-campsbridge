package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsitePhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Passive examples of four steps, unknown/failed sources, changed review and long text. */
internal class BookingPreviewProvider : PreviewParameterProvider<BookingUiState> {
    override val values: Sequence<BookingUiState>
        get() {
            val dates = previewBooking()
            val review = previewBooking(BookingStep.REVIEW).copy(canConfirm = true)
            val occupied = dates.offer.nights.map { CalendarNight(it, true, "another-reservation") }
            val conflictNights =
                dates.offer.nights.map {
                    CalendarNight(
                        it,
                        true,
                        "another-reservation".takeIf { _ ->
                            it ==
                                dates.draft.checkIn
                        },
                    )
                }
            val conflictInventory = CalendarInventoryRead.Content(conflictNights)
            val remaining = dates.offer.nights.toSet() - requireNotNull(dates.draft.checkIn)
            return sequenceOf(
                dates,
                previewBooking(BookingStep.GUESTS),
                previewBooking(BookingStep.TERMS),
                review,
                BookingUiState.Loading,
                BookingUiState.Failure(BookingReadSource.OFFERS),
                BookingUiState.Unavailable(BookingUnavailableReason.BOOKING_CLOSED),
                BookingUiState.Reserved("preview-reservation"),
                dates.copy(
                    draft = BookingDraft(),
                    calendar =
                        BookingCalendarReadState(
                            visible = CalendarInventoryRead.Content(occupied),
                        ),
                    visibleEligibleNights = emptySet(),
                    selectedEligibleNights = null,
                    stayValidation = null,
                    quote = null,
                ),
                review.copy(
                    campsite =
                        review.campsite.copy(
                            details = review.campsite.details.copy(nightlyPriceCents = 7000),
                        ),
                    quote = BookingQuote.Value(14000),
                    canConfirm = false,
                ),
                review.copy(
                    calendar = BookingCalendarReadState(conflictInventory, conflictInventory),
                    visibleEligibleNights = remaining,
                    selectedEligibleNights = remaining,
                    stayValidation = StaySelectionInput.UNAVAILABLE_NIGHTS,
                    error = BookingStepError.Dates(StaySelectionInput.UNAVAILABLE_NIGHTS),
                    canConfirm = false,
                ),
                dates.copy(
                    calendar =
                        BookingCalendarReadState(
                            CalendarInventoryRead.Loading,
                            CalendarInventoryRead.Loading,
                        ),
                    visibleEligibleNights = null,
                    selectedEligibleNights = null,
                    stayValidation = null,
                ),
                dates.copy(
                    calendar =
                        BookingCalendarReadState(
                            CalendarInventoryRead.Failure,
                            CalendarInventoryRead.Failure,
                        ),
                    visibleEligibleNights = null,
                    selectedEligibleNights = null,
                    stayValidation = null,
                ),
                previewBooking(BookingStep.TERMS).copy(
                    campsite =
                        dates.campsite.copy(
                            details =
                                dates.campsite.details.copy(
                                    name = "Pine Creek Riverside family and group camping",
                                    rules =
                                        "Quiet hours after 10 pm. Pack out litter and respect neighbouring campsites.\n"
                                            .repeat(
                                                16,
                                            ),
                                ),
                        ),
                ),
                dates.copy(
                    eligibilityPending = true,
                    visibleEligibleNights = null,
                    selectedEligibleNights = null,
                    stayValidation = null,
                ),
                review.copy(quote = BookingQuote.Overflow, canConfirm = false),
            )
        }
}

/** Themed phone previews for every passive state, including an enlarged narrow layout.
 * @param state Example source/draft facts, never connected to live services.
 */
@Preview(name = "Booking states", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(
    name = "Booking large text",
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
    fontScale = 2f,
)
@Composable
private fun BookingPreview(
    @PreviewParameter(BookingPreviewProvider::class) state: BookingUiState,
) {
    BookingPreviewContent(state)
}

/** Shows disabled confirming presentation without creating or claiming a saved reservation. */
@Preview(name = "Submitting", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(
    name = "Submitting large text",
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
    fontScale = 2f,
)
@Composable
private fun BookingSubmittingPreview() {
    BookingPreviewContent(
        previewBooking(BookingStep.REVIEW).copy(canConfirm = true),
        isSubmitting = true,
    )
}

/** Shared inert binding for Android Studio previews and device render verification.
 * @param state Actual-shaped source/draft outcome.
 * @param isSubmitting Parent-owned action guard to illustrate.
 */
@Composable
internal fun BookingPreviewContent(
    state: BookingUiState,
    isSubmitting: Boolean = false,
) {
    CampsbridgeTheme {
        BookingScreen(
            state,
            onBack = {},
            onNext = {},
            onCancel = {},
            onMonth = {},
            onDate = {},
            onClearDates = {},
            onGuests = {},
            onAccept = { _, _, _ -> },
            onEdit = {},
            onQuote = {},
            onPolicy = {},
            onRetry = {},
            onRetryVisible = {},
            onRetrySelected = {},
            onReservation = {},
            onConfirm = {},
            photo =
                if (state is BookingUiState.Content) {
                    previewCampsitePhoto(state.campsite.id)
                } else {
                    ImageSlotState(loadState = ImageLoadState.READY)
                },
            isSubmitting = isSubmitting,
        )
    }
}
