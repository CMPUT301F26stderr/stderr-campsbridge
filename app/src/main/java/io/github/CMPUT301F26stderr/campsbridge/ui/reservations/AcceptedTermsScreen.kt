package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyDocumentContent
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.policyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationRecordScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationReference
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.reservationTime
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Shows the exact saved rules and policy version without substituting current listing terms.
 * @param state Saved parent's read outcome.
 * @param onBack Returns to the retained overview.
 * @param onRetry Restarts the failed source on this child.
 * @param modifier Caller layout.
 */
@Composable
fun AcceptedTermsScreen(
    state: ReservationDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ReservationRecordScreen(
        R.string.reservation_accepted_terms,
        state,
        onBack,
        onRetry,
        modifier,
    ) { record ->
        val terms = record.acceptedTerms
        ReservationReference(record)
        SummarySection(stringResource(R.string.reservation_acceptance_record)) {
            DetailRow(
                stringResource(R.string.reservation_accepted_at),
                reservationTime(record, terms.acceptedAt),
            )
            DetailRow(
                stringResource(R.string.reservation_policy_version),
                terms.cancellationPolicyVersion,
            )
        }
        SummarySection(stringResource(R.string.campsite_rules_title)) {
            Text(terms.rules.ifBlank { stringResource(R.string.reservation_no_saved_rules) })
        }
        SummarySection(stringResource(R.string.reservation_refund_policy)) {
            PolicyDocumentContent(policyContent(PolicyId.REFUND, terms.cancellationPolicyVersion))
        }
    }
}

/** Shows exact and unavailable policy versions with ordinary and enlarged text.
 * @param state Passive preview outcome.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Preview(showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2f)
@Composable
private fun AcceptedTermsPreview(
    @PreviewParameter(ReservationDetailPreviewProvider::class) state: ReservationDetailUiState,
) {
    CampsbridgeTheme { AcceptedTermsScreen(state, {}, {}) }
}
