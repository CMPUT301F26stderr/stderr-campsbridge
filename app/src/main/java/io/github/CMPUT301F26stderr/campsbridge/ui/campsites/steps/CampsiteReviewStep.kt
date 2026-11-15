/*
 * Reads the same draft's facts and opens explicit edit sections, without claiming persistence.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppIconAction
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Reads the same draft's facts and opens explicit edit sections, without claiming persistence.
 * @param draft Current raw input.
 * @param photo Local keep/replace/remove choice.
 * @param onEdit Opens one section for return to review.
 * @param editingEnabled Whether editing is safe during a save or uncertain photo result.
 */
@Composable
fun CampsiteReviewStep(
    draft: CampsiteDraft,
    photo: CampsitePhotoIntent,
    onEdit: (CampsiteStep) -> Unit,
    editingEnabled: Boolean = true,
) {
    val valid = validateCampsiteDetails(draft) as? CampsiteDetailsInput.Value
    if (valid == null) Text(stringResource(R.string.campsite_review_incomplete))
    ReviewSection {
        DetailRow(stringResource(R.string.campsite_name), draft.name)
        DetailRow(
            stringResource(R.string.campsite_description),
            draft.description.ifEmpty {
                stringResource(R.string.campsite_no_optional_value)
            },
        )
        ReviewEdit(CampsiteStep.BASICS, R.string.campsite_setup_basics, onEdit, editingEnabled)
    }
    ReviewSection {
        Text(
            stringResource(
                when (photo) {
                    CampsitePhotoIntent.Keep -> R.string.campsite_photo_unchanged
                    CampsitePhotoIntent.Remove -> R.string.campsite_photo_removed
                    is CampsitePhotoIntent.Replace -> R.string.campsite_photo_selected
                },
            ),
        )
        ReviewEdit(CampsiteStep.PHOTO, R.string.campsite_setup_photo, onEdit, editingEnabled)
    }
    ReviewSection {
        DetailRow(stringResource(R.string.campsite_location), draft.locationLabel)
        draft.location?.let {
            Text(stringResource(R.string.campsite_coordinates, it.latitude, it.longitude))
        }
        DetailRow(stringResource(R.string.campsite_time_zone), draft.timeZone)
        ReviewEdit(CampsiteStep.LOCATION, R.string.campsite_location, onEdit, editingEnabled)
    }
    ReviewSection {
        DetailRow(stringResource(R.string.campsite_max_guests), draft.maxGuestsText)
        DetailRow(
            stringResource(R.string.campsite_nightly_price),
            valid?.let {
                formatCad(it.details.nightlyPriceCents, LocalConfiguration.current.locales[0])
            } ?: draft.priceText,
        )
        ReviewEdit(
            CampsiteStep.CAPACITY_PRICE,
            R.string.campsite_setup_capacity,
            onEdit,
            editingEnabled,
        )
    }
    val options = CampingOptions.amenities + CampingOptions.tags
    val labels = options.associate { it.id to stringResource(it.label) }
    ReviewSection {
        DetailRow(
            stringResource(R.string.campsite_amenities),
            draft.amenities.joinToString {
                labels[it]
                    ?: it
            },
        )
        DetailRow(stringResource(R.string.campsite_tags), draft.tags.joinToString { labels[it] ?: it })
        ReviewEdit(CampsiteStep.OPTIONS, R.string.campsite_setup_options, onEdit, editingEnabled)
    }
    ReviewSection {
        DetailRow(
            stringResource(R.string.campsite_setup_rules),
            draft.rules.ifEmpty {
                stringResource(R.string.campsite_no_optional_value)
            },
        )
        ReviewEdit(CampsiteStep.RULES, R.string.campsite_setup_rules, onEdit, editingEnabled)
    }
    ReviewSection {
        Text(
            stringResource(
                R.string.campsite_requested_nights,
                draft.requestedAvailability.values.count { it },
                draft.requestedAvailability.values.count { !it },
            ),
        )
        if (draft.id != null) Text(stringResource(R.string.campsite_saved_availability))
        ReviewEdit(
            CampsiteStep.AVAILABILITY,
            R.string.campsite_setup_availability,
            onEdit,
            editingEnabled,
        )
    }
}

/** Opens a reviewed section through the shared tonal edit action.
 * @param step Target section.
 * @param title Section label resource.
 * @param onEdit Parent-owned step change.
 * @param enabled Whether the parent permits editing.
 */
@Composable
private fun ReviewEdit(
    step: CampsiteStep,
    title: Int,
    onEdit: (CampsiteStep) -> Unit,
    enabled: Boolean,
) {
    AppIconAction(stringResource(R.string.campsite_edit_section, stringResource(title)), {
        onEdit(step)
    }, enabled = enabled) {
        Icon(Icons.Outlined.Edit, contentDescription = null)
    }
}

/** Keeps each reviewed section and its edit action together.
 * @param content Draft facts and their existing edit action.
 */
@Composable
private fun ReviewSection(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
            content = content,
        )
    }
}
