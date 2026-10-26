package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
    AppTextField(draft.locationLabel, onLabel, stringResource(R.string.campsite_location_label))
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
        supportingText = stringResource(R.string.campsite_zone_hint),
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
