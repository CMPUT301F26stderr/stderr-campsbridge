package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Restored availability intent must remain protected before the saved parent can be read. */
class AvailabilityRestorationTest {
    @get:Rule val compose = createComposeRule()
    private val site = sampleCampsite()
    private val sites = FakeCampsiteRepository(listOf(site))
    private val store = ViewModelStore()
    private var exits = 0

    /** Cancels the held parent read and any range created by a retry. */
    @After fun close() {
        compose.runOnIdle { store.clear() }
    }

    /** App-bar Back confirms abandonment while a restored dirty parent is still loading. */
    @Test fun heldParentReadShowsDiscardForRestoredRequests() {
        sites.readGate = CompletableDeferred()
        start(
            SavedStateHandle(
                mapOf(
                    "availability.requested.dates" to arrayListOf("2027-05-14"),
                    "availability.requested.offered" to booleanArrayOf(false),
                ),
            ),
        )
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard unsaved date changes?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, exits) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard unsaved date changes?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, sites.writeCalls) }
    }

    /** Hardware Back after a failed restore keeps local selection until explicit discard. */
    @Test fun failedParentReadConfirmsDiscardOfRestoredSelection() {
        sites.readFailure = IOException("offline")
        start(SavedStateHandle(mapOf("availability.selection" to arrayListOf("2027-05-14"))))
        compose.onNodeWithText("Could not load this campsite.").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Discard unsaved date changes?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, exits) }
        compose.onNodeWithText("Discard changes").performClick()
        compose.waitUntil(5_000) { exits == 1 }
        compose.runOnIdle { assertEquals(0, sites.writeCalls) }
    }

    /** Hosts the actual restored route without a navigation-only demo substitute.
     * @param saved Primitive selection/request facts from the previous editor entry.
     */
    private fun start(saved: SavedStateHandle) {
        val model =
            AvailabilityViewModel(
                site.ownerId,
                site.id,
                sites,
                Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC),
                saved,
            )
        store.put("availability", model)
        compose.setContent { CampsbridgeTheme { AvailabilityRoute(model) { exits++ } } }
    }
}
