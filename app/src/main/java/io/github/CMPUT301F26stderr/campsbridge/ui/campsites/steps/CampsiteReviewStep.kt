package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDraft
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsitePhotoIntent
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.validateCampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.common.options.CampingOptions
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow

/** Reads the same draft's facts and opens explicit edit sections, without claiming persistence.
 * @param draft Current raw input.
 * @param photo Local keep/replace/remove choice.
 * @param onEdit Opens one section for return to review.
 */
@Composable
fun CampsiteReviewStep(
    draft: CampsiteDraft,
    photo: CampsitePhotoIntent,
    onEdit: (CampsiteStep) -> Unit,
) {
    val valid = validateCampsiteDetails(draft) as? CampsiteDetailsInput.Value
    if (valid == null) Text(stringResource(R.string.campsite_review_incomplete))
    DetailRow(stringResource(R.string.campsite_name), draft.name)
    DetailRow(
        stringResource(R.string.campsite_description),
        draft.description.ifEmpty {
            stringResource(R.string.campsite_no_optional_value)
        },
    )
    ReviewEdit(CampsiteStep.BASICS, R.string.campsite_setup_basics, onEdit)
    Text(
        stringResource(
            when (photo) {
                CampsitePhotoIntent.Keep -> R.string.campsite_photo_unchanged
                CampsitePhotoIntent.Remove -> R.string.campsite_photo_removed
                is CampsitePhotoIntent.Replace -> R.string.campsite_photo_selected
            },
        ),
    )
    ReviewEdit(CampsiteStep.PHOTO, R.string.campsite_setup_photo, onEdit)
    DetailRow(stringResource(R.string.campsite_location), draft.locationLabel)
    draft.location?.let {
        Text(stringResource(R.string.campsite_coordinates, it.latitude, it.longitude))
    }
    DetailRow(stringResource(R.string.campsite_time_zone), draft.timeZone)
    ReviewEdit(CampsiteStep.LOCATION, R.string.campsite_location, onEdit)
    DetailRow(stringResource(R.string.campsite_max_guests), draft.maxGuestsText)
    DetailRow(
        stringResource(R.string.campsite_nightly_price),
        valid?.let {
            formatCad(it.details.nightlyPriceCents, LocalConfiguration.current.locales[0])
        } ?: draft.priceText,
    )
    ReviewEdit(CampsiteStep.CAPACITY_PRICE, R.string.campsite_setup_capacity, onEdit)
    val options = CampingOptions.amenities + CampingOptions.tags
    val labels = options.associate { it.id to stringResource(it.label) }
    DetailRow(
        stringResource(R.string.campsite_amenities),
        draft.amenities.joinToString {
            labels[it]
                ?: it
        },
    )
    DetailRow(stringResource(R.string.campsite_tags), draft.tags.joinToString { labels[it] ?: it })
    ReviewEdit(CampsiteStep.OPTIONS, R.string.campsite_setup_options, onEdit)
    DetailRow(
        stringResource(R.string.campsite_setup_rules),
        draft.rules.ifEmpty {
            stringResource(R.string.campsite_no_optional_value)
        },
    )
    ReviewEdit(CampsiteStep.RULES, R.string.campsite_setup_rules, onEdit)
    Text(
        stringResource(
            R.string.campsite_requested_nights,
            draft.requestedAvailability.values.count { it },
            draft.requestedAvailability.values.count { !it },
        ),
    )
    if (draft.id != null) Text(stringResource(R.string.campsite_saved_availability))
    ReviewEdit(CampsiteStep.AVAILABILITY, R.string.campsite_setup_availability, onEdit)
}

/** Reuses a text action for direct review editing.
 * @param step Target section.
 * @param title Section label resource.
 * @param onEdit Parent-owned step change.
 */
@Composable
private fun ReviewEdit(
    step: CampsiteStep,
    title: Int,
    onEdit: (CampsiteStep) -> Unit,
) {
    TextButton({
        onEdit(step)
    }) { Text(stringResource(R.string.campsite_edit_section, stringResource(title))) }
}
