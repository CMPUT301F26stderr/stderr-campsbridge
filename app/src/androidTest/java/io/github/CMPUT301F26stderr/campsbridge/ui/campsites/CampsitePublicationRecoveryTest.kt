package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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

/** Actual restored route cannot abandon a listing whose publication is still unknown. */
class CampsitePublicationRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val site = sampleCampsite(published = false)
    private val sites = FakeCampsiteRepository(listOf(site))
    private val store = ViewModelStore()
    private var exits = 0
    private var saved: String? = null

    /** Cancels graph-owned lookup and reader work. */
    @After fun close() {
        compose.runOnIdle { store.clear() }
    }

    /** A held restored lookup shows recovery feedback and blocks toolbar navigation. */
    @Test fun heldRestoredPublicationReadBlocksAbandonment() {
        sites.readGate = CompletableDeferred()
        start()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled().performClick()
        compose
            .onNodeWithText(
                "Publication is not confirmed. Retry before editing or leaving.",
            ).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, exits) }
    }

    /** A failed initial read exposes read retry, then navigates only on the actual server identity. */
    @Test fun failedRestoredPublicationCanRetryTheServerRead() {
        sites.readFailure = IOException("server unavailable")
        start()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, exits)
            assertEquals(0, sites.publishRequests.size)
            sites.readFailure = null
            sites.records.value = listOf(site.copy(publishedAt = site.createdAt))
        }
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(5_000) { saved == site.id }
        compose.runOnIdle {
            assertEquals(site.id, saved)
            assertEquals(0, sites.publishRequests.size)
        }
    }

    /** Creates the real route with primitive unknown-publication state and controlled IO. */
    private fun start() {
        val model =
            CampsiteSetupViewModel(
                site.ownerId,
                site.id,
                sites,
                FakeImageRepository(),
                Clock.systemUTC(),
                SavedStateHandle(
                    mapOf(
                        "campsiteSetup.publishing" to true,
                        "campsiteSetup.action" to CampsiteSaveAction.PUBLISH.name,
                        "campsiteSetup.attempt" to true,
                    ),
                ),
            )
        store.put("setup", model)
        compose.setContent {
            CampsbridgeTheme {
                CampsiteSetupRoute(model, { exits++ }, { saved = it }, {})
            }
        }
    }
}
