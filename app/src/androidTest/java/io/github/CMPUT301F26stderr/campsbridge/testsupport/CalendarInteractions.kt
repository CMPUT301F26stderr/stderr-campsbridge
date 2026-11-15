package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Finds Google's accessible date cell without depending on its internal layout.
 * @param date Actual local date in the visible calendar.
 */
fun ComposeTestRule.onCalendarDate(date: LocalDate): SemanticsNodeInteraction =
    onNode(
        hasText(
            date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)),
            substring = true,
        ) and
            hasClickAction(),
    )

/** Adds an inclusive range through the same controls as an owner.
 * @param start First night to add.
 * @param end Last night, or the same date for one night.
 */
fun ComposeTestRule.addCalendarNights(
    start: LocalDate,
    end: LocalDate = start,
) {
    onCalendarDate(start).performScrollTo().performClick()
    onCalendarDate(end).performScrollTo().performClick()
    onNodeWithText("Add range").performScrollTo().performClick()
}
