package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField

/** Optional owner rules and the same packaged refund policy used by booking.
 * @param rules Raw multiline rule input.
 * @param onRules Local input edit.
 * @param onOpenPolicy Opens the reader without replacing the parent draft.
 */
@Composable
fun CampsiteRulesStep(
    rules: String,
    onRules: (String) -> Unit,
    onOpenPolicy: () -> Unit,
) {
    AppTextField(
        rules,
        onRules,
        stringResource(R.string.campsite_rules_optional),
        singleLine = false,
        minLines = 4,
    )
    TextButton(onOpenPolicy) { Text(stringResource(R.string.campsite_read_refund)) }
}
