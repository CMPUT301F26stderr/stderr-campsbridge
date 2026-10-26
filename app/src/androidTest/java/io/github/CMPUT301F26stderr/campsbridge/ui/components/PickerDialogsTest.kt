package io.github.CMPUT301F26stderr.campsbridge.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.DateInputDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.DateRangeInputDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.TimeInputDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Shared Material dialog application/cancellation contracts. */
class PickerDialogsTest {
    @get:Rule val compose = createComposeRule()

    /** A cancelled temporary date never replaces the applied value or reappears on reopen. */
    @Test fun dateCancellationDiscardsTemporaryChanges() {
        val applied = LocalDate.of(2027, 5, 14)
        val open = mutableStateOf(true)
        var result: LocalDate? = null
        compose.setContent {
            CampsbridgeTheme {
                TextButton({ open.value = true }) { Text("Open picker") }
                if (open.value) {
                    DateInputDialog(applied, {
                        result = it
                        open.value = false
                    }, {
                        open.value =
                            false
                    })
                }
            }
        }
        compose.onNodeWithText("May 15, 2027", substring = true).performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(result) }
        compose.onNodeWithText("Open picker").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { assertEquals(applied, result) }
    }

    /** Range edits require two endpoints, can be cancelled and return the chosen checkout. */
    @Test fun rangeEditsRequireCompletionAndApplyOnlyOnConfirm() {
        val from = LocalDate.of(2027, 5, 14)
        val until = from.plusDays(2)
        var result: Pair<LocalDate, LocalDate>? = null
        val open = mutableStateOf(true)
        compose.setContent {
            CampsbridgeTheme {
                TextButton({ open.value = true }) { Text("Open picker") }
                if (open.value) {
                    DateRangeInputDialog(
                        from,
                        until,
                        { a, b ->
                            result = a to b
                            open.value = false
                        },
                        { open.value = false },
                    )
                }
            }
        }
        compose.onNodeWithText("May 18, 2027", substring = true).performClick()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(result) }
        compose.onNodeWithText("Open picker").performClick()
        compose.onNodeWithText("May 18, 2027", substring = true).performClick()
        compose.onNodeWithText("May 20, 2027", substring = true).performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { assertEquals(from.plusDays(4) to from.plusDays(6), result) }
    }

    /** Edited wall time is discarded on Cancel and applied on Confirm without a timezone. */
    @Test fun timeEditsApplyOnlyOnConfirm() {
        val selected = LocalTime.of(18, 30)
        val open = mutableStateOf(true)
        var result: LocalTime? = null
        compose.setContent {
            CampsbridgeTheme {
                TextButton({ open.value = true }) { Text("Open picker") }
                if (open.value) {
                    TimeInputDialog(
                        selected,
                        {
                            result = it
                            open.value = false
                        },
                        { open.value = false },
                        is24Hour = true,
                    )
                }
            }
        }
        compose.onNodeWithText("Use keyboard").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("19")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(result) }
        compose.onNodeWithText("Open picker").performClick()
        compose.onNodeWithText("Use keyboard").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("19")
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { assertEquals(LocalTime.of(19, 30), result) }
    }
}
