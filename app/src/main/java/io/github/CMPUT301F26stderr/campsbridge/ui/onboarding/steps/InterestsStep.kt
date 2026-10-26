package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.ui.common.options.CampingOptions
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.CheckboxRow

/** Optional checked interests using stable tag IDs; existing unlisted interests are retained.
 * @param preferences Current requested interests.
 * @param onChange Reports explicitly requested checkbox state.
 * @param enabled Whether selections are editable.
 */
@Composable
fun InterestsStep(
    preferences: Preferences,
    onChange: (Preferences) -> Unit,
    enabled: Boolean,
) {
    Text(stringResource(R.string.onboarding_interests_optional))
    for (option in CampingOptions.tags) {
        CheckboxRow(stringResource(option.label), option.id in preferences.tags, { checked ->
            val tags = if (checked) preferences.tags + option.id else preferences.tags - option.id
            onChange(preferences.copy(tags = tags))
        }, enabled = enabled)
    }
}
