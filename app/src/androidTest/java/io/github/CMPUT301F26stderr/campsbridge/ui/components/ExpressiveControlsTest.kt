package io.github.CMPUT301F26stderr.campsbridge.ui.components

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigation
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.components.DiscoveryToolbar
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Preserves exclusive view choices and reachable navigation through the component port. */
class ExpressiveControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** Matches the application's edge-to-edge and keyboard configuration. */
    @Before fun configureWindow() {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    /** View choices expose one radio selection and cannot clear the active presentation. */
    @Test fun listAndMapStayExclusive() {
        val map = mutableStateOf(false)
        compose.setContent {
            CampsbridgeTheme {
                DiscoveryToolbar("", {}, map.value, { map.value = it }, {})
            }
        }
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        compose
            .onNodeWithText("List")
            .assert(radio)
            .assertIsSelected()
            .performClick()
            .assertIsSelected()
        compose
            .onNodeWithText("Map")
            .assert(radio)
            .assertIsNotSelected()
            .performClick()
            .assertIsSelected()
        compose
            .onNodeWithText("List")
            .assertIsNotSelected()
            .performClick()
            .assertIsSelected()
        compose.onNodeWithText("Map").assertIsNotSelected()
    }

    /** Selecting a view reports its identity without inventing an applied change. */
    @Test fun viewSelectionRemainsCallerOwned() {
        val requested = mutableListOf<Boolean>()
        compose.setContent {
            CampsbridgeTheme { DiscoveryToolbar("", {}, false, { requested += it }, {}) }
        }
        compose.onNodeWithText("Map").performClick()
        compose.onNodeWithText("List").assertIsSelected()
        compose.onNodeWithText("Map").assertIsNotSelected()
        compose.runOnIdle { assertEquals(listOf(true), requested) }
    }

    /** Three destinations leave the last content action reachable above their reserved space. */
    @Test fun threeDestinationsKeepContentAndBadgesReachable() {
        checkNavigation(listOf("Explore", "Inbox", "Profile"), 1f)
    }

    /** Narrow, enlarged text keeps five destination targets and the page's final action usable. */
    @Test fun fiveDestinationsRemainReachableWithEnlargedText() {
        checkNavigation(listOf("Explore", "Lotteries", "Reservations", "Inbox", "Profile"), 2f)
    }

    /** Exercises the real navigation component inside a scaffold with scrollable content.
     * @param labels Actual destination labels, including the longer reservation label.
     * @param fontScale Android text multiplier.
     */
    private fun checkNavigation(
        labels: List<String>,
        fontScale: Float,
    ) {
        val selected = mutableStateOf(labels.first())
        var saves = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                CampsbridgeTheme {
                    Box(Modifier.width(360.dp)) {
                        Scaffold(bottomBar = {
                            WorkspaceNavigation(
                                labels.map {
                                    WorkspaceNavigationItem(
                                        it,
                                        it,
                                        Icons.Default.Home,
                                        if (it ==
                                            "Inbox"
                                        ) {
                                            "2"
                                        } else {
                                            null
                                        },
                                    )
                                },
                                selected.value,
                                { selected.value = it },
                            )
                        }) { padding ->
                            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                                Spacer(Modifier.height(900.dp))
                                Button({ saves++ }, Modifier.testTag("last-action")) { Text("Save changes") }
                            }
                        }
                    }
                }
            }
        }
        compose
            .onNodeWithTag("last-action")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        for (label in labels) {
            val item = compose.onNodeWithText(label)
            item.assertIsDisplayed().performClick().assertIsSelected()
            val bounds = item.fetchSemanticsNode().boundsInRoot
            val minimum = 48f * compose.density.density
            assertTrue("$label keeps its touch height", bounds.height >= minimum)
            assertTrue("$label keeps its touch width", bounds.width >= minimum)
        }
        compose.onNodeWithText("2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("last-action").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, saves) }
    }
}
