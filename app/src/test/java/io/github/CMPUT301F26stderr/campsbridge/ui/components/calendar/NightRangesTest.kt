package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Selection contracts for Material range picking over loaded inventory. */
class NightRangesTest {
    private val month = YearMonth.of(2027, 5)
    private val first = month.atDay(14)

    /** Grouping preserves separate ranges, month boundaries and single nights. */
    @Test fun groupsOnlyConsecutiveSelectedNights() {
        assertEquals(emptyList<NightRange>(), nightRanges(emptySet()))
        assertEquals(
            listOf(NightRange(first, first.plusDays(1)), NightRange(first.plusDays(4), first.plusDays(4))),
            nightRanges(setOf(first.plusDays(4), first.plusDays(1), first)),
        )
        assertEquals(
            listOf(NightRange(month.atEndOfMonth(), month.plusMonths(1).atDay(1))),
            nightRanges(setOf(month.atEndOfMonth(), month.plusMonths(1).atDay(1))),
        )
    }

    /** Owner ranges include both endpoints and can contain one night. */
    @Test fun selectsCompleteInclusiveRanges() {
        val state = known()
        assertEquals(setOf(first), selectableNightRange(state, first, first))
        assertEquals(
            setOf(first, first.plusDays(1), first.plusDays(2)),
            selectableNightRange(state, first, first.plusDays(2)),
        )
    }

    /** A range cannot silently skip an occupied or unknown interior night. */
    @Test fun rejectsPartialUnknownOccupiedAndDisabledRanges() {
        val state = known()
        assertNull(selectableNightRange(state, null, first))
        assertNull(selectableNightRange(state, first, null))
        assertNull(selectableNightRange(state, first.plusDays(1), first))
        assertNull(selectableNightRange(state.copy(selectionEnabled = false), first, first))
        assertNull(selectableNightRange(state, first, first.plusDays(3)))
        assertNull(selectableNightRange(state.copy(days = state.days - first.plusDays(1)), first, first.plusDays(2)))
        assertNull(
            selectableNightRange(
                state.copy(
                    days =
                        state.days +
                            (first.plusDays(1) to CalendarDayState(CalendarDayStatus.OCCUPIED, false)),
                ),
                first,
                first.plusDays(2),
            ),
        )
    }

    /** Supplies three editable nights in a confirmed month. */
    private fun known(): CalendarState =
        CalendarState(
            month,
            (0L..2L).associate { first.plusDays(it) to CalendarDayState(CalendarDayStatus.AVAILABLE, true) },
            selectionEnabled = true,
        )
}
