package io.github.CMPUT301F26stderr.campsbridge.ui.components.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale

/** Presents a small exclusive choice without owning the applied selection.
 * @param options Stable choices in display order.
 * @param selectedId Applied choice; exactly one option must match.
 * @param onSelect Requests a different choice. Tapping the active option keeps it selected.
 * @param modifier Caller-supplied width and layout.
 * @param enabled Whether changing the choice is permitted.
 */
@Composable
fun ConnectedChoices(
    options: List<ChoiceOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(options.size >= 2 && options.map { it.id }.distinct().size == options.size)
    require(options.any { it.id == selectedId })
    Row(
        modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option.id == selectedId,
                onCheckedChange = { checked -> if (checked && option.id != selectedId) onSelect(option.id) },
                enabled = enabled,
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp * LocalControlScale.current.coerceAtLeast(1f))
                        .semantics {
                            role = Role.RadioButton
                            selected = option.id == selectedId
                        },
            ) { Text(option.label) }
        }
    }
}
