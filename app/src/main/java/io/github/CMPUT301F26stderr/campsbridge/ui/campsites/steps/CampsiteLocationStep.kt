/*
 * Ordinary location inputs plus a real registered picker action when available.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDraft
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField

/** Ordinary location inputs plus a real registered picker action when available.
 * @param draft Current label, coordinate and timezone.
 * @param errors Current explicit validation feedback.
 * @param onLabel Local label edit.
 * @param onZone Local IANA zone edit.
 * @param onChoose Opens a registered map child; isolated content can omit this action.
 */
@Composable
fun CampsiteLocationStep(
    draft: CampsiteDraft,
    errors: CampsiteDetailsInput.Errors,
    onLabel: (String) -> Unit,
    onZone: (String) -> Unit,
    onChoose: (() -> Unit)?,
) {
    val zoneFocus = remember { FocusRequester() }
    AppTextField(
        draft.locationLabel,
        onLabel,
        stringResource(R.string.campsite_location_label),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { zoneFocus.requestFocus() }),
    )
    draft.location?.let {
        Text(stringResource(R.string.campsite_coordinates, it.latitude, it.longitude))
    }
    if (errors.locationRequired) Text(stringResource(R.string.campsite_pin_required))
    if (onChoose != null) {
        TextButton(onChoose) { Text(stringResource(R.string.campsite_choose_location)) }
    } else {
        Text(stringResource(R.string.campsite_map_pending))
    }
    AppTextField(
        draft.timeZone,
        onZone,
        stringResource(R.string.campsite_time_zone),
        modifier = Modifier.focusRequester(zoneFocus),
        supportingText = stringResource(R.string.campsite_zone_hint),
        revealError = errors.timeZoneInvalid && !errors.locationRequired,
        errorMessage =
            if (errors.timeZoneInvalid) {
                stringResource(
                    R.string.campsite_zone_invalid,
                )
            } else {
                null
            },
    )
}
