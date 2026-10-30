package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.StaySummary
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationCancellationDetails
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationRecordScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationReference
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.reservationStatusLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Displays booked facts and saved-page links without creating or changing a reservation.
 * @param state Saved parent's current read outcome.
 * @param onBack Returns to the caller.
 * @param onRetry Restarts a failed observation.
 * @param onPayment Opens this record's payment child.
 * @param onTerms Opens this record's accepted-terms child.
 * @param modifier Caller layout.
 */
@Composable
fun ReservationDetailScreen(
    state: ReservationDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPayment: () -> Unit,
    onTerms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ReservationRecordScreen(
        R.string.reservation_title,
        state,
        onBack,
        onRetry,
        modifier,
    ) { record ->
        Text(
            reservationStatusLabel(record),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        ReservationReference(record)
        StaySummary(
            record.bookedCampsite.name,
            record.checkInDate,
            record.checkOutDate,
            record.guestCount,
            record.payment.amountCents,
        )
        NavigationRow(stringResource(R.string.reservation_payment), onPayment)
        NavigationRow(stringResource(R.string.reservation_accepted_terms), onTerms)
        SummarySection(stringResource(R.string.reservation_stay_details)) {
            DetailRow(
                stringResource(R.string.campsite_location),
                record.bookedCampsite.locationLabel,
            )
            DetailRow(stringResource(R.string.campsite_time_zone), record.bookedCampsite.timeZone)
        }
        ReservationCancellationDetails(record)
    }
}

/** Supplies saved history and every read outcome to passive themed previews. */
class ReservationDetailPreviewProvider : PreviewParameterProvider<ReservationDetailUiState> {
    override val values: Sequence<ReservationDetailUiState>
        get() {
            val record = previewReservation()
            val cancelledAt = record.createdAt.plusSeconds(60)
            return sequenceOf(
                ReservationDetailUiState.Loading,
                ReservationDetailUiState.Unavailable,
                ReservationDetailUiState.Failure,
                ReservationDetailUiState.Content(record),
                ReservationDetailUiState.Content(
                    record.copy(
                        cancellation =
                            ReservationCancellation(
                                cancelledAt,
                                "owner",
                                Role.OWNER,
                                "Campsite closure",
                            ),
                        payment = record.payment.copy(refundedAt = cancelledAt),
                    ),
                ),
                ReservationDetailUiState.Content(
                    record.copy(
                        acceptedTerms =
                            record.acceptedTerms.copy(
                                cancellationPolicyVersion = "retired-policy-v0",
                            ),
                    ),
                ),
            )
        }
}

/** Shows overview content and feedback with ordinary and enlarged system text.
 * @param state Passive preview outcome.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Preview(showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2f)
@Composable
private fun ReservationDetailPreview(
    @PreviewParameter(ReservationDetailPreviewProvider::class) state: ReservationDetailUiState,
) {
    CampsbridgeTheme { ReservationDetailScreen(state, {}, {}, {}, {}) }
}
