package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.components.DiscoveryToolbar
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/** Actual discovery/filter/detail routes with fixed reserved-helper outcomes. */
class DiscoveryFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val a =
        sampleCampsite("river/one").let {
            it.copy(details = it.details.copy(name = "River camp", locationLabel = "Jasper"))
        }
    private val b =
        sampleCampsite("forest").let {
            it.copy(details = it.details.copy(name = "Forest camp", locationLabel = "Banff"))
        }

    /** Releases every graph-owned reader and pending operation. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Typing retains submitted results; Search opens the actual saved ID and returns to its query. */
    @Test fun submittedSearchAndSavedDetailUseTheActualGraph() {
        start()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Forest camp"))
        compose.onNodeWithText("Search campsites").performTextReplacement("river")
        compose.onNodeWithText("Forest camp").assertIsDisplayed()
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Forest camp").assertDoesNotExist()
        compose.onNodeWithText("River camp").performClick()
        compose.onNodeWithText("river/one").assertIsDisplayed()
        compose.onNode(hasText("Explore") and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithText("Join lottery").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("river").assertIsDisplayed()
        compose.onNode(hasText("Explore") and hasClickAction()).assertIsSelected()
        compose.runOnIdle {
            assertEquals(1, host.exploreCreations)
            assertEquals(listOf("river/one"), host.campsites.observedIds)
        }
    }

    /** Apply commits working criteria; hardware Back and Reset/Cancel retain the applied filter. */
    @Test fun filtersApplyAndBackHaveDistinctEffects() {
        start()
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Location").performTextReplacement("Jasper")
        compose.onNodeWithContentDescription("Increase Guests").performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.onNodeWithText("Location: Jasper").assertIsDisplayed()
        compose.onNodeWithText("Guests: 2").assertIsDisplayed()
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Location").performTextReplacement("Banff")
        closeSoftKeyboard()
        pressBack()
        compose.onNodeWithText("Location: Jasper").assertIsDisplayed()
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Location: Jasper").assertIsDisplayed()
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.onNodeWithText("Location: Jasper").assertDoesNotExist()
        compose.onNodeWithText("Guests: 2").assertDoesNotExist()
        compose.onNode(hasText("Explore") and hasClickAction()).assertIsSelected()
        compose.runOnIdle { assertEquals(1, host.exploreCreations) }
    }

    /** The native picker opens in the injected month and applies its actual exclusive checkout. */
    @Test fun nativeStayPickerAppliesToTheWorkingFilter() {
        start()
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Dates").performClick()
        compose.onNodeWithText("May 14, 2027", substring = true).performClick()
        compose.onNodeWithText("May 16, 2027", substring = true).performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("May 14, 2027 to May 16, 2027").assertIsDisplayed()
        compose.onNodeWithText("Apply filters").performClick()
        compose.onNodeWithText("No campsites match these criteria.").assertIsDisplayed()
        compose.runOnIdle {
            val from = LocalDate.of(2027, 5, 14)
            val until = LocalDate.of(2027, 5, 16)
            assertEquals(
                setOf(Triple(a.id, from, until), Triple(b.id, from, until)),
                host.campsites.observedCalendars.toSet(),
            )
            assertEquals(2, host.campsites.activeCalendarObservers)
        }
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Clear dates").performClick()
        compose.onNodeWithText("May 14, 2027 to May 16, 2027").assertDoesNotExist()
        compose.onNodeWithText("Apply filters").performClick()
        compose.runOnIdle { assertEquals(0, host.campsites.activeCalendarObservers) }
    }

    /** Returning from an ID-based reader retains the actual lazy-list position and primary selection. */
    @Test fun savedDetailBackRetainsScrollPosition() {
        host.campsites.records.value =
            (1..12).map { index ->
                sampleCampsite(
                    "camp-$index",
                ).let { it.copy(details = it.details.copy(name = "Camp $index")) }
            }
        host.matchesText = { _, _ -> true }
        host.matchesCapacity = { _, _ -> true }
        compose.setContent { host.Content() }
        compose.onNode(hasText("Explore") and hasClickAction()).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Camp 10"))
        compose.onNodeWithText("Camp 10").performClick()
        compose.onNodeWithText("camp-10").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Camp 10").assertIsDisplayed()
        compose.onNode(hasText("Explore") and hasClickAction()).assertIsSelected()
        compose.runOnIdle { assertEquals(1, host.exploreCreations) }
    }

    /** A transient root read failure restores the same working editor and parent owner. */
    @Test fun rootRetryRetainsTheFilterChild() {
        start()
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Location").performTextReplacement("Banff")
        compose.runOnIdle { host.accounts.streamFailure.value = IOException("offline") }
        compose.onNodeWithText("Could not start Campsbridge").assertIsDisplayed()
        compose.runOnIdle { host.accounts.streamFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Banff").assertIsDisplayed()
        compose.onNodeWithText("Apply filters").performClick()
        compose.onNodeWithText("Location: Banff").assertIsDisplayed()
        compose.onNodeWithText("Forest camp").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.exploreCreations) }
    }

    /** Optional image/rating failure keeps the listing and offers an independent read retry. */
    @Test fun optionalFailuresKeepTheListingReadable() {
        host.images.observationFailure = IOException("photo")
        host.reviews.failure(a.id).value = IOException("rating")
        start()
        compose.onNodeWithText("River camp").assertIsDisplayed()
        compose.onNodeWithText("Ratings unavailable").assertIsDisplayed()
        compose.runOnIdle {
            host.images.observationFailure = null
            host.reviews.failure(a.id).value = null
        }
        compose.onNodeWithText("Retry photo or ratings for River camp").performClick()
        compose
            .onAllNodesWithText(
                "No ratings",
            ).fetchSemanticsNodes()
            .also { check(it.isNotEmpty()) }
        compose.onNodeWithText("River camp").assertIsDisplayed()
    }

    /** Default pending dependencies retain usable filters without claiming matched results. */
    @Test fun pendingMatchingIsExplicitAndDoesNotBlockFilterEditing() {
        compose.setContent { host.Content() }
        compose.onNode(hasText("Explore") and hasClickAction()).performClick()
        compose
            .onNodeWithText(
                "Search and guest matching are pending implementation in this build.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Location").performTextReplacement("Jasper")
        compose.onNodeWithText("Apply filters").performClick()
        compose.onNodeWithText("Location: Jasper").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, host.campsites.activeCalendarObservers) }
    }

    /** Search button and IME each submit once, while omitted view controls cannot be invoked. */
    @Test fun toolbarHasExplicitSearchAndNoInactiveMapToggle() {
        var searches = 0
        compose.setContent {
            CampsbridgeTheme {
                DiscoveryToolbar(
                    "River",
                    {},
                    false,
                    {},
                    {},
                    onSearch = { searches++ },
                    showViewToggle = false,
                )
            }
        }
        compose.onNodeWithText("Map").assertDoesNotExist()
        compose.onNodeWithText("List").assertDoesNotExist()
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Search campsites").performImeAction()
        compose.runOnIdle { assertEquals(2, searches) }
    }

    /** Opens production discovery with known fixture decisions, never a copied matching algorithm. */
    private fun start() {
        host.campsites.records.value = listOf(a, b)
        val decisions =
            mapOf(
                (a.id to "") to true,
                (b.id to "") to true,
                (a.id to "river") to true,
                (b.id to "river") to false,
            )
        host.matchesText = { site, query -> checkNotNull(decisions[site.id to query]) }
        host.matchesCapacity = { _, _ -> true }
        compose.setContent { host.Content() }
        compose.onNode(hasText("Explore") and hasClickAction()).performClick()
    }
}
