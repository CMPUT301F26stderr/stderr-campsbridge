package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
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

/** Restored child routes must explain unresolved parent facts and allow return/retry. */
class CampsiteLocationRouteTest {
    @get:Rule val compose = createComposeRule()
    private val site = sampleCampsite(published = false)
    private val campsites = FakeCampsiteRepository(listOf(site))
    private val images = FakeImageRepository()
    private val store = ViewModelStore()
    private var returns = 0

    /** Releases readers and rejects accidental persistence from every child outcome. */
    @After fun close() {
        compose.runOnIdle {
            store.clear()
            assertEquals(0, campsites.writeCalls)
            assertEquals(0, images.replaceCalls)
        }
    }

    /** A held authoritative reread leaves visible loading/title/Back rather than a blank child. */
    @Test fun restoredChildShowsHeldReadAndBack() {
        campsites.readGate = CompletableDeferred()
        start()
        compose.onNodeWithText("Choose campsite location").assertIsDisplayed()
        compose.onNodeWithText("Loading").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, returns) }
    }

    /** Failed parent read retries in place and reveals the actual loaded coordinate afterward. */
    @Test fun restoredChildFailureCanRetryWithoutAnotherModel() {
        campsites.readFailure = IOException("offline")
        start()
        compose.onNodeWithText("Could not load this campsite.").assertIsDisplayed()
        compose.runOnIdle { campsites.readFailure = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Choose campsite location").assertIsDisplayed()
        compose.onNodeWithText("Use this location").assertIsDisplayed()
        compose.onNodeWithText("Use this location").performClick()
        compose.runOnIdle {
            assertEquals(listOf(site.id, site.id), campsites.readIds)
            assertEquals(1, returns)
        }
    }

    /** A confirmed absent target stays unavailable and cannot fabricate another listing/pin. */
    @Test fun restoredMissingTargetShowsUnavailableAndBack() {
        campsites.records.value = emptyList()
        start()
        compose.onNodeWithText("Campsite unavailable").assertIsDisplayed()
        compose.onNodeWithText("Use this location").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, returns) }
    }

    /** Constructs the same saved-ID model a restored parent entry owns before its reread completes. */
    private fun start() {
        lateinit var model: CampsiteSetupViewModel
        compose.runOnIdle {
            model =
                CampsiteSetupViewModel(
                    site.ownerId,
                    site.id,
                    campsites,
                    images,
                    Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC),
                    SavedStateHandle(),
                )
            store.put("restoredSetup", model)
        }
        compose.setContent {
            CampsbridgeTheme { CampsiteLocationRoute(model) { returns++ } }
        }
    }
}
