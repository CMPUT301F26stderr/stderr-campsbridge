package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.bookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Supplies passive booking facts without repositories, clocks, commands or navigation.
 * @param step Draft step to illustrate; confirmation remains disabled in these local fixtures.
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
    return BookingUiState.Content(
        lottery =
            Lottery(
                "spring-draw",
                site.ownerId,
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
        campsite = site,
        offer = LotteryOffer(site.id, nights.sorted()),
        draft =
            BookingDraft(
                checkIn = start,
                checkOut = end,
                guestInput = "3",
                step = step,
                agreement =
                    BookingAgreement(site.details.rules, BookingPolicy.VERSION)
                        .takeIf { step == BookingStep.REVIEW },
                reviewedTotalCents = 13000L.takeIf { step == BookingStep.REVIEW },
            ),
        month = YearMonth.from(start),
        today = LocalDate.of(2027, 5, 1),
        calendar = BookingCalendarReadState(inventory, inventory),
        eligibilityPending = false,
        visibleEligibleNights = nights,
        selectedEligibleNights = nights,
        stayValidation = StaySelectionInput.VALID,
        quote = bookingQuote(start, end, site.details.nightlyPriceCents),
        guestCount = 3,
    )
}
