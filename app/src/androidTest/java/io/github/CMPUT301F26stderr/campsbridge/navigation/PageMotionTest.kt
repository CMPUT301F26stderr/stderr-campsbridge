/*
 * Verifies the shared page and form step motion contracts.
 */

package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Verifies that page and form step changes use one horizontal slide. */
class PageMotionTest {
    // The isolated runner turns off system animations, but Compose motion remains under test.
    @get:Rule
    val compose =
        createAndroidComposeRule<androidx.activity.ComponentActivity>(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = 1f
                },
        )

    /** Forward and Back slides move in the layout direction and settle without resizing pages. */
    @Test
    fun pagePushAndBackSlideInBothLayoutDirections() {
        compose.mainClock.autoAdvance = false
        val layoutDirection = mutableStateOf(LayoutDirection.Ltr)
        lateinit var controller: NavHostController
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection.value) {
                controller = rememberNavController()
                AppNavHost(controller, "home", Modifier.fillMaxSize()) {
                    composable("home") {
                        MotionPage("home", Color.Red, "Push") {
                            controller.navigate("detail")
                        }
                    }
                    composable("detail") {
                        MotionPage("detail", Color.Blue, "Unused") {}
                    }
                }
            }
        }
        compose.onNodeWithTag("page-home").assertIsDisplayed()
        assertPageFillsHost("page-home")

        compose.onNodeWithText("Push").performClick()
        compose.mainClock.advanceTimeBy(120)
        assertPageMidSlide("page-detail", LayoutDirection.Ltr)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertPageAtOrigin("page-detail")

        Espresso.pressBack()
        compose.mainClock.advanceTimeBy(120)
        assertPageMidSlide("page-home", LayoutDirection.Ltr, enteringFromEnd = true)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertPageAtOrigin("page-home")

        compose.runOnIdle { layoutDirection.value = LayoutDirection.Rtl }
        compose.onNodeWithText("Push").performClick()
        compose.mainClock.advanceTimeBy(120)
        assertPageMidSlide("page-detail", LayoutDirection.Rtl)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        Espresso.pressBack()
        compose.mainClock.advanceTimeBy(120)
        assertPageMidSlide("page-home", LayoutDirection.Rtl, enteringFromEnd = true)
    }

    /** A primary switch is immediate, while a detail opened from it still slides. */
    @Test
    fun markedPrimarySwitchIsImmediateAndLaterDetailSlides() {
        compose.mainClock.autoAdvance = false
        lateinit var controller: NavHostController
        val immediate = mutableStateOf<Pair<String, String>?>(null)
        compose.setContent {
            controller = rememberNavController()
            Column(Modifier.fillMaxSize()) {
                AppNavHost(
                    controller,
                    "home",
                    Modifier.weight(1f),
                    immediateTransition = immediate,
                ) {
                    composable("home") {
                        MotionPage("home", Color.Red, "Switch") {
                            val source = requireNotNull(controller.currentBackStackEntry).id
                            controller.navigate("tab")
                            immediate.value = source to requireNotNull(controller.currentBackStackEntry).id
                        }
                    }
                    composable("tab") {
                        MotionPage("tab", Color.Green, "Open detail") {
                            controller.navigate("tab/detail")
                        }
                    }
                    composable("tab/detail") {
                        MotionPage("tab-detail", Color.Blue, "Unused") {}
                    }
                }
            }
        }
        compose.onNodeWithText("Switch").performClick()
        // Allow the host's entry change and transition target to reach the layout.
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        compose.onNodeWithTag("page-tab").assertIsDisplayed()
        assertPageAtOrigin("page-tab")
        val pixels = compose.onNodeWithTag("page-tab").captureToImage().toPixelMap()
        assertEquals(Color.Green, pixels[pixels.width / 2, pixels.height / 2])

        compose.onNodeWithText("Open detail").performClick()
        compose.mainClock.advanceTimeBy(120)
        assertPageMidSlide("page-tab-detail", LayoutDirection.Ltr)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        Espresso.pressBack()
        compose.mainClock.advanceTimeBy(120)
        assertPageMidSlide("page-tab", LayoutDirection.Ltr, enteringFromEnd = true)
    }

    /** Step changes slide and retain the previous snapshot while same-step edits stay in place. */
    @Test
    fun formStepSlidesAndKeepsSameStepUpdatesStill() {
        compose.mainClock.autoAdvance = false
        val form = mutableStateOf(FormState(step = 0, text = "first draft"))
        compose.setContent {
            StepTransition(form.value, { it.step }, Modifier.fillMaxSize()) { snapshot ->
                var localDraft by remember(snapshot.step) { mutableStateOf("initial local") }
                Box(
                    Modifier.fillMaxSize().background(Color.White).testTag("form-step-${snapshot.step}"),
                ) {
                    Text(snapshot.text)
                    Text(localDraft)
                    if (snapshot.step == 0) {
                        Column {
                            Button(onClick = { localDraft = "updated local" }) {
                                Text("Edit locally")
                            }
                            Button(onClick = { form.value = form.value.copy(step = 1, text = "second draft") }) {
                                Text("Advance")
                            }
                        }
                    } else {
                        Button(onClick = { form.value = form.value.copy(step = 0, text = "returned draft") }) {
                            Text("Back a step")
                        }
                    }
                }
            }
        }
        assertPageAtOrigin("form-step-0")
        val initialBounds = compose.onNodeWithTag("form-step-0").getUnclippedBoundsInRoot()
        compose.onNodeWithText("Edit locally").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { form.value = form.value.copy(text = "edited draft") }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("edited draft").assertIsDisplayed()
        compose.onNodeWithText("updated local").assertIsDisplayed()
        val editedBounds = compose.onNodeWithTag("form-step-0").getUnclippedBoundsInRoot()
        assertTrue("A same-step edit should not move the page", initialBounds == editedBounds)

        compose.onNodeWithText("Advance").performClick()
        compose.mainClock.advanceTimeBy(120)
        compose.onAllNodesWithText("edited draft").assertCountEquals(1)
        compose.onAllNodesWithText("second draft").assertCountEquals(1)
        assertPageMidSlide("form-step-1", LayoutDirection.Ltr)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        compose.onNodeWithText("second draft").assertIsDisplayed()

        compose.onNodeWithText("Back a step").performClick()
        compose.mainClock.advanceTimeBy(120)
        compose.onAllNodesWithText("second draft").assertCountEquals(1)
        compose.onNodeWithText("returned draft").assertExists()
        assertPageMidSlide("form-step-0", LayoutDirection.Ltr, enteringFromEnd = true)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        compose.onNodeWithText("returned draft").assertIsDisplayed()
    }

    /** Checks that a page occupies the same complete host bounds during navigation.
     * @param tag Semantic page identity.
     */
    private fun assertPageFillsHost(tag: String) {
        val hostBounds = compose.onRoot().getUnclippedBoundsInRoot()
        val hostWidth = hostBounds.right - hostBounds.left
        val page = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue(
            "Page width ${page.right - page.left} should match host width $hostWidth",
            page.right - page.left == hostWidth,
        )
    }

    /** Checks that a completed page rests at the host's leading edge.
     * @param tag Semantic page identity.
     */
    private fun assertPageAtOrigin(tag: String) {
        val page = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue("Expected $tag at origin, found ${page.left}", page.left.value == 0f)
    }

    /** Checks that a page is partway through a direction-aware horizontal slide.
     * @param tag Semantic page identity.
     * @param direction Page layout direction.
     * @param enteringFromEnd Whether the transition enters from the trailing edge.
     */
    private fun assertPageMidSlide(
        tag: String,
        direction: LayoutDirection,
        enteringFromEnd: Boolean = false,
    ) {
        val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val hostBounds = compose.onRoot().getUnclippedBoundsInRoot()
        val width = hostBounds.right - hostBounds.left
        val shouldBePositive = (direction == LayoutDirection.Ltr) xor enteringFromEnd
        val inExpectedHalf =
            if (shouldBePositive) {
                bounds.left.value > 0f && bounds.left < width
            } else {
                bounds.left.value < 0f && bounds.left > -width
            }
        assertTrue("Expected $tag partway in its slide, found ${bounds.left} of $width", inExpectedHalf)
    }

    /** Renders one opaque full-screen test page with a navigation action.
     * @param tag Route identity used by the assertions.
     * @param color Distinct page surface color.
     * @param actionLabel Visible navigation action.
     * @param onAction Test navigation event.
     */
    @Composable
    private fun MotionPage(
        tag: String,
        color: Color,
        actionLabel: String,
        onAction: () -> Unit,
    ) {
        Box(Modifier.fillMaxSize().background(color).testTag("page-$tag")) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }

    /** Keeps the step identity separate from editable draft content.
     * @param step Current form position.
     * @param text Editable content associated with the position.
     */
    private data class FormState(
        val step: Int,
        val text: String,
    )
}
