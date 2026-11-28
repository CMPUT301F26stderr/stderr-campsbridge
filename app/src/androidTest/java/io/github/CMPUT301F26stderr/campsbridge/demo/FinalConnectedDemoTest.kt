/*
 * JT-01 follows one fresh shared lottery through the actual demo launcher and production routes.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import io.github.CMPUT301F26stderr.campsbridge.testsupport.addCalendarNights
import io.github.CMPUT301F26stderr.campsbridge.testsupport.onCalendarDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** JT-01 follows one fresh shared lottery through the actual demo launcher and production routes. */
class FinalConnectedDemoTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** JT-01: both owners contribute before publication, then one entrant books B and both message. */
    @Test fun freshCollaboratingLotteryBooksTheContributingOwnersSiteAndRetainsAdminMessageHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        ActivityScenario.launch<OfflineDemoActivity>(intent).use { scenario ->
            waitFor("Demo tools")
            reset(scenario)
            waitFor("Hosting")
            open("Hosting")
            open("River Camp")
            open("Create lottery")
            compose.onNodeWithText("Lottery title").performTextInput("Final shared journey")
            closeSoftKeyboard()
            open("Next")
            open("Allocate nights")
            compose.addCalendarNights(LocalDate.of(2027, 5, 25))
            open("Apply nights")
            open("Next")
            schedule("Registration opening", 11)
            schedule("Registration closing", 12)
            open("Next")
            schedule("Booking opening", 13)
            schedule("Booking closing", 14)
            open("Next")
            compose.onNodeWithText("Maximum winners").performTextReplacement("1")
            closeSoftKeyboard()
            open("Next")
            open("Save draft")
            waitFor("Collaborating owners")
            open("Collaborating owners")
            open("Sam Rivera")
            open("Save owners")
            waitFor("Collaborating owners")
            val first = session(scenario)
            val draft =
                runBlocking {
                    first.services.repositories.lotteries.observeForOwner("demo-host").first().single {
                        it.title ==
                            "Final shared journey"
                    }
                }
            assertEquals("demo-host", draft.ownerId)
            assertTrue(
                draft.publishedAt == null && draft.participantOwnerIds.containsAll(setOf("demo-host", "demo-cohost")),
            )
            person(scenario, "Collaborating owner", DemoScenario.CO_OWNER)
            open("Lotteries")
            open("Drafts")
            open(draft.title)
            open("Manage my campsites")
            open("Add campsite")
            open("Cedar Ridge")
            compose.addCalendarNights(LocalDate.of(2027, 5, 18), LocalDate.of(2027, 5, 19))
            open("Apply nights")
            open("Save campsites")
            scenario.recreate()
            waitFor("Included campsites")
            val contributed =
                runBlocking {
                    session(scenario)
                        .services.repositories.lotteries
                        .getOffers(draft.id)
                }
            assertEquals(setOf("demo-river", "demo-cedar"), contributed.map { it.campsiteId }.toSet())
            open("Hosting")
            waitFor("Continue setup")
            open("Continue setup")
            open("Publish lottery")
            waitFor("Notify entrants")
            val published =
                runBlocking {
                    checkNotNull(
                        session(scenario)
                            .services.repositories.lotteries
                            .get(draft.id),
                    )
                }
            assertTrue(published.publishedAt != null)
            runBlocking {
                session(
                    scenario,
                ).store.advanceTo(checkNotNull(published.configuration).registrationOpensAt.plusSeconds(1))
            }
            open("Camping")
            waitFor("Search campsites")
            compose.onNodeWithText("Search campsites").performTextReplacement("Cedar Ridge")
            compose.onNodeWithText("Search").performClick()
            compose
                .onNode(
                    hasText("Cedar Ridge") and hasAnyAncestor(hasScrollAction()),
                ).performScrollTo()
                .performClick()
            open("Join waitlist")
            waitFor("You are on the waitlist.")
            open("View lottery")
            runBlocking {
                session(
                    scenario,
                ).store.advanceTo(checkNotNull(published.configuration).registrationClosesAt.plusSeconds(1))
            }
            open("Hosting")
            waitFor("Run draw")
            open("Run draw")
            runBlocking {
                session(
                    scenario,
                ).store.advanceTo(checkNotNull(published.configuration).bookingOpensAt.plusSeconds(1))
            }
            open("Camping")
            waitFor("Book your stay")
            open("Book your stay")
            compose.onNodeWithContentDescription("Choose Cedar Ridge").performScrollTo().performClick()
            compose.onNodeWithTag("booking-calendar").performScrollTo()
            compose.onCalendarDate(LocalDate.of(2027, 5, 18)).performScrollTo().performClick()
            compose.onCalendarDate(LocalDate.of(2027, 5, 20)).performScrollTo().performClick()
            open("Next")
            open("Next")
            open("I have read and accept these rules and the reservation policy.")
            open("Next")
            open("Confirm reservation")
            waitFor("Messages")
            val reservation =
                runBlocking {
                    checkNotNull(
                        session(scenario)
                            .services.repositories.bookings
                            .getForOpportunity(draft.id, "demo-account"),
                    )
                }
            assertEquals("demo-cedar", reservation.campsiteId)
            assertEquals("demo-account", reservation.entrantId)
            open("Messages")
            compose
                .onNodeWithTag(
                    "message-input",
                ).performScrollTo()
                .performTextInput("Final journey arrival: is the east path open?")
            open("Send message")
            waitFor("Final journey arrival: is the east path open?")
            person(scenario, "Collaborating owner", DemoScenario.CO_OWNER)
            compose.onNodeWithContentDescription("Back").performClick()
            open("Reservations")
            compose.onNodeWithTag("reservation:${reservation.id}").performScrollTo().performClick()
            open("Messages")
            waitFor("Final journey arrival: is the east path open?")
            compose
                .onNodeWithTag(
                    "message-input",
                ).performScrollTo()
                .performTextInput("Yes, we will meet you at the east entrance.")
            open("Send message")
            waitFor("Yes, we will meet you at the east entrance.")
            val records =
                runBlocking {
                    withTimeout(15000) {
                        session(
                            scenario,
                        ).services.repositories.messages.observeConversation(reservation.id).first { rows ->
                            rows.any {
                                it.senderId == "demo-cohost" &&
                                    it.body == "Yes, we will meet you at the east entrance."
                            }
                        }
                    }
                }
            val entrantMessage = records.single { it.senderId == "demo-account" }
            assertTrue(
                records.any {
                    it.senderId == "demo-cohost" && it.body == "Yes, we will meet you at the east entrance."
                },
            )
            person(scenario, "Administrator", DemoScenario.ADMIN)
            open("Logs")
            open("Messages")
            compose.onNodeWithText("Search").performTextInput("Final journey arrival")
            compose.onNodeWithTag("admin-message-${entrantMessage.id}").performScrollTo().performClick()
            compose.onNodeWithText(entrantMessage.body).performScrollTo().assertIsDisplayed()
            scenario.recreate()
            waitFor("Message details")
            compose.onNodeWithText(entrantMessage.body).performScrollTo().assertIsDisplayed()
            assertEquals(draft.id, reservation.lotteryId)
            assertEquals("demo-admin", session(scenario).scenario.accountId)
        }
    }

    /** Opens an actual Material action, scrolling its current content when needed.
     * @param text Exact existing label.
     */
    private fun open(text: String) {
        waitFor(text)
        val node = compose.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    /** Waits for committed application state rather than sleeping between feature handoffs.
     * @param text Expected exact content or action.
     */
    private fun waitFor(text: String) {
        compose.waitUntil(15000) {
            runCatching {
                compose.onNodeWithText(text).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
    }

    /** Uses the actual demo person switch while preserving the shared document store. The switch finishes in a
     * ViewModel coroutine that Compose idling doesn't track, so this waits until the host exposes that person.
     * @param scenario This test's owned launcher Activity.
     * @param label Existing person menu label.
     * @param target Demo person the label selects.
     */
    private fun person(
        scenario: ActivityScenario<OfflineDemoActivity>,
        label: String,
        target: DemoScenario,
    ) {
        open("Demo tools")
        open(label)
        compose.waitUntil(15000) { runCatching { session(scenario).scenario == target }.getOrDefault(false) }
    }

    /** Resets the demo and waits for the rebuilt session, since the old screen still shows the same labels.
     * @param scenario This test's owned launcher Activity.
     */
    private fun reset(scenario: ActivityScenario<OfflineDemoActivity>) {
        compose.waitUntil(15000) { runCatching { session(scenario) }.isSuccess }
        val before = session(scenario)
        open("Demo tools")
        open("Reset scenario")
        open("Reset scenario")
        compose.waitUntil(15000) { runCatching { session(scenario) !== before }.getOrDefault(false) }
    }

    /** Selects one May schedule date and the dialog's explicit default wall time.
     * @param label Existing unique schedule-point prefix.
     * @param dayOfMonth May 2027 day for this controlled-clock journey.
     */
    private fun schedule(
        label: String,
        dayOfMonth: Int,
    ) {
        open("$label date")
        compose.onNodeWithText("May $dayOfMonth, 2027", substring = true).performClick()
        open("Confirm")
        open("$label time")
        open("Confirm")
    }

    /** Reads the active real composition to inspect outcomes and control the single shared clock.
     * @param scenario This test's owned launcher Activity.
     */
    private fun session(scenario: ActivityScenario<OfflineDemoActivity>): DemoSession {
        lateinit var result: DemoSession
        scenario.onActivity {
            result =
                checkNotNull(
                    ViewModelProvider(it)[DemoHostViewModel::class.java].session.value,
                )
        }
        return result
    }
}
