package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
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
    val options =
        listOf(
            "tent" to R.string.onboarding_tent,
            "national-parks" to R.string.onboarding_national_parks,
            "backcountry" to R.string.onboarding_backcountry,
            "rv" to R.string.onboarding_rv,
            "lakeside" to R.string.onboarding_lakeside,
            "family" to R.string.onboarding_family,
            "stargazing" to R.string.onboarding_stargazing,
        )
    for ((id, label) in options) {
        CheckboxRow(stringResource(label), id in preferences.tags, { checked ->
            val tags = if (checked) preferences.tags + id else preferences.tags - id
            onChange(preferences.copy(tags = tags))
        }, enabled = enabled)
    }
}
