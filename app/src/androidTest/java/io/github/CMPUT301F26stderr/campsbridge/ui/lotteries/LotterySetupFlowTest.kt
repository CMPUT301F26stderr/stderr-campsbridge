package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

/**
 * Actual management/setup/allocation routes and native schedule dialogs with the reserved F13
 * pending.
 */
class LotterySetupFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val site = sampleCampsite("river/one")
    private val coast =
        sampleCampsite("coast").let {
            it.copy(
                details = it.details.copy(name = "Coastal camp", timeZone = "America/Vancouver"),
            )
        }
    private val day = LocalDate.of(2027, 5, 14)

    /** Closes graph-owned models and every choice/range listener after the case. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /**
     * All six steps and native inputs lead to honest pending review, then one actual caller
     * return.
     */
    @Test fun sixStepsReachReviewWithNativeScheduleInput() {
        start()
        toSite()
        compose.onNodeWithText(site.details.name).performClick()
        allocate(day)
        compose.onNodeWithText("Next").performClick()
        schedule("Registration opening", 5)
        schedule("Registration closing", 6)
        compose.onNodeWithText("Next").performClick()
        schedule("Booking opening", 7)
        schedule("Booking closing", 8)
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Maximum winners").assertIsDisplayed()
        compose.onNodeWithText("Unlimited registrations").assertIsDisplayed()
        compose.onNodeWithText("Maximum winners").performTextInput("2")
        closeSoftKeyboard()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 6 of 6").assertIsDisplayed()
        compose.onNodeWithText("Save draft").assertIsNotEnabled()
        compose.onNodeWithText("Publish lottery").assertIsNotEnabled()
        compose
            .onNodeWithText("Schedule validation is pending in this build.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Edit Lottery basics").performScrollTo().performClick()
        compose.onNodeWithText("May weekends").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 6 of 6").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard this lottery setup?").assertIsDisplayed()
        compose.onNodeWithText("Discard setup").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.lotterySetupCreations)
            assertEquals(0, host.campsites.writeCalls)
        }
    }

    /**
     * Working selection survives saved-state restoration; Back cancels and changing site
     * clears allocation.
     */
    @Test fun allocationRestoresAndCancelsWithinOneParent() {
        val restoration = StateRestorationTester(compose)
        start(restoration)
        toSite()
        compose.onNodeWithText(site.details.name).performClick()
        allocate(day)
        compose.onNodeWithText("Allocate nights").performClick()
        compose.onNodeWithTag("calendar-date-${day.plusDays(1)}").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("calendar-date-$day").assertIsSelected()
        compose.onNodeWithTag("calendar-date-${day.plusDays(1)}").assertIsSelected()
        pressBack()
        compose.onNodeWithText("Step 2 of 6").assertIsDisplayed()
        compose.onNodeWithText("1 night allocated").assertIsDisplayed()
        compose.onNodeWithText(coast.details.name).performClick()
        compose.onNodeWithText("0 nights allocated").assertIsDisplayed()
        compose.onNodeWithText("Allocate nights").performClick()
        compose.onNodeWithText("America/Vancouver").assertIsDisplayed()
        compose.onNodeWithTag("calendar-date-$day").assertIsNotSelected()
        compose.onNodeWithTag("calendar-date-${day.plusDays(1)}").performClick()
        compose.onNodeWithText("Apply nights").performClick()
        compose.onNodeWithText("1 night allocated").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.lotterySetupCreations)
            assertEquals(
                coast.id,
                host.campsites.observedCalendars
                    .last()
                    .first,
            )
            assertEquals(0, host.campsites.writeCalls)
            assertEquals(0, host.campsites.activeCalendarObservers)
        }
    }

    /**
     * Required inventory failure disables Apply and its retry retains the existing working
     * selection.
     */
    @Test fun allocationFailureCanRetryWithoutRepicking() {
        start()
        toSite()
        compose.onNodeWithText(site.details.name).performClick()
        compose.runOnIdle { host.campsites.calendarFailure.value = IOException("calendar offline") }
        compose.onNodeWithText("Allocate nights").performClick()
        compose.onNodeWithText("Apply nights").assertIsNotEnabled()
        compose
            .onNodeWithText(
                "Could not load allocation availability. Your working selection is retained.",
            ).assertIsDisplayed()
        compose.runOnIdle { host.campsites.calendarFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithTag("calendar-date-$day").performClick()
        compose.runOnIdle { host.campsites.calendarFailure.value = IOException("offline again") }
        compose.onNodeWithText("Apply nights").assertIsNotEnabled()
        compose.onNodeWithText("1 night allocated").assertIsDisplayed()
        compose.runOnIdle { host.campsites.calendarFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithTag("calendar-date-$day").assertIsSelected()
        compose.onNodeWithText("Apply nights").performClick()
        compose.onNodeWithText("Step 2 of 6").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.lotterySetupCreations)
            assertEquals(0, host.campsites.writeCalls)
            assertEquals(0, host.campsites.activeCalendarObservers)
        }
    }

    /**
     * A failed choice source preserves input, Back and explicit abandonment instead of
     * showing fake choices.
     */
    @Test fun failedChoicesDoNotTrapTheLocalDraft() {
        start()
        toSite()
        compose.runOnIdle { host.campsites.streamFailure.value = IOException("sites offline") }
        compose.onNodeWithText("Could not load your published campsites.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("May weekends").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        pressBack()
        compose.onNodeWithText("May weekends").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard setup").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.lotterySetupCreations)
            assertEquals(0, host.campsites.writeCalls)
        }
    }

    /** An untouched local form can leave immediately without manufacturing an unsaved draft. */
    @Test fun emptyFormBackReturnsWithoutDiscardPrompt() {
        start()
        pressBack()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.onNodeWithText("Discard this lottery setup?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, host.campsites.writeCalls) }
    }

    /**
     * Shared scrolling keeps the last schedule input and footer reachable at enlarged
     * system text.
     */
    @Test fun largeScheduleInputsAndFieldFeedbackRemainReachable() {
        val state =
            LotterySetupPreviewProvider()
                .values
                .first {
                    it.draft.step ==
                        LotteryStep.REGISTRATION
                }.copy(
                    errors =
                        LotterySetupErrors(
                            schedule =
                                mapOf(
                                    LotterySchedulePoint.REGISTRATION_OPEN to
                                        LotteryScheduleError.AMBIGUOUS_TIME,
                                ),
                        ),
                )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                CampsbridgeTheme {
                    LotterySetupScreen(
                        state,
                        YearMonth.of(2027, 5),
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        { _, _ -> },
                        { _, _ -> },
                        {},
                        {},
                        {},
                    )
                }
            }
        }
        compose
            .onNodeWithText(
                "This local time is skipped or repeated by daylight saving. Choose an unambiguous time.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Registration closing time").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsDisplayed()
    }

    /** Enters the real local setup graph through an actually published owned campsite.
     * @param restoration Optional saved-state test host using the same production graph.
     */
    private fun start(restoration: StateRestorationTester? = null) {
        host.accounts.account.value = sampleAccount(setOf(Role.ENTRANT, Role.OWNER))
        host.preferences.progress.value =
            host.preferences.progress.value
                .copy(workspace = Role.OWNER)
        host.campsites.records.value = listOf(site, coast)
        host.campsites.calendarNights.value =
            listOf(
                CalendarNight(day, true),
                CalendarNight(day.plusDays(1), true),
                CalendarNight(day.plusDays(2), true, "booked"),
            )
        if (restoration == null) {
            compose.setContent { host.Content() }
        } else {
            restoration.setContent { host.Content() }
        }
        compose.onNodeWithText(site.details.name).performClick()
        compose.onNodeWithText("Create lottery").performScrollTo().performClick()
        compose.onNodeWithText("Step 1 of 6").assertIsDisplayed()
    }

    /** Uses the actual title field and anchored Next action to reach campsite selection. */
    private fun toSite() {
        compose.onNodeWithText("Lottery title").performTextInput("May weekends")
        closeSoftKeyboard()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 2 of 6").assertIsDisplayed()
    }

    /** Selects and applies one real known free date through the shared calendar child.
     * @param date Actual offered fixture night.
     */
    private fun allocate(date: LocalDate) {
        compose.onNodeWithText("Allocate nights").performClick()
        compose.onNodeWithTag("calendar-date-$date").performClick()
        compose.onNodeWithText("Apply nights").performClick()
        compose.onNodeWithText("Step 2 of 6").assertIsDisplayed()
    }

    /** Confirms a native date and explicit default wall time for one actual schedule point.
     * @param label Localized unique opening/closing field prefix.
     * @param dayOfMonth Explicit May 2027 schedule date.
     */
    private fun schedule(
        label: String,
        dayOfMonth: Int,
    ) {
        compose.onNodeWithText("$label date").performScrollTo().performClick()
        compose.onNodeWithText("May $dayOfMonth, 2027", substring = true).performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("$label time").performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performClick()
    }
}
