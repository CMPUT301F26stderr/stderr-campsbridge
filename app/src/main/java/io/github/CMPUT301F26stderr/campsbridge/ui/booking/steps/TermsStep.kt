package io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingAgreement
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.CheckboxRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Prototype 07.04 renders actual rules and the packaged policy without synthetic examples.
 * @param state Current source-backed rules and exact acknowledgement.
 * @param onAccept Checkbox action carrying the exact displayed rules/version.
 * @param onPolicy Opens the existing full policy reader without advancing.
 * @param enabled Whether acknowledgement and navigation are permitted.
 */
@Composable
fun TermsStep(
    state: BookingUiState.Content,
    onAccept: (Boolean, String, String) -> Unit,
    onPolicy: () -> Unit,
    enabled: Boolean,
) {
    val rules = state.campsite.details.rules
    OutlinedCard(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(
                stringResource(R.string.campsite_rules_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(rules.ifEmpty { stringResource(R.string.booking_no_rules) })
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        NavigationRow(stringResource(R.string.refund_policy_title), onPolicy, enabled = enabled)
        Text(
            stringResource(R.string.policy_simulation_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CheckboxRow(
        stringResource(R.string.booking_agreement),
        state.draft.agreement == BookingAgreement(rules, BookingPolicy.VERSION),
        { onAccept(it, rules, BookingPolicy.VERSION) },
        enabled = enabled,
    )
}
