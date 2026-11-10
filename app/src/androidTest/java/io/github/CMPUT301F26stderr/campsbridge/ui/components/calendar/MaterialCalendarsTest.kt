package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Google date-range selection adapts to stays and explicit collections of nights. */
class MaterialCalendarsTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2027, 5)
    private val first = month.atDay(14)

    /** Choosing two dates updates one stay with an excluded checkout endpoint. */
    @Test fun oneCalendarSelectsArrivalAndCheckout() {
        var dates by mutableStateOf<Pair<LocalDate?, LocalDate?>>(null to null)
        compose.setContent {
            CampsbridgeTheme {
                StayDateRangeCalendar(known(), dates.first, dates.second, {}, { a, b -> dates = a to b })
            }
        }
        date(first).performClick()
        date(first.plusDays(2)).performClick()
        compose.runOnIdle { assertEquals(first to first.plusDays(2), dates) }
    }

    /** Adding disconnected ranges retains previous selections and removal affects only its range. */
    @Test fun rangesAreAddedAndRemovedExplicitly() {
        var selected by mutableStateOf(setOf(first.minusDays(4)))
        compose.setContent {
            CampsbridgeTheme {
                NightRangeCalendar(known().copy(selection = CalendarSelection.Dates(selected)), {}, { selected = it })
            }
        }
        date(first).performClick()
        date(first.plusDays(1)).performClick()
        compose.runOnIdle { assertEquals(setOf(first.minusDays(4)), selected) }
        compose.onNodeWithText("Add range").performClick()
        compose.runOnIdle { assertEquals(setOf(first.minusDays(4), first, first.plusDays(1)), selected) }
        compose.onNodeWithText("Remove May 14–15, 2027").performClick()
        compose.runOnIdle { assertEquals(setOf(first.minusDays(4)), selected) }
    }

    /** Known occupied dates are disabled; an unknown interior date blocks adding the whole range. */
    @Test fun occupiedAndUnknownNightsCannotBeAdded() {
        var state by mutableStateOf(known())
        var selected = emptySet<LocalDate>()
        compose.setContent {
            CampsbridgeTheme { NightRangeCalendar(state, {}, { selected = it }) }
        }
        date(first.plusDays(4)).assertIsNotEnabled()
        date(first).performClick()
        date(first.plusDays(2)).performClick()
        compose.runOnIdle { state = state.copy(days = state.days - first.plusDays(1)) }
        compose.onNodeWithText("Add range").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(emptySet<LocalDate>(), selected) }
    }

    /** Pending submission blocks additions and removal while retaining selected nights. */
    @Test fun busySelectionCannotChange() {
        compose.setContent {
            CampsbridgeTheme {
                NightRangeCalendar(
                    known().copy(selection = CalendarSelection.Dates(setOf(first))),
                    {},
                    {},
                    enabled = false,
                )
            }
        }
        compose.onNodeWithText("Add range").assertIsNotEnabled()
        compose.onNodeWithText("Remove May 14, 2027").assertIsNotEnabled()
    }

    /** A pending two-tap range survives composition restoration before it is added. */
    @Test fun pendingRangeSurvivesRestoration() {
        val restoration = StateRestorationTester(compose)
        var selected = emptySet<LocalDate>()
        restoration.setContent { CampsbridgeTheme { NightRangeCalendar(known(), {}, { selected = it }) } }
        date(first).performClick()
        restoration.emulateSavedInstanceStateRestore()
        date(first.plusDays(1)).performClick()
        compose.onNodeWithText("Add range").performClick()
        compose.runOnIdle { assertEquals(setOf(first, first.plusDays(1)), selected) }
    }

    /** Changing months retains added nights while the next range waits for its own inventory. */
    @Test fun addedNightsRemainAcrossMonths() {
        var displayed by mutableStateOf(month)
        var selected by mutableStateOf(emptySet<LocalDate>())
        compose.setContent {
            CampsbridgeTheme {
                NightRangeCalendar(
                    known(displayed).copy(selection = CalendarSelection.Dates(selected)),
                    { displayed = it },
                    { selected = it },
                )
            }
        }
        date(first).performClick()
        date(first.plusDays(1)).performClick()
        compose.onNodeWithText("Add range").performClick()
        showMonth(month.plusMonths(1))
        val next = month.plusMonths(1).atDay(14)
        date(next).performClick()
        date(next).performClick()
        compose.onNodeWithText("Add range").performClick()
        compose.runOnIdle { assertEquals(setOf(first, first.plusDays(1), next), selected) }
    }

    /** One picker keeps arrival when checkout is chosen in the following month. */
    @Test fun stayCanCrossMonths() {
        var displayed by mutableStateOf(month)
        var dates by mutableStateOf<Pair<LocalDate?, LocalDate?>>(null to null)
        compose.setContent {
            CampsbridgeTheme {
                StayDateRangeCalendar(
                    known(displayed),
                    dates.first,
                    dates.second,
                    { displayed = it },
                    { a, b -> dates = a to b },
                )
            }
        }
        val arrival = month.atEndOfMonth()
        date(arrival).performScrollTo().performClick()
        showMonth(month.plusMonths(1))
        val checkout = month.plusMonths(1).atDay(2)
        date(checkout).performClick()
        compose.runOnIdle { assertEquals(arrival to checkout, dates) }
    }

    /** Scrolls the stock month list without adding application navigation controls.
     * @param value Month in Material's default 1900–2100 year range.
     */
    private fun showMonth(value: YearMonth) {
        compose
            .onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("material-range-calendar")))
            .performScrollToIndex((value.year - 1900) * 12 + value.monthValue - 1)
        compose.waitForIdle()
    }

    /** Finds the date using Material's accessible text.
     * @param value Actual local date in the displayed month.
     */
    private fun date(value: LocalDate) =
        compose.onNode(
            hasText(
                value.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)),
                substring = true,
            ) and
                hasClickAction(),
        )

    /** Supplies a loaded month with an occupied date after three editable nights.
     * @param month Loaded inventory month.
     */
    private fun known(month: YearMonth = this.month) =
        CalendarState(
            month,
            (1..month.lengthOfMonth()).associate { day ->
                month.atDay(day) to
                    CalendarDayState(
                        if (day ==
                            18
                        ) {
                            CalendarDayStatus.OCCUPIED
                        } else {
                            CalendarDayStatus.AVAILABLE
                        },
                        day != 18,
                    )
            },
            selectionEnabled = true,
        )
}
