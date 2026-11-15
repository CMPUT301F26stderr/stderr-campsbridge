package io.github.CMPUT301F26stderr.campsbridge.ui.components.actions

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Opens the page's creation flow from the scaffold's floating-action slot.
 * @param label Localized creation action.
 * @param onClick Starts the registered editor without creating a record.
 */
@Composable
fun CreateAction(
    label: String,
    onClick: () -> Unit,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        // This always-expanded overload keeps the visible label in the button's semantics.
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(Spacing.small))
        Text(label)
    }
}
