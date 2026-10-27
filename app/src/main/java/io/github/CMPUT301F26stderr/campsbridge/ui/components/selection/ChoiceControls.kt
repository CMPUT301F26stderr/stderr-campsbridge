package io.github.CMPUT301F26stderr.campsbridge.ui.components.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** One visible option; stable identity is independent of its localized label.
 * @param id Stable caller-defined option ID.
 * @param label Localized visible label.
 */
data class ChoiceOption(
    val id: String,
    val label: String,
)

/** Radio choice whose complete row has one semantic selection action.
 * @param title Visible option label.
 * @param selected Caller-owned selected state.
 * @param onClick Selection request.
 * @param modifier Caller layout.
 * @param supportingText Optional explanation.
 * @param enabled Whether selection is permitted.
 */
@Composable
fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supportingText?.let { { Text(it) } },
        trailingContent = { RadioButton(selected, onClick = null, enabled = enabled) },
        modifier =
            modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
    )
}

/** Full-row toggle with a passive Material switch to prevent duplicate callbacks.
 * @param title Visible preference label.
 * @param checked Caller-owned value.
 * @param onCheckedChange Requested value.
 * @param modifier Caller layout.
 * @param supportingText Optional explanation.
 * @param enabled Whether edits are permitted.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supportingText?.let { { Text(it) } },
        trailingContent = { Switch(checked, onCheckedChange = null, enabled = enabled) },
        modifier =
            modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    )
}

/** Full-row acknowledgement with caller-owned acceptance state.
 * @param title Readable acknowledgement text.
 * @param checked Current acceptance.
 * @param onCheckedChange Requested acceptance.
 * @param modifier Caller layout.
 * @param enabled Whether edits are permitted.
 */
@Composable
fun CheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Checkbox(checked, onCheckedChange = null, enabled = enabled) },
        modifier =
            modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
    )
}

/** Wrapping Material chips; selection policy and mutation remain with the caller.
 * @param options Stable visible options.
 * @param selectedIds Currently selected identities.
 * @param onToggle Reports the clicked ID without changing the supplied set.
 * @param modifier Caller layout.
 * @param enabled Whether interaction is permitted.
 */
@Composable
fun PreferenceChips(
    options: List<ChoiceOption>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        options.forEach { option ->
            FilterChip(
                shape = CircleShape,
                selected = option.id in selectedIds,
                onClick = { onToggle(option.id) },
                label = { Text(option.label) },
                leadingIcon =
                    if (option.id in
                        selectedIds
                    ) {
                        ({ Icon(Icons.Default.Check, null) })
                    } else {
                        null
                    },
                enabled = enabled,
            )
        }
    }
}
