package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.options.CampingOption
import io.github.CMPUT301F26stderr.campsbridge.ui.common.options.CampingOptions
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.CheckboxRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceOption
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.PreferenceChips

/** Shared stable catalogs; unknown selections remain visible and are not silently dropped.
 * @param amenities Selected stable facility IDs.
 * @param tags Selected stable characteristic IDs.
 * @param onAmenity Changes one amenity.
 * @param onTag Changes one tag.
 */
@Composable
fun CampsiteOptionsStep(
    amenities: Set<String>,
    tags: Set<String>,
    onAmenity: (String, Boolean) -> Unit,
    onTag: (String, Boolean) -> Unit,
) {
    Text(stringResource(R.string.campsite_amenities), style = MaterialTheme.typography.titleMedium)
    CampingChoices(CampingOptions.amenities, amenities, onAmenity)
    Text(stringResource(R.string.campsite_tags), style = MaterialTheme.typography.titleMedium)
    CampingChoices(CampingOptions.tags, tags, onTag)
}

/** Composes existing choice controls without replacing the caller's full selection.
 * @param options Known stable IDs and labels.
 * @param selected Selected IDs, including saved unlisted values.
 * @param onChange Reports only the explicitly changed ID/value.
 */
@Composable
private fun CampingChoices(
    options: List<CampingOption>,
    selected: Set<String>,
    onChange: (String, Boolean) -> Unit,
) {
    PreferenceChips(
        options.map { ChoiceOption(it.id, stringResource(it.label)) },
        selected,
        { id -> onChange(id, id !in selected) },
    )
    for (id in selected - options.map { it.id }.toSet()) {
        CheckboxRow(id, true, { onChange(id, it) })
    }
}
