/*
 * Follows the real launcher across separate people, preserving one shared local history.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.testsupport.onCalendarDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Follows the real launcher across separate people, preserving one shared local history. */
class ConnectedOfflineDemoTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** Draw, Inbox, booking, rejection, refund and admin history use the same saved records. */
    @Test fun continuousJourneyKeepsSavedResultsAcrossPeopleAndRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        ActivityScenario.launch<OfflineDemoActivity>(intent).use { scenario ->
            waitFor("Demo tools")
            compose.onNodeWithText("Demo tools").performClick()
            compose.onNodeWithText("Reset scenario").performClick()
            compose.onNodeWithText("Reset scenario").performClick()
            waitFor("Camping")
            compose.onNodeWithText("Show draw results").performClick()
            val winTag = "inbox-event-${DocumentKeys.compound("LOTTERY_WON", "demo-open", "demo-account")}"
            compose.waitUntil(15_000) {
                runCatching {
                    compose.onNodeWithTag(winTag).fetchSemanticsNode()
                    true
                }.getOrDefault(false)
            }
            compose.onNodeWithTag(winTag).performScrollTo().performClick()
            open("Book your stay")
            compose.onNodeWithTag("booking-calendar").performScrollTo()
            compose.onCalendarDate(java.time.LocalDate.parse("2027-05-18")).performScrollTo().performClick()
            compose.onCalendarDate(java.time.LocalDate.parse("2027-05-20")).performScrollTo().performClick()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Next").performClick()
            open("I have read and accept these rules and the reservation policy.")
            compose.onNodeWithText("Next").performClick()
            open("Confirm reservation")
            waitFor("Payment & refund")
            open("Payment & refund")
            compose
                .onNodeWithText(
                    "Simulated payment. No banking information is collected.",
                ).performScrollTo()
                .assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("View as host").performClick()
            waitFor("Reject reservation")
            open("Reject reservation")
            compose
                .onNode(
                    hasAnyAncestor(isDialog()) and
                        androidx.compose.ui.test
                            .hasText("Reject reservation"),
                ).performClick()
            waitFor("Cancelled by owner")
            scenario.recreate()
            waitFor("Cancelled by owner")
            compose.onNodeWithText("Camping").performClick()
            waitFor("Alex · Camper")
            waitFor("Cancelled by owner")
            open("Payment & refund")
            waitFor("Full simulated refund")
            compose.onNodeWithText("Full simulated refund").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Inspect history").performClick()
            waitFor("Filter notification events")
            assertTrue(FirebaseApp.getApps(context).isEmpty())
        }
    }

    /** Host edits and an explicit offline pin remain intact while changing areas mid-draft. */
    @Test fun hostingDraftAndOfflineLocationSurviveAreaChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        ActivityScenario.launch<OfflineDemoActivity>(intent).use { scenario ->
            waitFor("Hosting")
            compose.onNodeWithText("Hosting").performClick()
            waitFor("My campsites")
            compose.onNodeWithText("Drafts").performClick()
            open("Forest clearing")
            open("Continue setup")
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Next").performClick()
            open("Choose location")
            open("Jasper")
            compose.onNodeWithText("Use this location").performClick()
            compose.onNodeWithText("Camping").performClick()
            waitFor("Alex · Camper")
            compose.onNodeWithText("Hosting").performClick()
            waitFor("Jordan · Host")
            scenario.recreate()
            compose.onNodeWithText("Latitude 52.87340, longitude -118.08140", substring = true).assertIsDisplayed()
            assertTrue(FirebaseApp.getApps(context).isEmpty())
        }
    }

    /** Opens an existing scrollable action or row.
     * @param text Exact visible action.
     */
    private fun open(text: String) {
        val node = compose.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    /** Waits for asynchronous actor loading and committed repository updates.
     * @param text Expected visible content.
     */
    private fun waitFor(text: String) {
        compose.waitUntil(15_000) {
            runCatching {
                compose.onNodeWithText(text).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
    }
}
