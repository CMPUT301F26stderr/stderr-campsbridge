package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField

/** Presents controlled name input and the device-identity explanation.
 * @param name Exact entered input.
 * @param onNameChange Reports input without persistence.
 * @param required Whether to show the required-field error.
 * @param enabled Whether editing is allowed.
 * @param onContinue Reports the keyboard Done action.
 */
@Composable
fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    required: Boolean,
    enabled: Boolean,
    onContinue: () -> Unit,
) {
    AppTextField(
        name,
        onNameChange,
        stringResource(R.string.onboarding_name),
        enabled = enabled,
        errorMessage = if (required) stringResource(R.string.onboarding_name_required) else null,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onContinue() }),
    )
    Text(stringResource(R.string.onboarding_device_identity))
}
