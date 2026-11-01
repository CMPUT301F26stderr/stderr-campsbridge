package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Selection permissions never confuse excluded checkout with physical inventory status. */
class BookingCalendarTest {
    private val month = YearMonth.of(2027, 5)
    private val start = month.atDay(14)
    private val end = start.plusDays(2)
    private val eligible = setOf(start, start.plusDays(1), month.atDay(21), month.atDay(22))
    private val nights =
        eligible.sorted().map { CalendarNight(it, true) } +
            listOf(
                CalendarNight(month.atDay(7), true, "reserved"),
                CalendarNight(month.atDay(20), true),
            )
    private val state =
        BookingUiState.Content(
            CampingFixtures.lottery(),
            CampingFixtures.campsite(),
            LotteryOffer("site", eligible.sorted()),
            BookingDraft(),
            month,
            month.atDay(1),
            calendar = BookingCalendarReadState(visible = CalendarInventoryRead.Content(nights)),
            eligibilityPending = false,
            visibleEligibleNights = eligible,
        )

    /** First selection uses eligible nonpast arrivals and retains physical occupied labels. */
    @Test fun initialSelectionUsesSuppliedEligibilityAndActualOccupiedStatus() {
        val calendar = bookingCalendarState(state)
        assertTrue(calendar.selectionEnabled)
        assertEquals(31, calendar.days.size)
        assertEquals(CalendarSelection.None, calendar.selection)
        assertEquals(CalendarDayStatus.AVAILABLE, calendar.days[start]?.status)
        assertEquals(true, calendar.days[start]?.isSelectable)
        assertEquals(CalendarDayStatus.OCCUPIED, calendar.days[month.atDay(7)]?.status)
        assertEquals(false, calendar.days[month.atDay(7)]?.isSelectable)
        assertEquals(CalendarDayStatus.UNAVAILABLE, calendar.days[month.atDay(20)]?.status)
        assertEquals(false, calendar.days[month.atDay(20)]?.isSelectable)
        assertEquals(false, calendar.days[end]?.isSelectable)
    }

    /** Past free nights retain inventory facts without permitting a new past arrival. */
    @Test fun pastNightsAreNotSelectableArrivals() {
        val calendar = bookingCalendarState(state.copy(today = start.plusDays(1)))
        assertEquals(CalendarDayStatus.AVAILABLE, calendar.days[start]?.status)
        assertEquals(false, calendar.days[start]?.isSelectable)
        assertEquals(true, calendar.days[start.plusDays(1)]?.isSelectable)
    }

    /** Loading/failure/hidden scopes preserve selection without presenting confirmed inventory. */
    @Test fun unknownVisibleRangeDisablesActionsButRetainsSelectedNights() {
        for (outcome in listOf(
            null,
            CalendarInventoryRead.Loading,
            CalendarInventoryRead.Failure,
        )) {
            val calendar =
                bookingCalendarState(
                    state.copy(
                        draft = BookingDraft(start, end),
                        calendar = BookingCalendarReadState(visible = outcome),
                        visibleEligibleNights = null,
                    ),
                )
            assertFalse(calendar.selectionEnabled)
            assertTrue(calendar.days.values.none { it.isSelectable })
            assertEquals(CalendarSelection.Stay(start, end), calendar.selection)
        }
    }

    /** Known empty inventory remains a real unavailable month rather than loading. */
    @Test fun confirmedEmptyInventoryHasNoSelectableDates() {
        val calendar =
            bookingCalendarState(
                state.copy(
                    calendar =
                        BookingCalendarReadState(
                            visible = CalendarInventoryRead.Content(emptyList()),
                        ),
                    visibleEligibleNights = emptySet(),
                ),
            )
        assertTrue(calendar.selectionEnabled)
        assertEquals(31, calendar.days.size)
        assertTrue(
            calendar.days.values.all {
                !it.isSelectable &&
                    it.status == CalendarDayStatus.UNAVAILABLE
            },
        )
    }

    /** Absence of F15 never enables dates using physical free rows as a replacement algorithm. */
    @Test fun pendingEligibilityDisablesAllDateActions() {
        val calendar =
            bookingCalendarState(
                state.copy(
                    eligibilityPending = true,
                    visibleEligibleNights = null,
                ),
            )
        assertFalse(calendar.selectionEnabled)
        assertTrue(calendar.days.values.none { it.isSelectable })
    }

    /** Completed range highlights only occupied nights; its next tap chooses a new arrival. */
    @Test fun completedStayExcludesCheckoutAndRestartsWithEligibleArrivalsOnly() {
        val calendar = bookingCalendarState(state.copy(draft = BookingDraft(start, end)))
        assertEquals(CalendarSelection.Stay(start, end), calendar.selection)
        assertTrue(start in calendar.selection)
        assertTrue(start.plusDays(1) in calendar.selection)
        assertFalse(end in calendar.selection)
        assertEquals(false, calendar.days[end]?.isSelectable)
    }

    /** A checkout endpoint immediately after an eligible night may itself be unoffered. */
    @Test fun partialStayPermitsUnofferedCheckoutEndpoint() {
        val calendar = bookingCalendarState(state.copy(draft = BookingDraft(checkIn = start)))
        assertEquals(CalendarSelection.Dates(setOf(start)), calendar.selection)
        assertEquals(CalendarDayStatus.UNAVAILABLE, calendar.days[end]?.status)
        assertEquals(true, calendar.days[end]?.isSelectable)
        assertEquals(false, calendar.days[end.plusDays(1)]?.isSelectable)
    }

    /** An occupied checkout keeps its inventory label while permitting departure only. */
    @Test fun occupiedCheckoutDoesNotBecomeAnOccupiedStayNight() {
        val calendar =
            bookingCalendarState(
                state.copy(
                    draft = BookingDraft(checkIn = start),
                    calendar =
                        BookingCalendarReadState(
                            visible =
                                CalendarInventoryRead.Content(
                                    nights + CalendarNight(end, true, "someone-else"),
                                ),
                        ),
                ),
            )
        assertEquals(CalendarDayStatus.OCCUPIED, calendar.days[end]?.status)
        assertEquals(true, calendar.days[end]?.isSelectable)
        assertFalse(end in calendar.selection)
    }

    /** First-of-month checkout can use the retained selected arrival from the preceding month. */
    @Test fun crossMonthCheckoutUsesConfirmedOffscreenSelection() {
        val arrival = month.atDay(1).minusDays(1)
        val calendar =
            bookingCalendarState(
                state.copy(
                    draft = BookingDraft(checkIn = arrival),
                    calendar =
                        BookingCalendarReadState(
                            CalendarInventoryRead.Content(emptyList()),
                            CalendarInventoryRead.Content(listOf(CalendarNight(arrival, true))),
                        ),
                    today = arrival.minusDays(1),
                    visibleEligibleNights = emptySet(),
                    selectedEligibleNights = setOf(arrival),
                ),
            )
        assertEquals(true, calendar.days[month.atDay(1)]?.isSelectable)
        assertEquals(CalendarDayStatus.UNAVAILABLE, calendar.days[month.atDay(1)]?.status)
        assertEquals(false, calendar.days[month.atDay(2)]?.isSelectable)
    }

    /** Invalid raw endpoints render safely instead of constructing an invalid domain selection. */
    @Test fun reversedRangeRetainsArrivalHighlightWithoutThrowing() {
        val calendar =
            bookingCalendarState(state.copy(draft = BookingDraft(start, start.minusDays(1))))
        assertEquals(CalendarSelection.Dates(setOf(start)), calendar.selection)
    }

    /** A partial arrival made past by local midnight requires a fresh eligible arrival. */
    @Test fun pastPartialSelectionDoesNotEnableExtraCheckoutDates() {
        val calendar =
            bookingCalendarState(
                state.copy(
                    draft = BookingDraft(checkIn = start),
                    today = start.plusDays(1),
                ),
            )
        assertEquals(true, calendar.days[start.plusDays(1)]?.isSelectable)
        assertEquals(false, calendar.days[end]?.isSelectable)
    }

    /** A retired dates step cannot expose actions from stale visible inventory. */
    @Test fun nonDateStepDisablesSelectionEvenWithKnownInventory() {
        val calendar =
            bookingCalendarState(state.copy(draft = BookingDraft(step = BookingStep.GUESTS)))
        assertFalse(calendar.selectionEnabled)
        assertTrue(calendar.days.values.none { it.isSelectable })
    }

    /** Passive step fixtures keep coherent inventory and prices without enabling confirmation. */
    @Test fun previewFixturesKeepBookingFactsConsistent() {
        for (step in BookingStep.entries) {
            val preview = previewBooking(step)
            assertEquals(step, preview.draft.step)
            assertEquals(preview.campsite.id, preview.offer.campsiteId)
            assertEquals(preview.campsite.ownerId, preview.lottery.ownerId)
            assertEquals(BookingQuote.Value(13000), preview.quote)
            assertEquals(3L, preview.guestCount)
            assertFalse(preview.canConfirm)
            val calendar = bookingCalendarState(preview)
            assertEquals(step == BookingStep.DATES, calendar.selectionEnabled)
            assertEquals(CalendarSelection.Stay(start, end), calendar.selection)
        }
    }
}
