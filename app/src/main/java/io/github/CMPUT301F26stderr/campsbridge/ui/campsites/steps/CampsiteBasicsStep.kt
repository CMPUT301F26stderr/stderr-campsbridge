package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField

/** Name and optional description; raw values are owned by the parent draft.
 * @param name Current name input.
 * @param description Optional multiline input.
 * @param nameRequired Whether explicit Next found a blank name.
 * @param onName Local name edit.
 * @param onDescription Local description edit.
 */
@Composable
fun CampsiteBasicsStep(
    name: String,
    description: String,
    nameRequired: Boolean,
    onName: (String) -> Unit,
    onDescription: (String) -> Unit,
) {
    AppTextField(
        name,
        onName,
        stringResource(R.string.campsite_name),
        errorMessage = if (nameRequired) stringResource(R.string.campsite_name_required) else null,
    )
    AppTextField(
        description,
        onDescription,
        stringResource(R.string.campsite_description),
        singleLine = false,
        minLines = 3,
    )
}
