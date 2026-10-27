package io.github.CMPUT301F26stderr.campsbridge.ui.components.selection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** A passive tag, without a disabled control appearance or a misleading click target.
 * @param text Localized or saved tag label.
 * @param modifier Caller layout.
 */
@Composable
fun TagLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
