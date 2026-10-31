package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationRecordScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationReference
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.reservationTime
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Reads original payment and confirmed refund facts from the same saved aggregate.
 * @param state Saved parent's read outcome.
 * @param onBack Returns to the retained overview.
 * @param onRetry Restarts the existing failed source without leaving this child.
 * @param modifier Caller layout.
 */
@Composable
fun PaymentDetailsScreen(
    state: ReservationDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    ReservationRecordScreen(
        R.string.reservation_payment,
        state,
        onBack,
        onRetry,
        modifier,
    ) { record ->
        ReservationReference(record)
        SummarySection(stringResource(R.string.reservation_payment_record)) {
            DetailRow(
                stringResource(R.string.reservation_total_paid),
                formatCad(record.payment.amountCents, locale),
            )
            DetailRow(
                stringResource(R.string.reservation_paid_at),
                reservationTime(record, record.payment.paidAt),
            )
            Text(stringResource(R.string.reservation_simulated_payment))
        }
        record.payment.refundedAt?.let { refundedAt ->
            SummarySection(stringResource(R.string.reservation_full_refund)) {
                DetailRow(
                    stringResource(R.string.reservation_refunded_amount),
                    formatCad(record.payment.amountCents, locale),
                )
                DetailRow(
                    stringResource(R.string.reservation_refunded_at),
                    reservationTime(record, refundedAt),
                )
            }
        }
    }
}

/** Shows recorded payment/refund history and read outcomes at both text sizes.
 * @param state Passive preview outcome.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Preview(showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2f)
@Composable
private fun PaymentDetailsPreview(
    @PreviewParameter(ReservationDetailPreviewProvider::class) state: ReservationDetailUiState,
) {
    CampsbridgeTheme { PaymentDetailsScreen(state, {}, {}) }
}
