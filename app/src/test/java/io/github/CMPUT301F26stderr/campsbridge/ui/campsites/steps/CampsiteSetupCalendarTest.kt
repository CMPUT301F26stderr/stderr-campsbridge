package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteInventoryUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayStatus
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
            CampsiteInventoryUiState.Content(
                listOf(
                    CalendarNight(offered, true),
                    CalendarNight(booked, true, "reservation"),
                ),
            )
        val state =
            setupCalendarState(
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
            CampsiteInventoryUiState.Loading,
            CampsiteInventoryUiState.Failure,
        )) {
            val state = setupCalendarState(month, inventory, emptyMap())
            assertFalse(state.selectionEnabled)
            assertTrue(state.days.values.none { it.isSelectable })
        }
    }

    /** Display cells are exactly the visible month; other-month input remains parent-owned. */
    @Test fun monthMappingDoesNotInventOtherMonthCells() {
        val state =
            setupCalendarState(
                month,
                CampsiteInventoryUiState.Content(emptyList()),
                mapOf(month.plusMonths(1).atDay(1) to true),
            )
        assertEquals(month.lengthOfMonth(), state.days.size)
        assertTrue(state.days.keys.all { YearMonth.from(it) == month })
        assertTrue(state.days.values.all { it.isSelectable })
    }
}
