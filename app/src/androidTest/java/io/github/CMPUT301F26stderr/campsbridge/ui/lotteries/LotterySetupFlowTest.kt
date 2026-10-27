package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import android.view.KeyEvent
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
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
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
        closeReadyKeyboard()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 6 of 6").assertIsDisplayed()
        compose.onNodeWithText("Save draft").assertIsNotEnabled()
        compose.onNodeWithText("Publish lottery").assertIsNotEnabled()
        compose
            .onNodeWithText("Schedule validation is pending in this build.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit Lottery basics").performScrollTo().performClick()
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
        compose.onNodeWithText("Discard setup").assertIsDisplayed()
        // Send system Back to the modal window without choosing the underlying activity root.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Discard setup").assertDoesNotExist()
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

    /**
     * A held Save disables exit, opens the saved record and resumes the same draft without
     * duplicates.
     */
    @Test fun saveOpensTheSavedIdentityAndContinueSetupReturnsToIt() {
        val gate = CompletableDeferred<Unit>()
        configureSaves()
        host.lotteries.createHandler = { input ->
            gate.await()
            Lottery(
                input.id,
                input.ownerId,
                input.title,
                input.description,
                input.configuration,
                0,
                Instant.parse("2027-05-01T12:00:00Z"),
            ).also {
                host.lotteries.records.value =
                    listOf(it)
            }
        }
        val restoration = StateRestorationTester(compose)
        start(restoration)
        completeReview()
        compose.onNodeWithText("Save draft").performClick()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Publish lottery").assertIsNotEnabled()
        compose.onNodeWithText("Save draft").assertIsNotEnabled()
        pressBack()
        compose.onNodeWithText("Step 6 of 6").assertIsDisplayed()
        compose.runOnIdle { gate.complete(Unit) }
        compose.onNodeWithText("Included campsites").performScrollTo().performClick()
        compose.onNodeWithText("May 14, 2027").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Continue setup").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Continue setup").performScrollTo().performClick()
        compose.onNodeWithText("Step 6 of 6").assertIsDisplayed()
        compose.onNodeWithText("Save draft").performClick()
        compose.onNodeWithText("Continue setup").performScrollTo().assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.lotteries.events.count { it == "create:lottery/new" })
            assertEquals(1, host.lotteries.offerRequests.size)
            assertEquals(2, host.lotterySetupCreations)
        }
    }

    /**
     * Failed draft writes retain review input and an explicit retry completes the same actual
     * record.
     */
    @Test fun failedSaveRetriesTheSameTargetAndKeepsTheForm() {
        configureSaves()
        val successful = host.lotteries.createHandler!!
        host.lotteries.createHandler = { throw IOException("offline") }
        start()
        completeReview()
        compose.onNodeWithText("Save draft").performClick()
        compose
            .onNodeWithText(
                "This save did not finish. Your input is retained; retry the requested action.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("May weekends").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { host.lotteries.createHandler = successful }
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.onNodeWithText("Continue setup").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, host.lotteries.events.count { it == "create:lottery/new" })
            assertEquals(1, host.lotteries.records.value.size)
        }
    }

    /**
     * Unknown publication blocks Back, survives recreation and recovers without repeating
     * Publish.
     */
    @Test fun publicationRecoveryOpensTheSavedPublishedRecordExactlyOnce() {
        configureSaves()
        host.lotteries.publishHandler = { id, _ ->
            host.lotteries.records.value =
                host.lotteries.records.value.map {
                    if (it.id == id) {
                        it.copy(publishedAt = Instant.parse("2027-05-01T12:00:00Z"))
                    } else {
                        it
                    }
                }
            host.lotteries.getHandler = { throw IOException("lost read") }
            throw IOException("lost publish acknowledgement")
        }
        val restoration = StateRestorationTester(compose)
        start(restoration)
        completeReview()
        compose.onNodeWithText("Publish lottery").performClick()
        compose
            .onNodeWithText(
                "Publication could not be confirmed. Retry the check.",
            ).assertIsDisplayed()
        pressBack()
        compose
            .onNodeWithText(
                "Publication could not be confirmed. Retry the check.",
            ).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose
            .onNodeWithText(
                "Publication could not be confirmed. Retry the check.",
            ).assertIsDisplayed()
        compose.runOnIdle { host.lotteries.getHandler = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Published").assertIsDisplayed()
        compose.onNodeWithText("Upcoming").assertIsDisplayed()
        compose.onNodeWithText("Continue setup").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Published").assertIsDisplayed()
        compose.onNodeWithText("Upcoming").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                1,
                host.lotteries.events.count {
                    it ==
                        "publish:lottery/new"
                },
            )
        }
    }

    /**
     * Supplies fixed F13 feedback and explicit acknowledgements without implementing the
     * reserved rule.
     */
    private fun configureSaves() {
        host.validateLotteryWindows = { _, _, _, _ -> LotteryWindowInput.Valid }
        host.newLotteryId = { "lottery/new" }
        host.lotteries.createHandler = { input ->
            Lottery(
                input.id,
                input.ownerId,
                input.title,
                input.description,
                input.configuration,
                0,
                Instant.parse("2027-05-01T12:00:00Z"),
            ).also {
                host.lotteries.records.value =
                    listOf(it)
            }
        }
        host.lotteries.setOfferHandler = { id, _, offer ->
            host.lotteries.serverOffers[id] = listOf(offer)
            host.lotteries.liveOffers.value = mapOf(id to listOf(offer))
        }
        host.lotteries.publishHandler = { id, _ ->
            host.lotteries.records.value =
                host.lotteries.records.value.map {
                    if (it.id == id) {
                        it.copy(publishedAt = Instant.parse("2027-05-01T12:00:00Z"))
                    } else {
                        it
                    }
                }
        }
    }

    /**
     * Uses the complete native six-step form to reach valid review for persistence interactions.
     */
    private fun completeReview() {
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
        compose.onNodeWithText("Maximum winners").performTextInput("2")
        closeReadyKeyboard()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 6 of 6").assertIsDisplayed()
        compose.onNodeWithText("May 14, 2027").performScrollTo().assertIsDisplayed()
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

    /** Waits for real IME readiness before closing it, then confirms the app window is unobscured. */
    private fun closeReadyKeyboard() {
        compose.waitUntil(15_000) { host.keyboardVisible }
        closeSoftKeyboard()
        compose.waitUntil(15_000) { !host.keyboardVisible }
    }

    /** Uses the actual title field and anchored Next action to reach campsite selection. */
    private fun toSite() {
        compose.onNodeWithText("Lottery title").performTextInput("May weekends")
        closeReadyKeyboard()
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
