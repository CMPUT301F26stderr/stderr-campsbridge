/*
 * Interaction contracts for shared Material search, fields, menus and feedback.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteBasicsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppSearchField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.CountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceOption
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.DropdownField
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Covers shared Material control behavior that callers rely on across screens. */
class MaterialInteractionTest {
    @get:Rule val compose = createComposeRule()

    /** IME search submits once and returns focus to the results. */
    @Test fun imeSearchSubmitsOnceAndClearsFocus() {
        val query = mutableStateOf("")
        var submissions = 0
        compose.setContent {
            CampsbridgeTheme {
                AppSearchField(
                    query.value,
                    { query.value = it },
                    "Campsite search",
                    onSearch = { submissions++ },
                )
            }
        }

        compose.onNode(hasSetTextAction()).performClick().performTextInput("Jasper")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        compose.runOnIdle { assertEquals(1, submissions) }
    }

    /** The visible Search action shares IME submission behavior and does not submit twice. */
    @Test fun searchButtonSubmitsOnceAndClearsFocus() {
        val query = mutableStateOf("")
        var submissions = 0
        compose.setContent {
            CampsbridgeTheme {
                AppSearchField(
                    query.value,
                    { query.value = it },
                    "Campsite search",
                    onSearch = { submissions++ },
                )
            }
        }

        compose.onNode(hasSetTextAction()).performClick().performTextInput("Jasper")
        compose.onNodeWithText("Search").performClick()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        compose.runOnIdle { assertEquals(1, submissions) }
    }

    /** Clear search edits raw text, requests input focus and never submits the query. */
    @Test fun clearSearchClearsAndFocusesWithoutSubmitting() {
        val query = mutableStateOf("Jasper")
        var submissions = 0
        compose.setContent {
            CampsbridgeTheme {
                AppSearchField(
                    query.value,
                    { query.value = it },
                    "Campsite search",
                    onSearch = { submissions++ },
                )
            }
        }

        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("", query.value)
            assertEquals(0, submissions)
        }
    }

    /** Live filtering clears text and Done dismisses focus without adding a submit callback. */
    @Test fun liveSearchClearAndDoneDoNotSubmit() {
        val query = mutableStateOf("Jasper")
        compose.setContent {
            CampsbridgeTheme {
                AppSearchField(query.value, { query.value = it }, "Campsite search")
            }
        }

        compose.onNode(hasSetTextAction()).performClick()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.runOnIdle { assertEquals("", query.value) }
        compose.onNode(hasSetTextAction()).performTextInput("Banff")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        compose.runOnIdle { assertEquals("Banff", query.value) }
    }

    /** IME Next from campsite name moves focus to the optional multiline description. */
    @Test fun campsiteNameNextFocusesDescription() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            CampsbridgeTheme {
                Column {
                    CampsiteBasicsStep("", "", false, {}, {})
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.campsite_name)).performClick()
        compose.onNodeWithText(context.getString(R.string.campsite_name)).performImeAction()
        compose.onNodeWithText(context.getString(R.string.campsite_description)).assertIsFocused()
    }

    /** An open dropdown marks its applied option while keeping selection caller-owned. */
    @Test fun dropdownMarksCurrentChoiceAndReportsOneAlternative() {
        var selected: String? = null
        compose.setContent {
            CampsbridgeTheme {
                DropdownField(
                    "Region",
                    listOf(ChoiceOption("jasper", "Jasper"), ChoiceOption("banff", "Banff")),
                    "jasper",
                    { selected = it },
                )
            }
        }

        compose.onNodeWithText("Region").performClick()
        compose.onAllNodesWithText("Jasper").assertAny(isSelected())
        compose.onNodeWithText("Banff").performClick()
        compose.runOnIdle { assertEquals("banff", selected) }
        compose.onNodeWithText("Jasper").assertIsDisplayed()
    }

    /** A pending confirmation exposes progress in the dialog while its actions stay disabled. */
    @Test fun submittingConfirmationShowsDialogProgress() {
        compose.setContent {
            CampsbridgeTheme {
                ConfirmationDialog(
                    "Remove campsite?",
                    "This action cannot be undone.",
                    {},
                    {},
                    {},
                    isSubmitting = true,
                )
            }
        }

        compose
            .onAllNodes(
                hasAnyAncestor(isDialog()) and
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            ).assertCountEquals(1)
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    /** Count input accepts valid bounded changes and preserves invalid raw text for correction. */
    @Test fun countInputKeepsInvalidTextEditableAndDisablesStepping() {
        val input = mutableStateOf("4")
        val count = mutableStateOf<Long?>(4)
        val changes = mutableListOf<String>()
        compose.setContent {
            CampsbridgeTheme {
                CountInput(
                    input.value,
                    count.value,
                    {
                        input.value = it
                        count.value = it.toLongOrNull()
                        changes.add(it)
                    },
                    "Tent sites",
                    "tent sites",
                    1,
                    5,
                )
            }
        }

        compose.onNodeWithContentDescription("Increase tent sites").performClick()
        compose.onNodeWithContentDescription("Decrease tent sites").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("several")
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase tent sites").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Decrease tent sites").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(listOf("5", "4", "several"), changes)
            assertEquals("several", input.value)
        }
    }

    /** Name validation reserves space and exposes its specific error on the field. */
    @Test fun campsiteNameErrorIsAssociatedAndDoesNotMoveDescription() {
        val nameRequired = mutableStateOf(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nameLabel = context.getString(R.string.campsite_name)
        val errorMessage = context.getString(R.string.campsite_name_required)
        compose.setContent {
            CampsbridgeTheme {
                Column {
                    CampsiteBasicsStep("", "", nameRequired.value, {}, {})
                }
            }
        }

        val initialDescriptionTop =
            compose
                .onNodeWithText(context.getString(R.string.campsite_description))
                .getUnclippedBoundsInRoot()
                .top
        compose.runOnIdle { nameRequired.value = true }
        compose.onNodeWithText(errorMessage).assertIsDisplayed()
        compose.onNodeWithText(nameLabel).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, errorMessage),
        )
        val validatedDescriptionTop =
            compose
                .onNodeWithText(context.getString(R.string.campsite_description))
                .getUnclippedBoundsInRoot()
                .top
        compose.runOnIdle {
            assertTrue(
                kotlin.math.abs((initialDescriptionTop - validatedDescriptionTop).value) < 1f,
            )
        }
    }

    /** Explicit name validation brings its error into view inside a short scrolling form. */
    @Test fun campsiteNameErrorScrollsIntoViewAfterValidation() {
        val nameRequired = mutableStateOf(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val errorMessage = context.getString(R.string.campsite_name_required)
        compose.setContent {
            CampsbridgeTheme {
                Box(Modifier.height(240.dp)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(Modifier.height(360.dp))
                        CampsiteBasicsStep("", "", nameRequired.value, {}, {})
                        Text("End of form")
                    }
                }
            }
        }

        compose.runOnIdle { nameRequired.value = true }
        compose.onNodeWithText(errorMessage).assertIsDisplayed()
    }
}
