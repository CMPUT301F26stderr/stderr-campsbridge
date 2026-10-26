package io.github.CMPUT301F26stderr.campsbridge.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigation
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.CountStepper
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepProgress
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.CheckboxRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceOption
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.DropdownField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.PreferenceChips
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.SwitchRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Contracts for controlled shared components and their accessibility actions. */
class SharedControlsTest {
    @get:Rule val compose = createComposeRule()

    /** Temporarily disabling a form closes its dropdown without reopening it on completion. */
    @Test fun disablingDropdownClearsExpandedState() {
        val enabled = mutableStateOf(true)
        compose.setContent {
            CampsbridgeTheme {
                DropdownField(
                    "Timezone",
                    listOf(
                        ChoiceOption("edmonton", "Edmonton"),
                        ChoiceOption("vancouver", "Vancouver"),
                    ),
                    "edmonton",
                    {},
                    enabled = enabled.value,
                )
            }
        }
        compose.onNodeWithText("Timezone").performClick()
        compose.onNodeWithText("Vancouver").assertIsDisplayed()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithText("Vancouver").assertDoesNotExist()
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithText("Vancouver").assertDoesNotExist()
    }

    /** Dropdown labels can change while callbacks retain stable option identities. */
    @Test fun dropdownReportsChoiceWithoutMutatingAppliedSelection() {
        var received: String? = null
        compose.setContent {
            CampsbridgeTheme {
                DropdownField(
                    "Timezone",
                    listOf(
                        ChoiceOption("edmonton", "Edmonton"),
                        ChoiceOption("vancouver", "Vancouver"),
                    ),
                    "edmonton",
                    { received = it },
                )
            }
        }
        compose.onNodeWithText("Timezone").performClick()
        compose.onNodeWithText("Vancouver").performClick()
        compose.runOnIdle { assertEquals("vancouver", received) }
        compose.onNodeWithText("Edmonton").assertIsDisplayed()
    }

    /** Both bounds block clicks; stepping to Long.MAX_VALUE remains overflow-safe. */
    @Test fun stepperHonorsBothBoundsAndLongMaximum() {
        val count = mutableStateOf(Long.MAX_VALUE - 1)
        val changes = mutableListOf<Long>()
        compose.setContent {
            CampsbridgeTheme {
                CountStepper(
                    "Guests",
                    count.value,
                    Long.MAX_VALUE - 1,
                    Long.MAX_VALUE,
                    {
                        count.value = it
                        changes.add(it)
                    },
                )
            }
        }
        compose.onNodeWithContentDescription("Decrease Guests").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Increase Guests").performClick().assertIsNotEnabled()
        compose.onNodeWithText(Long.MAX_VALUE.toString()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Decrease Guests").performClick().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(Long.MAX_VALUE, Long.MAX_VALUE - 1), changes) }
    }

    /** A disabled control does not forward a change even when its value is inside bounds. */
    @Test fun disabledStepperBlocksActions() {
        var changes = 0
        compose.setContent {
            CampsbridgeTheme { CountStepper("Guests", 4, 1, 8, { changes++ }, enabled = false) }
        }
        compose.onNodeWithContentDescription("Increase Guests").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Decrease Guests").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, changes) }
    }

    /** A chip reports stable identity and leaves the supplied selection unchanged. */
    @Test fun chipSelectionRemainsCallerOwned() {
        val selected = setOf("water")
        var received: String? = null
        compose.setContent {
            CampsbridgeTheme {
                PreferenceChips(
                    listOf(ChoiceOption("water", "Water"), ChoiceOption("quiet", "Quiet")),
                    selected,
                    { received = it },
                )
            }
        }
        compose.onNodeWithText("Water").assertIsSelected().performClick()
        compose.runOnIdle {
            assertEquals("water", received)
            assertEquals(setOf("water"), selected)
        }
        compose.onNodeWithText("Water").assertIsSelected()
    }

    /** Each full-row control forwards one event rather than firing both row and child actions. */
    @Test fun selectionRowsHaveSingleActions() {
        var choice = 0
        var toggle = 0
        var acknowledgement = 0
        compose.setContent {
            CampsbridgeTheme {
                Column {
                    ChoiceRow("Owner", false, { choice++ })
                    SwitchRow("Notifications", false, { toggle++ })
                    CheckboxRow("Accept rules", false, { acknowledgement++ })
                }
            }
        }
        compose.onNodeWithText("Owner").performClick()
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithText("Accept rules").performClick()
        compose.runOnIdle {
            assertEquals(1, choice)
            assertEquals(1, toggle)
            assertEquals(1, acknowledgement)
        }
    }

    /** Multiline input preserves newlines and gives errors precedence over normal help. */
    @Test fun multilineFieldPreservesInputAndErrorPrecedence() {
        val text = mutableStateOf("")
        compose.setContent {
            CampsbridgeTheme {
                AppTextField(
                    text.value,
                    { text.value = it },
                    "Rules",
                    singleLine = false,
                    supportingText = "Normal help",
                    errorMessage = "Review these rules",
                )
            }
        }
        compose.onNodeWithText("Rules").performTextInput("Keep quiet\nLeave no trace")
        compose.onNodeWithText("Rules").assertTextContains("Keep quiet\nLeave no trace")
        compose.onNodeWithText("Review these rules").assertIsDisplayed()
        compose.onNodeWithText("Normal help").assertDoesNotExist()
    }

    /** Navigation rows invoke only the supplied callback. */
    @Test fun navigationRowOpensItsDestination() {
        var opened = 0
        compose.setContent { CampsbridgeTheme { NavigationRow("Personal details", { opened++ }) } }
        compose.onNodeWithText("Personal details").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    /** Workspace navigation reflects supplied selection and reports stable destination IDs. */
    @Test fun workspaceNavigationUsesCallerSelection() {
        val selected = mutableStateOf("explore")
        compose.setContent {
            CampsbridgeTheme {
                WorkspaceNavigation(
                    listOf(
                        WorkspaceNavigationItem("explore", "Explore", Icons.Default.Search),
                        WorkspaceNavigationItem("profile", "Profile", Icons.Default.Person),
                    ),
                    selected.value,
                    { selected.value = it },
                )
            }
        }
        compose.onNodeWithText("Explore").assertIsSelected()
        compose.onNodeWithText("Profile").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals("profile", selected.value) }
    }

    /** A final step uses its supplied action label and preserves distinct skip/cancel callbacks. */
    @Test fun wizardUsesExplicitFinalAndSecondaryActions() {
        var published = 0
        var skipped = 0
        var cancelled = 0
        compose.setContent {
            CampsbridgeTheme {
                StepScaffold(
                    "Add campsite",
                    "Step 3 of 3",
                    {},
                    { published++ },
                    nextLabel = "Publish",
                    steps = StepProgress(3, 3),
                    onSkip = { skipped++ },
                    onCancel = { cancelled++ },
                ) {}
            }
        }
        compose.onNodeWithText("Publish").performClick()
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(1, published)
            assertEquals(1, skipped)
            assertEquals(1, cancelled)
        }
    }

    /** Submission blocks both visible and system Back, skip, cancel and repeat continuation. */
    @Test fun submittingWizardBlocksEveryExitAction() {
        var exits = 0
        compose.setContent {
            CampsbridgeTheme {
                StepScaffold(
                    "Add campsite",
                    "Step 3 of 3",
                    { exits++ },
                    { exits++ },
                    isSubmitting = true,
                    nextLabel = "Publish",
                    steps = StepProgress(3, 3),
                    onSkip = { exits++ },
                    onCancel = { exits++ },
                ) {}
            }
        }
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.onNodeWithText("Publish").assertIsNotEnabled()
        compose.onNodeWithText("Skip for now").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Add campsite").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, exits) }
    }
}
