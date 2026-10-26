package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Exercises the production owner graph with controlled saved records and real Back/tab wiring. */
class CampsiteNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val draft =
        sampleCampsite("draft % 1", published = false).let {
            it.copy(details = it.details.copy(name = "Forest clearing"))
        }

    /** Cancels root and navigation owners after each interaction. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** A draft opens by its encoded identity and Back retains the originating inventory tab. */
    @Test fun openSavedDraftAndReturnToItsTab() {
        start()
        compose.onNodeWithText("River Camp").assertIsDisplayed()
        compose.onNodeWithText("Drafts").performClick()
        compose.onNodeWithText("Forest clearing").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.onNodeWithText("draft % 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Campsites").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Drafts").assertIsSelected()
        compose.onNodeWithText("Forest clearing").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.campsiteListCreations)
            assertEquals(1, host.campsiteDetailCreations)
        }
    }

    /** Peer switching and account refresh retain selection instead of resetting the default. */
    @Test fun primarySwitchAndAccountRefreshRetainContext() {
        start()
        compose.onNodeWithText("Drafts").performClick()
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        compose.runOnIdle {
            host.accounts.account.value =
                sampleAccount(setOf(Role.OWNER)).copy(name = "Taylor")
        }
        compose.onNodeWithText("Taylor").assertIsDisplayed()
        compose.onNodeWithText("Campsites").performClick()
        compose.onNodeWithText("Drafts").assertIsSelected()
        compose.onNodeWithText("Forest clearing").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.campsiteListCreations)
            assertEquals(1, host.profileCreations)
        }
    }

    /** Source failure remains distinct from the empty selected category and retries in place. */
    @Test fun sourceFailureRetryRetainsDraftCategory() {
        host.campsites.streamFailure.value = IOException("offline")
        start()
        compose.onNodeWithText("Drafts").performClick()
        compose.onNodeWithText("Could not load your campsites.").assertIsDisplayed()
        compose.runOnIdle {
            host.campsites.records.value = emptyList()
            host.campsites.streamFailure.value = null
        }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("No saved drafts yet.").assertIsDisplayed()
        compose.onNodeWithText("Drafts").assertIsSelected()
    }

    /** Removal updates the same saved detail instead of substituting another fixture. */
    @Test fun removedListingKeepsActualCallerBack() {
        start()
        compose.onNodeWithText("River Camp").performClick()
        compose.runOnIdle {
            host.campsites.records.value =
                host.campsites.records.value.map {
                    it.copy(removal = Removal(Instant.EPOCH, "admin"))
                }
        }
        compose.onNodeWithText("Campsite unavailable").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("No published campsites yet.").assertIsDisplayed()
    }

    /** The confirmed owner inventory uses real rows and explicitly pending F11 ordering. */
    @Test fun unavailableOrderingNeverHidesTheSavedRows() {
        start()
        compose
            .onNodeWithText(
                "Alphabetical ordering is pending in this build.",
            ).assertIsDisplayed()
        compose.onNodeWithText("River Camp").assertIsDisplayed()
        compose.onNodeWithText("CAD 65.00 / night").assertIsDisplayed()
    }

    /** Leaving Ready releases inventory readers rather than retaining a removed account's data. */
    @Test fun removedAccountReleasesItsGraphReads() {
        start()
        compose.onNodeWithText("River Camp").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.campsites.activeObservers)
            host.accounts.account.value =
                sampleAccount(setOf(Role.OWNER)).copy(
                    removal = Removal(Instant.EPOCH, "admin"),
                )
        }
        compose.onNodeWithText("Account unavailable").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, host.campsites.activeObservers) }
    }

    /** The actual route reports visible rows and releases their readers on peer navigation. */
    @Test fun routeTracksVisibleThumbnailsAndDisposesThem() {
        start()
        compose.onNodeWithText("River Camp").assertIsDisplayed()
        compose.waitUntil(5_000) { host.images.activeObservers == 1 }
        compose.runOnIdle {
            assertEquals(
                ImageTargetType.CAMPSITE,
                host.images.observedTargets
                    .single()
                    .type,
            )
            assertEquals(
                "site-1",
                host.images.observedTargets
                    .single()
                    .id,
            )
        }
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.images.activeObservers)
            assertEquals(
                ImageTargetType.ACCOUNT,
                host.images.observedTargets
                    .last()
                    .type,
            )
        }
    }

    /** A transient root read error preserves the saved route, its owner and originating tab. */
    @Test fun rootReadRetryRestoresTheSameSavedDetail() {
        start()
        compose.onNodeWithText("Drafts").performClick()
        compose.onNodeWithText("Forest clearing").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle { host.accounts.streamFailure.value = IOException("profile offline") }
        compose.onNodeWithText("Could not start Campsbridge").assertIsDisplayed()
        compose.runOnIdle { host.accounts.streamFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.campsiteDetailCreations)
            assertEquals(1, host.campsiteListCreations)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Drafts").assertIsSelected()
        compose.onNodeWithText("Forest clearing").assertIsDisplayed()
    }

    /** Establishes the real Ready owner branch with saved fixtures and no writes. */
    private fun start() {
        host.accounts.account.value = sampleAccount(setOf(Role.OWNER))
        host.campsites.records.value = listOf(sampleCampsite(), draft)
        compose.setContent { host.Content() }
    }
}
