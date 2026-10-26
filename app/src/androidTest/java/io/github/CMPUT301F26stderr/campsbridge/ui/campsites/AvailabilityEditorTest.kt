package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/** Actual saved-ID management/editor navigation with controlled inventory acknowledgements. */
class AvailabilityEditorTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val site = sampleCampsite("site % availability")
    private val first = LocalDate.of(2027, 5, 14)
    private val second = first.plusDays(1)

    /** Releases account/feature scopes and each range reader after the case. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Selection is separate from intent, Save guards every action, and success remains in place. */
    @Test fun saveWaitsInIndependentEditorAndBackReturnsToManagement() {
        val gate = CompletableDeferred<Unit>()
        host.campsites.calendarNights.value = listOf(CalendarNight(second, true, "reservation"))
        host.campsites.offerHandler = { _, _, _, _ -> gate.await() }
        start()
        compose.onNodeWithTag("calendar-date-$second").assertIsNotEnabled()
        compose
            .onNodeWithTag(
                "calendar-date-$first",
            ).performScrollTo()
            .performClick()
            .assertIsSelected()
        compose.onNodeWithText("Selected nights: 1").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, host.campsites.writeCalls) }
        compose.onNodeWithText("Offer nights").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.onNodeWithContentDescription("Saving date changes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        pressBack()
        compose.onNodeWithText("Availability").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                site.id,
                host.campsites.offerRequests
                    .single()
                    .id,
            )
            assertEquals(
                setOf(first),
                host.campsites.offerRequests
                    .single()
                    .dates,
            )
            gate.complete(Unit)
        }
        compose.onNodeWithText("Availability saved.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("My campsites").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.availabilityCreations)
            assertEquals(1, host.campsiteDetailCreations)
            assertEquals(1, host.campsiteListCreations)
            assertEquals(0, host.campsites.activeCalendarObservers)
        }
    }

    /** Partial failure retains feedback and retries only the unresolved second date. */
    @Test fun partialSaveRetriesRemainingChangesWithoutRepeatingConfirmedDates() {
        host.campsites.offerHandler =
            { _, _, dates, _ ->
                if (dates.single() ==
                    second
                ) {
                    throw IOException("second date failed")
                }
            }
        start()
        for (date in listOf(
            first,
            second,
        )) {
            compose.onNodeWithTag("calendar-date-$date").performScrollTo().performClick()
        }
        compose.onNodeWithText("Offer nights").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose
            .onNodeWithText(
                "Could not save all date changes. Confirmed changes are kept. Retry to finish the remaining dates.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(
                "Date choices: 1 offered; 0 not offered",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.runOnIdle { host.campsites.offerHandler = { _, _, _, _ -> } }
        compose.onNodeWithText("Retry remaining").performClick()
        compose.onNodeWithText("Availability saved.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(first, second, second),
                host.campsites.offerRequests.map {
                    it.dates.single()
                },
            )
        }
    }

    /** Both hardware and app-bar Back use explicit discard without withdrawing saved nights. */
    @Test fun unsavedWithdrawalCanKeepEditingOrDiscardWithoutAWrite() {
        host.campsites.calendarNights.value = listOf(CalendarNight(first, true))
        start()
        compose.onNodeWithTag("calendar-date-$first").performScrollTo().performClick()
        compose.onNodeWithText("Withdraw nights").performScrollTo().performClick()
        pressBack()
        compose.onNodeWithText("Discard unsaved date changes?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose
            .onNodeWithText(
                "Date choices: 0 offered; 1 not offered",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, host.campsites.writeCalls) }
    }

    /** Missing F08 is explicit in the real route and cannot silently authorize a save. */
    @Test fun missingComparisonKeepsTheEditorPreviewableAndSavePending() {
        start(comparisonAvailable = false)
        compose.onNodeWithTag("calendar-date-$first").performScrollTo().performClick()
        compose.onNodeWithText("Offer nights").performScrollTo().performClick()
        compose
            .onNodeWithText(
                "Saving date changes is pending in this build. Your date choices are retained.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, host.campsites.writeCalls) }
    }

    /** Parent and range failures have independent read-only retries on the same saved identity. */
    @Test fun failedParentAndMonthReadsRetryWithoutLeavingTheEditor() {
        host.campsites.readFailure = IOException("parent offline")
        start()
        compose.onNodeWithText("Could not load this campsite.").assertIsDisplayed()
        compose.runOnIdle {
            host.campsites.readFailure = null
            host.campsites.calendarFailure.value = IOException("month offline")
        }
        compose.onNodeWithText("Retry").performClick()
        compose
            .onNodeWithText(
                "Could not load this month. Your date changes are retained.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("calendar-date-$first").assertIsNotEnabled()
        compose.runOnIdle { host.campsites.calendarFailure.value = null }
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.onNodeWithTag("calendar-date-$first").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            assertEquals(listOf(site.id, site.id), host.campsites.readIds)
            assertEquals(0, host.campsites.writeCalls)
            assertEquals(1, host.availabilityCreations)
        }
    }

    /** Opens the actual owner management row then its registered independent inventory editor.
     * @param comparisonAvailable Whether to supply a fixed known-difference outcome for lead tests.
     */
    private fun start(comparisonAvailable: Boolean = true) {
        host.accounts.account.value = sampleAccount(setOf(Role.OWNER))
        host.campsites.records.value = listOf(site)
        host.remainingAvailabilityEdits =
            if (comparisonAvailable) { requested, _ -> requested } else null
        compose.setContent { host.Content() }
        compose.onNodeWithText(site.details.name).performClick()
        compose.onNodeWithText("Availability").performScrollTo().performClick()
    }
}
