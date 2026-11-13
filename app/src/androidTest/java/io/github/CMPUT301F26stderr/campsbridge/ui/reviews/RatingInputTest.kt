package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.CMPUT301F26stderr.campsbridge.ui.reviews.components.RatingInput
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Numeric single-choice semantics and explicit callbacks for a caller-owned rating. */
class RatingInputTest {
    @get:Rule val compose = createComposeRule()

    /** SC-03: selection is explicit and pending controls cannot emit another rating. */
    @Test fun starSelectionIsNumericAndCallerOwned() {
        val rating = mutableStateOf<Long?>(null)
        val enabled = mutableStateOf(true)
        val selected = mutableListOf<Long>()
        compose.setContent {
            CampsbridgeTheme {
                RatingInput(rating.value, {
                    selected += it
                    rating.value = it
                }, enabled = enabled.value)
            }
        }
        compose
            .onAllNodes(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
            ).assertCountEquals(5)
        for (value in 1..5) compose.onNodeWithContentDescription("Rate $value out of 5").assertIsNotSelected()
        compose.runOnIdle { assertEquals(emptyList<Long>(), selected) }
        compose.onNodeWithContentDescription("Rate 3 out of 5").performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf(3L), selected)
            enabled.value = false
        }
        for (value in 1..5) compose.onNodeWithContentDescription("Rate $value out of 5").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Rate 4 out of 5").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(3L), selected) }
    }
}
