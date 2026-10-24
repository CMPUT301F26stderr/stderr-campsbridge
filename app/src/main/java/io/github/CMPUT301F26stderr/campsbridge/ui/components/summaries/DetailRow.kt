package io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Readable label/value pair with an optional contextual edit action.
 * @param label Localized fact label.
 * @param value Formatted value, permitted to wrap.
 * @param modifier Caller layout.
 * @param onEdit Optional explicit editing callback.
 */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (onEdit != null) TextButton(onEdit) { Text(stringResource(R.string.edit)) }
    }
}
