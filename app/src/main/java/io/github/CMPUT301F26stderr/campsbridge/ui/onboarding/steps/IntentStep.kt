package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceCard
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

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
    Column(
        Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        ChoiceCard(
            stringResource(R.string.onboarding_find),
            selected == Role.ENTRANT,
            { onSelect(Role.ENTRANT) },
            Icons.Outlined.LocationOn,
            enabled = enabled,
        )
        ChoiceCard(
            stringResource(R.string.onboarding_manage),
            selected == Role.OWNER,
            { onSelect(Role.OWNER) },
            Icons.Outlined.Home,
            enabled = enabled,
        )
    }
    Text(
        stringResource(R.string.onboarding_switch_later),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
