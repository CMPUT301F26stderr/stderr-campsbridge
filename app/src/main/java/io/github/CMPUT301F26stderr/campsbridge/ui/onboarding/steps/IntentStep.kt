package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceRow

/** Offers only public starting intents; selection never grants an account role.
 * @param selected Current choice.
 * @param onSelect Reports an explicit starting choice.
 * @param enabled Whether the flow allows edits.
 */
@Composable
fun IntentStep(
    selected: Role?,
    onSelect: (Role) -> Unit,
    enabled: Boolean,
) {
    ChoiceRow(
        stringResource(R.string.onboarding_find),
        selected == Role.ENTRANT,
        { onSelect(Role.ENTRANT) },
        enabled = enabled,
    )
    ChoiceRow(
        stringResource(R.string.onboarding_manage),
        selected == Role.OWNER,
        { onSelect(Role.OWNER) },
        enabled = enabled,
    )
    androidx.compose.material3.Text(stringResource(R.string.onboarding_switch_later))
}
