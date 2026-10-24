package io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** A labeled Material field opening an external picker without pretending to be editable text.
 * @param label Localized field label.
 * @param value Formatted value, or null when not selected.
 * @param onClick Opens the caller's picker.
 * @param modifier Caller layout.
 * @param enabled Whether selection is permitted.
 * @param errorMessage Optional local validation feedback.
 */
@Composable
fun PickerField(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Surface(
            onClick,
            enabled = enabled,
            modifier =
                Modifier.fillMaxWidth().semantics {
                    role =
                        Role.Button
                },
            shape = MaterialTheme.shapes.small,
            border =
                BorderStroke(
                    androidx.compose.ui.unit
                        .Dp(1f),
                    if (errorMessage !=
                        null
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
        ) {
            Column(Modifier.padding(Spacing.medium)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value ?: stringResource(R.string.choose_value),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        errorMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
