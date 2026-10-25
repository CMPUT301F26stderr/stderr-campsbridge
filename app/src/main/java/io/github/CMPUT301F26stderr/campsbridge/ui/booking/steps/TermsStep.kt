package io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingAgreement
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.CheckboxRow

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
    Text(rules.ifEmpty { stringResource(R.string.booking_no_rules) })
    Text(stringResource(R.string.policy_simulation_body))
    NavigationRow(stringResource(R.string.refund_policy_title), onPolicy, enabled = enabled)
    CheckboxRow(
        stringResource(R.string.booking_agreement),
        state.draft.agreement == BookingAgreement(rules, BookingPolicy.VERSION),
        { onAccept(it, rules, BookingPolicy.VERSION) },
        enabled = enabled,
    )
}
