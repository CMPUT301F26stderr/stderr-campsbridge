package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.navigation.AppDestination
import io.github.CMPUT301F26stderr.campsbridge.navigation.campsiteReadGraph
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Exercises real saved-reader children, caller contexts and scoped read failures. */
class CampsiteDetailFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val site = sampleCampsite("river/one")
    private val inspectionStore = ViewModelStore()

    /** Cancels every actual graph-owned reader after the case. */
    @After fun close() {
        compose.runOnIdle {
            host.close()
            inspectionStore.clear()
        }
    }

    /**
     * Entrant startup opens Explore; each section and policy returns within the same saved
     * parent.
     */
    @Test fun sectionsAndPoliciesShareOneSavedReader() {
        start()
        compose.onNode(hasText("Explore") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText(site.details.name).performClick()
        open("About")
        compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        compose.onNodeWithText(site.details.description).assertIsDisplayed()
        compose.onNodeWithText("Potable water").performScrollTo().assertIsDisplayed()
        pressBack()
        open("Map & location")
        compose.onNodeWithText(site.details.locationLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        open("Availability")
        compose.onNodeWithText("May 2027").assertIsDisplayed()
        compose.onNodeWithTag("calendar-date-2027-05-14").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText("June 2027").assertIsDisplayed()
        pressBack()
        compose.runOnIdle { assertEquals(0, host.campsites.activeCalendarObservers) }
        open("Availability")
        compose.onNodeWithText("June 2027").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        open("Campsite rules")
        compose.onNodeWithText(site.details.rules).assertIsDisplayed()
        open("Reservation & refund policy")
        compose.onNodeWithText("Simulated payments and refunds").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText(site.details.rules).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Join lottery").assertDoesNotExist()
        compose.onNodeWithText("Reviews").assertDoesNotExist()
        pressBack()
        compose.onNode(hasText("Explore") and hasClickAction()).assertIsSelected()
        compose.runOnIdle {
            assertEquals(1, host.campsiteDetailCreations)
            assertEquals(listOf(site.id), host.campsites.observedIds)
            assertEquals(0, host.campsites.writeCalls + host.accounts.writes)
        }
    }

    /** Optional read failures keep listing/section navigation usable and retry their own source. */
    @Test fun supplementalFailuresRemainIndependent() {
        host.reviews.failure(site.id).value = IOException("ratings offline")
        host.campsiteOwners.streamFailure.value = IOException("owner offline")
        start()
        compose.onNodeWithText(site.details.name).performClick()
        compose.onNodeWithText("Ratings unavailable").assertIsDisplayed()
        compose.runOnIdle { host.reviews.failure(site.id).value = null }
        compose.onNodeWithText("Retry ratings").performScrollTo().performClick()
        compose.onNodeWithText("No ratings").assertIsDisplayed()
        open("About")
        compose.onNodeWithText("Could not load the campsite host.").assertIsDisplayed()
        compose.onNodeWithText(site.details.description).assertIsDisplayed()
        compose.runOnIdle { host.campsiteOwners.streamFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        pressBack()
        host.campsites.calendarFailure.value = IOException("calendar offline")
        open("Availability")
        compose.onNodeWithText("Could not load this month.").assertIsDisplayed()
        compose.runOnIdle { host.campsites.calendarFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("May 2027").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.campsiteDetailCreations)
            assertEquals(0, host.campsites.writeCalls)
        }
    }

    /** A disappearing parent keeps Back usable and never renders stale child facts as current. */
    @Test fun missingParentDoesNotLeaveStaleSectionContent() {
        start()
        compose.onNodeWithText(site.details.name).performClick()
        open("About")
        compose.runOnIdle { host.campsites.records.value = emptyList() }
        compose.onNodeWithText("Campsite unavailable").assertIsDisplayed()
        compose.onNodeWithText(site.details.description).assertDoesNotExist()
        compose.runOnIdle { host.campsites.records.value = listOf(site) }
        compose.onNodeWithText(site.details.description).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, host.campsiteDetailCreations) }
    }

    /**
     * Owner preview is a separate reader, supports drafts and returns to the original
     * management/list.
     */
    @Test fun ownerPreviewNeverExposesEntrantOrEditorActions() {
        host.accounts.account.value = sampleAccount(setOf(Role.ENTRANT, Role.OWNER))
        host.preferences.progress.value =
            host.preferences.progress.value
                .copy(workspace = Role.OWNER)
        host.campsites.records.value = listOf(site.copy(publishedAt = null))
        compose.setContent { host.Content() }
        compose.onNodeWithText("Drafts").performClick()
        compose.onNodeWithText(site.details.name).performClick()
        open("Preview listing")
        open("Availability")
        compose.onNodeWithText("Offer nights").assertDoesNotExist()
        compose.onNodeWithText("Withdraw nights").assertDoesNotExist()
        compose.onNodeWithTag("calendar-date-2027-05-14").assertIsNotEnabled()
        pressBack()
        compose.onNodeWithText("Join lottery").assertDoesNotExist()
        compose.onNodeWithText("Continue setup").assertDoesNotExist()
        pressBack()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Drafts").assertIsSelected()
        compose.runOnIdle {
            assertEquals(2, host.campsiteDetailCreations)
            assertEquals(0, host.campsites.writeCalls)
            assertEquals(0, host.campsiteOwners.activeObservers)
            assertEquals(0, host.reviews.activeObservers)
        }
    }

    /** Admin inspection uses the registered shared graph for retained removed/draft records. */
    @Test fun adminInspectionRetainsHistoryWithoutMutationControls() {
        host.campsites.records.value =
            listOf(
                site.copy(
                    publishedAt = null,
                    removal = Removal(Instant.EPOCH, "admin"),
                ),
            )
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = inspectionStore
            }
        var creations = 0
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                CampsbridgeTheme {
                    val controller = rememberNavController()
                    NavHost(controller, "landing") {
                        composable("landing") {
                            Button({
                                controller.navigate(
                                    AppDestination.campsiteGraph(
                                        Role.ADMIN,
                                        site.id,
                                        CampsiteDetailContext.INSPECT,
                                    ),
                                )
                            }) { Text("Inspect record") }
                        }
                        campsiteReadGraph(
                            controller,
                            Role.ADMIN,
                            CampsiteDetailContext.INSPECT,
                            { id, context ->
                                viewModelFactory {
                                    initializer {
                                        creations++
                                        CampsiteDetailViewModel(
                                            "admin",
                                            id,
                                            context,
                                            host.campsites,
                                            host.images,
                                            host.campsiteOwners,
                                            host.reviews,
                                            createSavedStateHandle(),
                                            Clock.systemUTC(),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Inspect record").performClick()
        compose.onNodeWithText("Removed").assertIsDisplayed()
        open("About")
        compose.onNodeWithText(site.details.description).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Join lottery").assertDoesNotExist()
        compose.onNodeWithText("Remove campsite").assertDoesNotExist()
        compose.onNodeWithText("Continue setup").assertDoesNotExist()
        open("Availability")
        compose.onNodeWithText("Offer nights").assertDoesNotExist()
        compose.onNodeWithText("Withdraw nights").assertDoesNotExist()
        pressBack()
        pressBack()
        compose.onNodeWithText("Inspect record").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, creations)
            assertEquals(0, host.campsites.writeCalls + host.accounts.writes)
        }
    }

    /** Long descriptions and unknown stored options remain readable at enlarged system text. */
    @Test fun aboutRetainsUnknownOptionsAndLargeText() {
        val large =
            site.copy(
                details =
                    site.details.copy(
                        description = "A riverside campsite with quiet woodland paths. ".repeat(12),
                        amenities = setOf("water", "solar-oven"),
                        tags = setOf("custom-ridge"),
                    ),
            )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                CampsbridgeTheme {
                    CampsiteAboutScreen(
                        CampsiteDetailUiState.Content(large),
                        CampsiteOwnerState.Ready("Alex Morgan"),
                        {},
                        {},
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("custom-ridge").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("solar-oven").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    /**
     * Starts actual production navigation with deterministic predicate results, not another
     * matcher.
     */
    private fun start() {
        host.campsites.records.value = listOf(site)
        host.campsites.calendarNights.value = listOf(CalendarNight(LocalDate.of(2027, 5, 14), true))
        host.matchesText = { _, _ -> true }
        host.matchesCapacity = { _, _ -> true }
        compose.setContent { host.Content() }
    }

    /** Scrolls to an actual destination row before invoking its navigation action.
     * @param label Visible registered destination label.
     */
    private fun open(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
    }
}
