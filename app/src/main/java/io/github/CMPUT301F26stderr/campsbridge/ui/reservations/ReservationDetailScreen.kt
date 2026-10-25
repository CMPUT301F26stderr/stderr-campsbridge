package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.StaySummary
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyDocumentContent
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.policyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Actual saved-reader children; each keeps its own saveable scroll position. */
enum class ReservationDetailPage { OVERVIEW, PAYMENT, TERMS }

/** Connects the shared parent to the current child without starting another repository read.
 * @param model Saved identity's single aggregate owner.
 * @param page Actual current child destination.
 * @param onBack Returns to this child's actual caller.
 * @param onPayment Opens the sibling payment child from overview.
 * @param onTerms Opens the sibling accepted-terms child from overview.
 */
@Composable
fun ReservationDetailRoute(
    model: ReservationDetailViewModel,
    page: ReservationDetailPage,
    onBack: () -> Unit,
    onPayment: () -> Unit,
    onTerms: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    when (page) {
        ReservationDetailPage.OVERVIEW -> {
            ReservationDetailScreen(
                state,
                onBack,
                model::retry,
                onPayment,
                onTerms,
            )
        }

        ReservationDetailPage.PAYMENT -> {
            PaymentDetailsScreen(state, onBack, model::retry)
        }

        ReservationDetailPage.TERMS -> {
            AcceptedTermsScreen(state, onBack, model::retry)
        }
    }
}

/** Displays booked facts and real saved child links; cancellation is added in M3d.
 * @param state Complete saved aggregate or explicit read outcome.
 * @param onBack Returns to the original list, lottery or Inbox.
 * @param onRetry Retries the sole failed aggregate source.
 * @param onPayment Opens this reservation's payment child.
 * @param onTerms Opens this reservation's historical terms child.
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
        Text(reservationStatusLabel(record), style = MaterialTheme.typography.titleMedium)
        DetailRow(stringResource(R.string.reservation_reference), record.id)
        StaySummary(
            record.bookedCampsite.name,
            record.checkInDate,
            record.checkOutDate,
            record.guestCount,
            record.payment.amountCents,
        )
        DetailRow(stringResource(R.string.campsite_location), record.bookedCampsite.locationLabel)
        DetailRow(stringResource(R.string.campsite_time_zone), record.bookedCampsite.timeZone)
        DetailRow(
            stringResource(R.string.reservation_booked_at),
            reservationTime(record, record.createdAt),
        )
        record.cancellation?.let { cancellation ->
            DetailRow(
                stringResource(R.string.reservation_cancelled_at),
                reservationTime(record, cancellation.at),
            )
            if (!cancellation.reason.isNullOrBlank()) {
                DetailRow(
                    stringResource(R.string.reservation_reason),
                    cancellation.reason,
                )
            }
        }
        NavigationRow(stringResource(R.string.reservation_payment), onPayment)
        NavigationRow(stringResource(R.string.reservation_accepted_terms), onTerms)
    }
}

/** Reads the nested simulated charge/refund without a separate financial repository.
 * @param state Complete saved aggregate or explicit read outcome.
 * @param onBack Returns to the retained overview.
 * @param onRetry Retries the sole aggregate source.
 * @param modifier Caller layout.
 */
@Composable
fun PaymentDetailsScreen(
    state: ReservationDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ReservationRecordScreen(
        R.string.reservation_payment,
        state,
        onBack,
        onRetry,
        modifier,
    ) { record ->
        val locale = LocalConfiguration.current.locales[0]
        Text(record.bookedCampsite.name, style = MaterialTheme.typography.titleMedium)
        DetailRow(stringResource(R.string.reservation_reference), record.id)
        DetailRow(
            stringResource(R.string.reservation_total_paid),
            formatCad(record.payment.amountCents, locale),
        )
        Text(stringResource(R.string.reservation_simulated_payment))
        DetailRow(
            stringResource(R.string.reservation_paid_at),
            reservationTime(record, record.payment.paidAt),
        )
        record.payment.refundedAt?.let { at ->
            Text(
                stringResource(R.string.reservation_full_refund),
                style = MaterialTheme.typography.titleMedium,
            )
            DetailRow(
                stringResource(R.string.reservation_refunded_amount),
                formatCad(record.payment.amountCents, locale),
            )
            DetailRow(stringResource(R.string.reservation_refunded_at), reservationTime(record, at))
        }
    }
}

/** Reads saved rules and only the exact accepted policy; unknown versions never show newer text.
 * @param state Complete saved aggregate or explicit read outcome.
 * @param onBack Returns to the retained overview.
 * @param onRetry Retries the sole aggregate source.
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
        Text(record.bookedCampsite.name, style = MaterialTheme.typography.titleMedium)
        DetailRow(
            stringResource(R.string.reservation_accepted_at),
            reservationTime(record, record.acceptedTerms.acceptedAt),
        )
        DetailRow(
            stringResource(R.string.reservation_policy_version),
            record.acceptedTerms.cancellationPolicyVersion,
        )
        Text(
            stringResource(R.string.campsite_rules_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            record.acceptedTerms.rules.ifBlank {
                stringResource(
                    R.string.reservation_no_saved_rules,
                )
            },
        )
        PolicyDocumentContent(
            policyContent(PolicyId.REFUND, record.acceptedTerms.cancellationPolicyVersion),
        )
    }
}

/** Hosts all saved children consistently, preserving their title/Back during failed reads.
 * @param title Localized child title.
 * @param state Shared parent's current aggregate outcome.
 * @param onBack Actual caller action for app-bar and system Back.
 * @param onRetry Explicit source retry.
 * @param modifier Caller layout.
 * @param content Child-specific facts within this host's sole scroll owner.
 */
@Composable
private fun ReservationRecordScreen(
    @StringRes title: Int,
    state: ReservationDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
    content: @Composable (Reservation) -> Unit,
) {
    BackHandler(onBack = onBack)
    AppScreen(stringResource(title), modifier, onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when (state) {
                ReservationDetailUiState.Loading -> {
                    LoadingContent()
                }

                ReservationDetailUiState.Failure -> {
                    FailureContent(
                        stringResource(R.string.reservation_load_failed),
                        onRetry,
                    )
                }

                ReservationDetailUiState.Unavailable -> {
                    Text(
                        stringResource(R.string.reservation_unavailable),
                    )
                }

                is ReservationDetailUiState.Content -> {
                    content(state.reservation)
                }
            }
        }
    }
}

/** Labels only actual confirmation/cancellation facts, without classifying clock-based lists.
 * @param record Complete saved aggregate.
 */
@Composable
internal fun reservationStatusLabel(record: Reservation): String =
    stringResource(
        when (record.cancellation?.initiatedAs) {
            null -> R.string.reservation_confirmed
            Role.OWNER -> R.string.reservation_cancelled_by_owner
            Role.ENTRANT -> R.string.reservation_cancelled_by_entrant
            Role.ADMIN -> R.string.reservation_cancelled_by_admin
        },
    )

/** Formats a historical instant in the booked campsite zone, never the device zone.
 * @param record Source of the preserved IANA zone.
 * @param instant Actual saved timestamp.
 */
@Composable
private fun reservationTime(
    record: Reservation,
    instant: Instant,
): String =
    formatDateTime(
        instant,
        ZoneId.of(record.bookedCampsite.timeZone),
        LocalConfiguration.current.locales[0],
    )

/** Supplies a passive complete record to main-source previews, without a fake backend. */
internal fun previewReservation(): Reservation {
    val at = Instant.parse("2027-04-21T15:00:00Z")
    return Reservation(
        "reservation-river",
        "may-weekends",
        "camper",
        "river",
        LocalDate.of(2027, 5, 14),
        LocalDate.of(2027, 5, 16),
        4,
        BookedCampsite("River Camp", "Near Jasper", Coordinates(53.0, -118.0), "America/Edmonton"),
        AcceptedTerms("Quiet hours after 10 pm.\nPack out all litter.", BookingPolicy.VERSION, at),
        at,
        Payment(13000, at),
    )
}

/** Supplies confirmed/refunded/history and source outcomes without acquiring repositories. */
class ReservationDetailPreviewProvider : PreviewParameterProvider<ReservationDetailUiState> {
    override val values: Sequence<ReservationDetailUiState> =
        run {
            val record = previewReservation()
            sequenceOf(
                ReservationDetailUiState.Content(record),
                ReservationDetailUiState.Content(
                    record.copy(
                        cancellation =
                            ReservationCancellation(
                                record.createdAt.plusSeconds(30),
                                "owner",
                                Role.OWNER,
                                "Campsite closure",
                            ),
                        payment =
                            record.payment.copy(
                                refundedAt = record.createdAt.plusSeconds(30),
                            ),
                    ),
                ),
                ReservationDetailUiState.Content(
                    record.copy(
                        acceptedTerms =
                            record.acceptedTerms.copy(
                                cancellationPolicyVersion = "retired-refund-v0",
                            ),
                    ),
                ),
                ReservationDetailUiState.Loading,
                ReservationDetailUiState.Failure,
                ReservationDetailUiState.Unavailable,
            )
        }
}

/** Previews the shared saved overview, including read and cancellation variants.
 * @param state Passive complete record or read outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun ReservationDetailPreview(
    @PreviewParameter(ReservationDetailPreviewProvider::class) state: ReservationDetailUiState,
) {
    CampsbridgeTheme { ReservationDetailScreen(state, {}, {}, {}, {}) }
}

/** Previews exact historical payment/refund facts and read outcomes.
 * @param state Passive complete record or read outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PaymentDetailsPreview(
    @PreviewParameter(ReservationDetailPreviewProvider::class) state: ReservationDetailUiState,
) {
    CampsbridgeTheme { PaymentDetailsScreen(state, {}, {}) }
}

/** Previews multiline rules, exact/unavailable policy and enlarged text.
 * @param state Passive complete record or read outcome.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun AcceptedTermsPreview(
    @PreviewParameter(ReservationDetailPreviewProvider::class) state: ReservationDetailUiState,
) {
    CampsbridgeTheme { AcceptedTermsScreen(state, {}, {}) }
}
