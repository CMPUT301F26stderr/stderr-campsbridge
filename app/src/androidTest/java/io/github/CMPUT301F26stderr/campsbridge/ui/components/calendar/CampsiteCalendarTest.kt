package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Shared inventory rendering emits actions without owning persistence or selection. */
class CampsiteCalendarTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2027, 5)
    private val first = month.atDay(1)

    /** Navigation requests a month; only externally supplied state changes the display. */
    @Test fun monthNavigationIsControlled() {
        var state by mutableStateOf(CalendarState(month))
        val requests = mutableListOf<YearMonth>()
        compose.setContent { CampsbridgeTheme { CampsiteCalendar(state, requests::add, {}) } }
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(month.minusMonths(1), month.plusMonths(1)),
                requests,
            )
        }
        compose.onNodeWithText("May 2027").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(month = month.plusMonths(1)) }
        compose.onNodeWithText("June 2027").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, requests.size) }
    }

    /** Supplied selectability, rather than display color, determines which taps are delivered. */
    @Test fun datesExposeSelectionAndRespectDisabledState() {
        val calls = mutableListOf<LocalDate>()
        val state =
            CalendarState(
                month,
                days =
                    mapOf(
                        first to CalendarDayState(CalendarDayStatus.AVAILABLE, true),
                        first.plusDays(1) to CalendarDayState(CalendarDayStatus.OCCUPIED, false),
                        first.plusDays(3) to CalendarDayState(CalendarDayStatus.UNAVAILABLE, true),
                    ),
                selection = CalendarSelection.Dates(setOf(first)),
                selectionEnabled = true,
            )
        compose.setContent { CampsbridgeTheme { CampsiteCalendar(state, {}, calls::add) } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose
            .onNodeWithTag("calendar-date-$first")
            .assertContentDescriptionEquals(
                first.format(
                    DateTimeFormatter
                        .ofLocalizedDate(FormatStyle.FULL)
                        .withLocale(context.resources.configuration.locales[0]),
                ),
            ).assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.calendar_available),
                ),
            ).assertIsSelected()
            .performScrollTo()
            .performClick()
        compose
            .onNodeWithTag("calendar-date-${first.plusDays(1)}")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.calendar_occupied),
                ),
            ).assertIsNotEnabled()
            .performTouchInput { click() }
        compose
            .onNodeWithTag("calendar-date-${first.plusDays(2)}")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.calendar_unavailable),
                ),
            ).assertIsNotEnabled()
        compose.onNodeWithTag("calendar-date-${first.plusDays(3)}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(first, first.plusDays(3)), calls) }
    }

    /** Read-only stays highlight nights through the night before checkout without writes. */
    @Test fun readOnlyRangeExcludesCheckout() {
        var calls = 0
        val state =
            CalendarState(month, selection = CalendarSelection.Stay(first, first.plusDays(2)))
        compose.setContent { CampsbridgeTheme { CampsiteCalendar(state, {}, { calls++ }) } }
        compose.onNodeWithTag("calendar-date-$first").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithTag("calendar-date-${first.plusDays(1)}").assertIsSelected()
        compose.onNodeWithTag("calendar-date-${first.plusDays(2)}").assertIsNotSelected()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    /** Enlarged text and controls retain accessible targets and reachable late-week dates. */
    @Test fun enlargedCalendarRemainsUsable() {
        val last = month.atDay(30) // Sunday exercises horizontal reachability at enlarged sizes.
        var selected: LocalDate? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                CampsbridgeTheme(AccountSettings(controlScale = 1.5)) {
                    CampsiteCalendar(
                        CalendarState(
                            month,
                            days =
                                mapOf(
                                    last to CalendarDayState(CalendarDayStatus.AVAILABLE, true),
                                ),
                            selectionEnabled = true,
                        ),
                        {},
                        { selected = it },
                    )
                }
            }
        }
        compose
            .onNodeWithTag("calendar-date-$last")
            .performScrollTo()
            .assertIsDisplayed()
            .assertWidthIsAtLeast(72.dp)
            .performClick()
        compose.runOnIdle { assertEquals(last, selected) }
    }
}
