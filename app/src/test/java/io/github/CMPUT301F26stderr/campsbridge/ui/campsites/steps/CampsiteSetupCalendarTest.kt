package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.offeredNightCalendarState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Verifies setup's caller-supplied inventory mapping, without duplicating calendar geometry. */
class CampsiteSetupCalendarTest {
    private val month = YearMonth.of(2027, 5)

    /** Known missing/unoffered dates are editable; an existing claim stays visible and
     * protected.
     */
    @Test fun knownMonthSeparatesOfferIntentFromReservationClaims() {
        val offered = month.atDay(14)
        val booked = month.atDay(15)
        val absent = month.atDay(16)
        val inventory =
            CalendarInventoryRead.Content(
                listOf(
                    CalendarNight(offered, true),
                    CalendarNight(booked, true, "reservation"),
                ),
            )
        val state =
            offeredNightCalendarState(
                month,
                inventory,
                mapOf(
                    offered to false,
                    absent to true,
                    booked to false,
                ),
            )
        assertEquals(CalendarDayStatus.UNAVAILABLE, state.days.getValue(offered).status)
        assertTrue(state.days.getValue(offered).isSelectable)
        assertEquals(CalendarDayStatus.AVAILABLE, state.days.getValue(absent).status)
        assertTrue(state.days.getValue(absent).isSelectable)
        assertEquals(CalendarDayStatus.OCCUPIED, state.days.getValue(booked).status)
        assertFalse(state.days.getValue(booked).isSelectable)
        assertTrue(offered in state.selection)
    }

    /** Pending/failed ranges cannot supply selectable dates or claim a known empty baseline. */
    @Test fun unknownRangeHasNoEditableDates() {
        for (inventory in listOf(
            CalendarInventoryRead.Loading,
            CalendarInventoryRead.Failure,
        )) {
            val state = offeredNightCalendarState(month, inventory, emptyMap())
            assertFalse(state.selectionEnabled)
            assertTrue(state.days.values.none { it.isSelectable })
        }
    }

    /** Working selection can differ from desired offers without changing those displayed facts. */
    @Test fun independentWorkingSelectionDoesNotChangeRequestedOfferStatus() {
        val first = month.atDay(14)
        val second = first.plusDays(1)
        val state =
            offeredNightCalendarState(
                month,
                CalendarInventoryRead.Content(emptyList()),
                mapOf(first to true),
                setOf(second),
            )
        assertEquals(CalendarDayStatus.AVAILABLE, state.days.getValue(first).status)
        assertEquals(CalendarSelection.Dates(setOf(second)), state.selection)
    }

    /** Display cells are exactly the visible month; other-month input remains parent-owned. */
    @Test fun monthMappingDoesNotInventOtherMonthCells() {
        val state =
            offeredNightCalendarState(
                month,
                CalendarInventoryRead.Content(emptyList()),
                mapOf(month.plusMonths(1).atDay(1) to true),
            )
        assertEquals(month.lengthOfMonth(), state.days.size)
        assertTrue(state.days.keys.all { YearMonth.from(it) == month })
        assertTrue(state.days.values.all { it.isSelectable })
    }
}
