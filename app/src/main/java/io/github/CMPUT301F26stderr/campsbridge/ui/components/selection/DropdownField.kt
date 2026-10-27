package io.github.CMPUT301F26stderr.campsbridge.ui.components.selection

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R

/** Material dropdown whose visible choice remains owned by its caller.
 * @param label Localized field label.
 * @param options Available stable IDs and localized labels.
 * @param selectedId Applied choice, or null.
 * @param onSelect Reports a selected ID without mutating caller state.
 * @param modifier Caller layout.
 * @param enabled Whether selection is permitted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    options: List<ChoiceOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(options.map { it.id }.distinct().size == options.size)
    require(selectedId == null || options.any { it.id == selectedId })
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }
    ExposedDropdownMenuBox(expanded && enabled, { if (enabled) expanded = it }, modifier) {
        OutlinedTextField(
            value =
                options.find { it.id == selectedId }?.label ?: stringResource(
                    R.string.choose_value,
                ),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            modifier =
                Modifier.fillMaxWidth().menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled,
                ),
        )
        ExposedDropdownMenu(expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option.id)
                    },
                )
            }
        }
    }
}
