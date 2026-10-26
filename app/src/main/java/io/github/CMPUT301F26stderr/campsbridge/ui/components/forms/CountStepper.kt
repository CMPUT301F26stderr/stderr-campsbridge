package io.github.CMPUT301F26stderr.campsbridge.ui.components.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Displays a caller-owned bounded count; disabled bounds avoid arithmetic overflow.
 * @param label Accessible name and visible field label.
 * @param value Current count within bounds.
 * @param minimum Inclusive lower bound.
 * @param maximum Inclusive upper bound.
 * @param onValueChange Requested count; caller owns mutation.
 * @param modifier Caller layout.
 * @param enabled Whether interaction is permitted.
 * @param supportingText Optional explanation below the label.
 */
@Composable
fun CountStepper(
    label: String,
    value: Long,
    minimum: Long,
    maximum: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    require(minimum <= maximum && value in minimum..maximum)
    val decrease = stringResource(R.string.decrease_count, label)
    val increase = stringResource(R.string.increase_count, label)
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        IconButton(
            onClick = { onValueChange(value - 1) },
            enabled = enabled && value > minimum,
            modifier = Modifier.semantics { contentDescription = decrease },
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { stateDescription = "$label: $value" },
        )
        IconButton(
            onClick = { onValueChange(value + 1) },
            enabled = enabled && value < maximum,
            modifier = Modifier.semantics { contentDescription = increase },
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
