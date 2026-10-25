package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Builds passive realistic screen facts, without repositories, callbacks or simulated saves.
 * @param step Actual wizard page to illustrate.
 */
internal fun previewBooking(step: BookingStep = BookingStep.DATES): BookingUiState.Content {
    val now = Instant.parse("2027-05-01T12:00:00Z")
    val start = LocalDate.of(2027, 5, 14)
    val end = start.plusDays(2)
    val site = previewCampsite().copy(id = "pine-creek", ownerId = "owner")
    val nights = setOf(start, start.plusDays(1), start.plusDays(7), start.plusDays(8))
    val inventory =
        CalendarInventoryRead.Content(
            nights.sorted().map { CalendarNight(it, true) } +
                CalendarNight(start.minusDays(7), true, "another-reservation"),
        )
    val agreement = BookingAgreement(site.details.rules, BookingPolicy.VERSION)
    return BookingUiState.Content(
        Lottery(
            "spring-draw",
            "owner",
            "Spring camping",
            "May stays at Pine Creek",
            LotteryConfiguration(
                now.minusSeconds(240),
                now.minusSeconds(120),
                now.minusSeconds(60),
                now.plusSeconds(86400),
                1,
                null,
            ),
            0,
            now.minusSeconds(300),
            now.minusSeconds(300),
            now.minusSeconds(90),
        ),
        site,
        LotteryOffer(site.id, nights.sorted()),
        BookingDraft(
            start,
            end,
            "3",
            step,
            agreement =
                agreement.takeIf {
                    step ==
                        BookingStep.REVIEW
                },
            reviewedTotalCents = 13000.takeIf { step == BookingStep.REVIEW }?.toLong(),
        ),
        YearMonth.from(start),
        LocalDate.of(2027, 5, 1),
        BookingCalendarReadState(inventory, inventory),
        eligibilityPending = false,
        visibleEligibleNights = nights,
        selectedEligibleNights = nights,
        stayValidation = StaySelectionInput.VALID,
        quote = BookingQuote.Value(13000),
        guestCount = 3,
        canConfirm = step == BookingStep.REVIEW,
    )
}

/** Shows each actual step plus unknown, pending, failed and changed-review outcomes. */
class BookingPreviewProvider : PreviewParameterProvider<BookingUiState> {
    override val values: Sequence<BookingUiState> =
        sequenceOf(
            *BookingStep.entries.map { previewBooking(it) }.toTypedArray(),
            BookingUiState.Loading,
            BookingUiState.Failure(BookingReadSource.OPPORTUNITY),
            BookingUiState.Unavailable(BookingUnavailableReason.BOOKING_CLOSED),
            previewBooking().copy(
                eligibilityPending = true,
                visibleEligibleNights = null,
                selectedEligibleNights = null,
                stayValidation = null,
            ),
            previewBooking().copy(
                calendar =
                    BookingCalendarReadState(
                        CalendarInventoryRead.Failure,
                        CalendarInventoryRead.Failure,
                    ),
                visibleEligibleNights = null,
                selectedEligibleNights = null,
                stayValidation = null,
            ),
            previewBooking(BookingStep.REVIEW).let {
                it.copy(
                    draft = it.draft.copy(reviewedTotalCents = null, agreement = null),
                    canConfirm = false,
                )
            },
        )
}

/** Renders production content with inert callbacks; previewing cannot reserve or acknowledge.
 * @param state Supplied real-shaped screen outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun BookingPreview(
    @PreviewParameter(BookingPreviewProvider::class) state: BookingUiState,
) =
    BookingPreviewContent(state, BookingSubmission.Idle)

/** Supplies actual pending and retry outcomes without starting any operation. */
class BookingSubmissionPreviewProvider : PreviewParameterProvider<BookingSubmission> {
    override val values: Sequence<BookingSubmission> =
        sequenceOf(
            BookingSubmission.Submitting,
            BookingSubmission.Recovering,
            BookingSubmission.Unknown,
            BookingSubmission.Absent,
            BookingSubmission.Rejected(PersistenceFailure.Reason.CONFLICT),
        )
}

/** Illustrates submission/recovery feedback with inert callbacks and no fake booking success.
 * @param submission Supplied command phase or recovery result.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BookingSubmissionPreview(
    @PreviewParameter(BookingSubmissionPreviewProvider::class) submission: BookingSubmission,
) =
    BookingPreviewContent(previewBooking(BookingStep.REVIEW), submission)

/** Shares passive callback binding for screen and operation-state previews.
 * @param state Actual-shaped source/draft facts.
 * @param submission Independent command phase to display.
 */
@Composable
private fun BookingPreviewContent(
    state: BookingUiState,
    submission: BookingSubmission,
) {
    CampsbridgeTheme {
        BookingScreen(
            state,
            false,
            false,
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            { _, _, _ -> },
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            submission,
            {},
            {},
        )
    }
}
