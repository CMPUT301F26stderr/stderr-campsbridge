package io.github.CMPUT301F26stderr.campsbridge.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepAction
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Contracts for short steps and reachable actions at large text sizes. */
class StepScaffoldTest {
    @get:Rule val compose = createComposeRule()

    /** The step supplies its caller's title/progress and explicit next action. */
    @Test fun shortStepShowsProgressAndDeliversNext() {
        var next = 0
        compose.setContent {
            CampsbridgeTheme {
                StepScaffold("Your details", "Step 1 of 3", {}, { next++ }) {
                    Text("Choose how you want to camp.")
                }
            }
        }
        compose.onNodeWithText("Your details").assertIsDisplayed()
        compose.onNodeWithText("Step 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.runOnIdle { assertEquals(1, next) }
    }

    /** Review has separate publish and draft callbacks instead of relabelling optional Skip. */
    @Test fun reviewDeliversDistinctPrimaryAndDraftActions() {
        var published = 0
        var saved = 0
        compose.setContent {
            CampsbridgeTheme {
                StepScaffold(
                    "Review campsite",
                    "Step 8 of 8",
                    {},
                    { published++ },
                    nextLabel = "Publish",
                    secondaryLabel = "Save draft",
                    onSecondaryAction = { saved++ },
                ) { Text("Jasper campsite") }
            }
        }
        compose.onNodeWithText("Save draft").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, saved)
            assertEquals(0, published)
        }
        compose.onNodeWithText("Publish").performClick()
        compose.runOnIdle { assertEquals(1, published) }
        compose.onNodeWithText("Skip for now").assertDoesNotExist()
    }

    /** A held submission blocks either save action and all explicit exit controls. */
    @Test fun submittingReviewBlocksBothActionsAndExits() {
        var actions = 0
        compose.setContent {
            CampsbridgeTheme {
                StepScaffold(
                    "Review campsite",
                    "Step 8 of 8",
                    { actions++ },
                    { actions++ },
                    isSubmitting = true,
                    nextLabel = "Publish",
                    onCancel = { actions++ },
                    secondaryLabel = "Save draft",
                    onSecondaryAction = { actions++ },
                ) { Text("Saving campsite") }
            }
        }
        compose.onNodeWithText("Publish").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Save draft").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Cancel").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Publish").assertIsDisplayed()
        compose.onNodeWithContentDescription("Save draft").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, actions) }
    }

    /** An idle recovery can block abandonment without disabling its explicit retry action. */
    @Test fun recoveryBlocksExitsButKeepsExplicitRetryAvailable() {
        var saved = 0
        var exits = 0
        compose.setContent {
            CampsbridgeTheme {
                StepScaffold(
                    "Review campsite",
                    "Step 8 of 8",
                    { exits++ },
                    {},
                    nextEnabled = false,
                    nextLabel = "Publish",
                    onCancel = { exits++ },
                    secondaryLabel = "Retry save",
                    onSecondaryAction = { saved++ },
                    exitEnabled = false,
                ) { Text("Confirm the photo result before leaving.") }
            }
        }
        compose.onNodeWithText("Cancel").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled().performClick()
        pressBack()
        compose.onNodeWithText("Retry save").performClick()
        compose.runOnIdle {
            assertEquals(1, saved)
            assertEquals(0, exits)
        }
    }

    /** Saving a draft shows that action's progress while publication remains disabled and idle. */
    @Test fun draftSubmissionShowsProgressOnTheDraftAction() {
        compose.setContent {
            CampsbridgeTheme {
                StepScaffold(
                    "Review campsite",
                    "Step 8 of 8",
                    {},
                    {},
                    isSubmitting = true,
                    nextLabel = "Publish",
                    secondaryLabel = "Save draft",
                    onSecondaryAction = {},
                    submittingAction = StepAction.SECONDARY,
                ) { Text("Jasper campsite") }
            }
        }
        compose.onNodeWithText("Publish").assertIsNotEnabled()
        compose.onNodeWithText("Save draft").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Save draft").assertIsDisplayed()
        compose.onNodeWithContentDescription("Publish").assertDoesNotExist()
    }

    /** Optional steps retain their existing Skip action when no review action is supplied. */
    @Test fun optionalStepStillDeliversSkip() {
        var skipped = 0
        compose.setContent {
            CampsbridgeTheme {
                StepScaffold("Photo", "Step 2 of 8", {}, {}, onSkip = { skipped++ }) {
                    Text("Choose an optional campsite photo.")
                }
            }
        }
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Save draft").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, skipped) }
    }

    /** A valid draft may be saved even when a separate publication prerequisite is unmet. */
    @Test fun disabledPrimaryCanStillSaveAValidDraft() {
        var saved = 0
        val draftEnabled = mutableStateOf(false)
        compose.setContent {
            CampsbridgeTheme {
                StepScaffold(
                    "Review lottery",
                    "Step 6 of 6",
                    {},
                    {},
                    nextEnabled = false,
                    nextLabel = "Publish",
                    secondaryLabel = "Save draft",
                    onSecondaryAction = { saved++ },
                    secondaryEnabled = draftEnabled.value,
                ) { Text("Registration must open in the future to publish.") }
            }
        }
        compose.onNodeWithText("Publish").assertIsNotEnabled()
        compose.onNodeWithText("Save draft").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(0, saved)
            draftEnabled.value = true
        }
        compose.onNodeWithText("Save draft").performClick()
        compose.runOnIdle { assertEquals(1, saved) }
    }

    /** Long text at enlarged scale keeps both explicit review actions reachable. */
    @Test fun largeTextKeepsBothReviewActionsReachable() {
        var saved = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                CampsbridgeTheme(settings = AccountSettings(fontScale = 1.25, controlScale = 1.5)) {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        StepScaffold(
                            "Review campsite",
                            "Step 8 of 8",
                            {},
                            {},
                            nextLabel = "Publish",
                            secondaryLabel = "Save draft",
                            onSecondaryAction = { saved++ },
                        ) {
                            repeat(20) { Text("A campsite fact to review before publication.") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Publish").assertIsDisplayed()
        compose.onNodeWithText("Save draft").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, saved) }
    }

    /** Large text and long content retain a visible, working bottom action. */
    @Test fun largeTextKeepsNextReachable() {
        var next = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                CampsbridgeTheme(settings = AccountSettings(fontScale = 1.25, controlScale = 1.5)) {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        StepScaffold("Your camping preferences", "Step 2 of 3", {}, { next++ }) {
                            repeat(20) { Text("A long description of a camping preference.") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Next").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, next) }
    }

    /** The actual keyboard leaves both footer actions visible after focusing the input. */
    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun keyboardKeepsBothReviewActionsReachable() {
        var keyboardVisible = false
        var next = 0
        var saved = 0
        compose.setContent {
            CampsbridgeTheme {
                val visible = WindowInsets.isImeVisible
                SideEffect { keyboardVisible = visible }
                val name = remember { mutableStateOf("") }
                StepScaffold(
                    "Your details",
                    "Step 1 of 3",
                    {},
                    { next++ },
                    secondaryLabel = "Save draft",
                    onSecondaryAction = { saved++ },
                ) {
                    AppTextField(name.value, { name.value = it }, "Name")
                }
            }
        }
        compose.onNodeWithText("Name").performClick().assertIsFocused()
        // A fresh emulator initializes the system IME lazily after the first focus request.
        compose.waitUntil(15_000) { keyboardVisible }
        compose.onNodeWithText("Save draft").assertIsDisplayed().performClick()
        compose.onNodeWithText("Next").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, next)
            assertEquals(1, saved)
        }
    }
}
