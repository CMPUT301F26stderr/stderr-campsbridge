package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteInventoryUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Allocation cells expose only valid addition or removal of already-selected invalidated dates. */
class LotteryAllocationCalendarTest {
    /**
     * Offered free dates can be added; booked/unoffered dates cannot, but stale selections
     * can be removed.
     */
    @Test fun datesSeparateFreeAdditionFromInvalidSelectionRemoval() {
        val month = YearMonth.of(2027, 5)
        val state =
            lotteryAllocationCalendarState(
                LotteryAllocationUiState(
                    month,
                    setOf(month.atDay(4)),
                    CampsiteInventoryUiState.Content(
                        listOf(
                            CalendarNight(month.atDay(1), true),
                            CalendarNight(month.atDay(2), true, "booked"),
                            CalendarNight(month.atDay(3), false),
                            CalendarNight(month.atDay(4), true, "new-booking"),
                        ),
                    ),
                ),
            )
        assertTrue(state.selectionEnabled)
        assertTrue(state.days.getValue(month.atDay(1)).isSelectable)
        assertEquals(CalendarDayStatus.AVAILABLE, state.days.getValue(month.atDay(1)).status)
        assertFalse(state.days.getValue(month.atDay(2)).isSelectable)
        assertEquals(CalendarDayStatus.OCCUPIED, state.days.getValue(month.atDay(2)).status)
        assertFalse(state.days.getValue(month.atDay(3)).isSelectable)
        assertEquals(CalendarDayStatus.UNAVAILABLE, state.days.getValue(month.atDay(3)).status)
        assertTrue(state.days.getValue(month.atDay(4)).isSelectable)
        assertEquals(CalendarSelection.Dates(setOf(month.atDay(4))), state.selection)
    }

    /**
     * An unresolved or failed range never enables a calendar addition or a stale selected-
     * date action.
     */
    @Test fun unknownInventoryHasNoDateActions() {
        val month = YearMonth.of(2027, 5)
        for (inventory in listOf(
            CampsiteInventoryUiState.Loading,
            CampsiteInventoryUiState.Failure,
        )) {
            val state =
                lotteryAllocationCalendarState(
                    LotteryAllocationUiState(
                        month,
                        setOf(month.atDay(1)),
                        inventory,
                    ),
                )
            assertFalse(state.selectionEnabled)
            assertTrue(state.days.values.none { it.isSelectable })
        }
    }
}
