/*
 * Displays booked history, saved-page links and explicit cancellation confirmation.
 */

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
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationGroup
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.StaySummary
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.CancellationPromptDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationCancellationDetails
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationRecordScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationReference
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.reservationStatusLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Displays booked history, saved-page links and explicit cancellation confirmation.
 * @param state Saved parent's current read outcome.
 * @param onBack Returns to the caller.
 * @param onRetry Restarts a failed observation.
 * @param onPayment Opens this record's payment child.
 * @param onTerms Opens this record's accepted-terms child.
 * @param modifier Caller layout.
 * @param context Actual actor workspace.
 * @param cancellation Retained action projection.
 * @param onRequestCancel Opens confirmation.
 * @param onReason Edits optional input.
 * @param onConfirmCancel Confirms one guarded operation.
 * @param onDismissCancel Dismisses without writing.
 * @param onCheckCancellation Reads an uncertain result.
 * @param onMessages Opens the saved participant conversation when registered.
 */
@Composable
fun ReservationDetailScreen(
    state: ReservationDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPayment: () -> Unit,
    onTerms: () -> Unit,
    modifier: Modifier = Modifier,
    context: ReservationContext = ReservationContext.ENTRANT,
    cancellation: CancellationUiState = CancellationUiState(),
    onRequestCancel: () -> Unit = {},
    onReason: (String) -> Unit = {},
    onConfirmCancel: () -> Unit = {},
    onDismissCancel: () -> Unit = {},
    onCheckCancellation: () -> Unit = {},
    onMessages: (() -> Unit)? = null,
) {
    ReservationRecordScreen(
        R.string.reservation_title,
        state,
        onBack,
        onRetry,
        modifier,
        backEnabled = cancellation.phase == CancellationPhase.IDLE,
        feedback = { CancellationContent(cancellation, context, onCheckCancellation) },
    ) { record ->
        Text(
            reservationStatusLabel(record),
            style = MaterialTheme.typography.titleMediumEmphasized,
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
        NavigationGroup(
            buildList<@Composable () -> Unit> {
                if (onMessages != null) add { NavigationRow(stringResource(R.string.conversation_title), onMessages) }
                add { NavigationRow(stringResource(R.string.reservation_payment), onPayment) }
                add { NavigationRow(stringResource(R.string.reservation_accepted_terms), onTerms) }
            },
        )
        SummarySection(stringResource(R.string.reservation_stay_details)) {
            DetailRow(
                stringResource(R.string.campsite_location),
                record.bookedCampsite.locationLabel,
            )
            DetailRow(stringResource(R.string.campsite_time_zone), record.bookedCampsite.timeZone)
        }
        ReservationCancellationDetails(record)
        if (cancellation.canRequest) {
            AppActionButton(
                stringResource(cancellationAction(context)),
                onRequestCancel,
                style = ActionStyle.DESTRUCTIVE,
            )
        }
    }
    CancellationPromptDialog(
        (state as? ReservationDetailUiState.Content)?.reservation,
        context,
        cancellation,
        onReason,
        onConfirmCancel,
        onDismissCancel,
    )
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
