package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.testsupport.onCalendarDate
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** Stock Material inspection keeps inventory labels separate from editable selections. */
class CampsiteCalendarTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2027, 5)
    private val first = month.atDay(14)

    /** Stock month navigation requests the new inventory month without writing dates. */
    @Test fun monthNavigationLoadsInventory() {
        var state by mutableStateOf(CalendarState(month))
        val requests = mutableListOf<YearMonth>()
        compose.setContent {
            CampsbridgeTheme {
                CampsiteCalendar(state, {
                    requests += it
                    state = state.copy(month = it)
                })
            }
        }
        compose.onNodeWithContentDescription("Change to next month").performClick()
        compose.onNodeWithText("June 2027").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(month.plusMonths(1)), requests) }
        compose.onNodeWithContentDescription("Change to previous month").performClick()
        compose.onNodeWithText("May 2027").assertIsDisplayed()
    }

    /** Inspection distinguishes occupied, available and unknown dates through readable text. */
    @Test fun inventoryRemainsReadableOutsideDateCells() {
        val state =
            CalendarState(
                month,
                mapOf(
                    first to CalendarDayState(CalendarDayStatus.AVAILABLE),
                    first.plusDays(1) to CalendarDayState(CalendarDayStatus.OCCUPIED),
                ),
            )
        compose.setContent { CampsbridgeTheme { CampsiteCalendar(state, {}) } }
        compose.onNodeWithText("Available: May 14, 2027").assertIsDisplayed()
        compose.onNodeWithText("Occupied: May 15, 2027").assertIsDisplayed()
        compose.onCalendarDate(first.plusDays(2)).assertIsNotEnabled()
        compose.onCalendarDate(first.plusDays(1)).performClick().assertIsSelected()
        compose.onNodeWithText("May 15, 2027: Occupied").assertIsDisplayed()
        compose.onNodeWithText("Add range").assertDoesNotExist()
    }

    /** Enlarged text preserves stock touch targets and date inspection. */
    @Test fun enlargedCalendarRemainsUsable() {
        val last = month.atDay(30)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                CampsbridgeTheme(AccountSettings(controlScale = 1.5)) {
                    CampsiteCalendar(
                        CalendarState(month, mapOf(last to CalendarDayState(CalendarDayStatus.AVAILABLE))),
                        {},
                    )
                }
            }
        }
        compose
            .onCalendarDate(last)
            .performScrollTo()
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher("At least 72 dp at the screen density") { node ->
                    node.boundsInRoot.width >= with(compose.density) { 72.dp.toPx() } - 1f
                },
            ).performClick()
        compose.onCalendarDate(last).assertIsSelected()
    }
}
