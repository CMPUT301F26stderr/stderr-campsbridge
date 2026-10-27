package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.ui.common.options.CampingOptions
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceOption
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.PreferenceChips

/** Optional compact interests using stable tag IDs; existing unlisted interests are retained.
 * @param preferences Current requested interests.
 * @param onChange Reports explicitly requested interest selection.
 * @param enabled Whether selections are editable.
 */
@Composable
fun InterestsStep(
    preferences: Preferences,
    onChange: (Preferences) -> Unit,
    enabled: Boolean,
) {
    Text(
        stringResource(R.string.onboarding_interests_optional),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PreferenceChips(
        options = CampingOptions.tags.map { ChoiceOption(it.id, stringResource(it.label)) },
        selectedIds = preferences.tags,
        onToggle = { id ->
            val tags = if (id in preferences.tags) preferences.tags - id else preferences.tags + id
            onChange(preferences.copy(tags = tags))
        },
        enabled = enabled,
    )
}
