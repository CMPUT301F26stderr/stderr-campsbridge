package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.TagLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Saved owner/entrant overview; detailed facts live in registered read-only children.
 * @param state Confirmed parent, independent reads and current command feedback.
 * @param onBack Returns to the actual caller.
 * @param onRetry Retries the parent observation.
 * @param onRetryOffers Retries only allocation/campsite reads.
 * @param onContinue Reopens the active unpublished draft by ID.
 * @param onRetryMembership Retries only the own-entry source.
 * @param onRetryOpportunity Reconciles unknown reservation consumption.
 * @param onJoin Joins or rejoins an eligible waitlist.
 * @param onRequestLeave Opens withdrawal confirmation.
 * @param onDismissLeave Dismisses an unsubmitted withdrawal.
 * @param onConfirmLeave Submits the confirmed withdrawal.
 * @param onDraw Invokes the eligible owner fallback or explicit retry.
 * @param modifier Caller layout.
 * @param onParticipants Registered owner participant child, hidden for drafts.
 * @param onReservation Opens the confirmed consuming reservation.
 * @param onBooking Opens the registered single-site winner flow.
 * @param onOpenSection Registered detail children, absent in isolated examples.
 */
@Composable
fun LotteryDetailScreen(
    state: LotteryDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryOffers: () -> Unit,
    onContinue: () -> Unit,
    onRetryMembership: () -> Unit,
    onRetryOpportunity: () -> Unit,
    onJoin: () -> Unit,
    onRequestLeave: () -> Unit,
    onDismissLeave: () -> Unit,
    onConfirmLeave: () -> Unit,
    onDraw: () -> Unit,
    modifier: Modifier = Modifier,
    onParticipants: (() -> Unit)? = null,
    onReservation: ((String) -> Unit)? = null,
    onBooking: ((String, String) -> Unit)? = null,
    onOpenSection: ((LotteryDetailSection) -> Unit)? = null,
) {
    val content = state as? LotteryDetailUiState.Content
    val bookingOffer =
        content
            ?.takeIf {
                it.opportunity == LotteryOpportunityRead.Unused &&
                    it.lifecycle == LotteryLifecycle.BOOKING_OPEN && onBooking != null
            }?.let { (it.offers as? LotteryOffersState.Content)?.offers?.singleOrNull() }
    val reservation =
        (content?.opportunity as? LotteryOpportunityRead.Reserved)
            ?.reservation
            ?.takeIf { onReservation != null }
    val participation = content?.participation
    val hasActions =
        content != null && (
            bookingOffer != null || reservation != null || content.canContinue || content.canDraw ||
                content.isDrawing || content.drawFailed || participation?.canJoin == true ||
                participation?.canLeave == true || participation?.action != null ||
                participation?.error != null || participation?.isConfirmingLeave == true
        )
    LotteryDetailFrame(
        stringResource(R.string.lottery_detail_title),
        state,
        onBack,
        onRetry,
        modifier,
        bottomContent = {
            if (content != null && hasActions) {
                ActionFooter {
                    if (bookingOffer != null && onBooking != null) {
                        AppActionButton(stringResource(R.string.booking_title), {
                            onBooking(content.lottery.id, bookingOffer.offer.campsiteId)
                        }, Modifier.fillMaxWidth())
                    }
                    if (reservation != null && onReservation != null) {
                        AppActionButton(stringResource(R.string.reservation_view), {
                            onReservation(reservation.id)
                        }, Modifier.fillMaxWidth())
                    }
                    LotteryMembershipActions(
                        content.participation,
                        onJoin,
                        onRequestLeave,
                        onDismissLeave,
                        onConfirmLeave,
                    )
                    LotteryDrawActions(content, onDraw)
                    if (content.canContinue) {
                        AppActionButton(
                            stringResource(R.string.lottery_continue_setup),
                            onContinue,
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ) { current ->
        Text(current.lottery.title, style = MaterialTheme.typography.headlineSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            if (current.lottery.publishedAt != null &&
                current.lifecycle != LotteryLifecycle.CANCELLED
            ) {
                TagLabel(stringResource(R.string.lottery_published_status))
            }
            TagLabel(lotteryLifecycleLabel(current.lifecycle))
        }
        val deadline = lotteryDeadline(current.lottery, current.lifecycle)
        if (deadline != null) {
            Text(
                stringResource(
                    when (current.lifecycle) {
                        LotteryLifecycle.UPCOMING -> R.string.lottery_registration_starts
                        LotteryLifecycle.REGISTRATION_OPEN -> R.string.lottery_registration_ends
                        LotteryLifecycle.AWAITING_BOOKING -> R.string.lottery_booking_starts
                        else -> R.string.lottery_booking_deadline
                    },
                    formatDateTime(
                        deadline,
                        lotteryDisplayZone(current.offers),
                        LocalConfiguration.current.locales[0],
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val pendingIsInHeading =
            current.lifecycle == LotteryLifecycle.DRAW_PENDING &&
                (current.membership as? LotteryMembershipRead.Content)?.status ==
                LotteryMembershipStatus.RESULTS_PENDING
        if (!pendingIsInHeading) LotteryMembershipReadContent(current.membership, onRetryMembership)
        LotteryOpportunityContent(current.opportunity, onRetryOpportunity)
        if (current.offers == LotteryOffersState.Failure) {
            FailureContent(stringResource(R.string.lottery_offers_failed), onRetryOffers)
        } else if ((current.offers as? LotteryOffersState.Content)?.offers?.isEmpty() == true) {
            Text(stringResource(R.string.lottery_no_offers))
        }
        Column {
            if (onOpenSection != null) {
                NavigationRow(stringResource(R.string.lottery_schedule_title), {
                    onOpenSection(LotteryDetailSection.SCHEDULE)
                })
                NavigationRow(stringResource(R.string.lottery_campsites_title), {
                    onOpenSection(LotteryDetailSection.CAMPSITES)
                })
            }
            if (onParticipants != null && current.lottery.publishedAt != null) {
                NavigationRow(stringResource(R.string.lottery_participants), onParticipants)
            }
        }
    }
}

/** Keeps consumption knowledge separate from the footer's currently registered actions.
 * @param opportunity Server-confirmed reservation outcome.
 * @param onRetry Retries only a failed reservation source.
 */
@Composable
private fun LotteryOpportunityContent(
    opportunity: LotteryOpportunityRead,
    onRetry: () -> Unit,
) {
    when (opportunity) {
        LotteryOpportunityRead.NotApplicable -> {
            Unit
        }

        LotteryOpportunityRead.Loading -> {
            Text(stringResource(R.string.lottery_opportunity_loading))
        }

        LotteryOpportunityRead.Failure -> {
            FailureContent(stringResource(R.string.lottery_opportunity_failed), onRetry)
        }

        LotteryOpportunityRead.Unused -> {
            Text(stringResource(R.string.lottery_opportunity_unused))
        }

        is LotteryOpportunityRead.Reserved -> {
            Text(stringResource(R.string.lottery_saved_reservation, opportunity.reservation.id))
        }

        is LotteryOpportunityRead.Unavailable -> {
            Text(stringResource(R.string.lottery_saved_reservation_unavailable))
            Text(stringResource(R.string.lottery_saved_reservation, opportunity.reservationId))
        }
    }
}

/** Localizes derived lifecycle facts for shared list/detail presentation.
 * @param lifecycle Explicit controller-derived phase.
 */
@Composable
internal fun lotteryLifecycleLabel(lifecycle: LotteryLifecycle): String =
    stringResource(
        when (lifecycle) {
            LotteryLifecycle.DRAFT -> R.string.lottery_draft_status
            LotteryLifecycle.UPCOMING -> R.string.lottery_upcoming
            LotteryLifecycle.REGISTRATION_OPEN -> R.string.lottery_registration_open
            LotteryLifecycle.DRAW_PENDING -> R.string.lottery_draw_pending
            LotteryLifecycle.AWAITING_BOOKING -> R.string.lottery_awaiting_booking
            LotteryLifecycle.BOOKING_OPEN -> R.string.lottery_booking_open
            LotteryLifecycle.CLOSED -> R.string.lottery_closed
            LotteryLifecycle.CANCELLED -> R.string.lottery_cancelled_status
        },
    )

/** Shows real context actions and read states without constructing repositories.
 * @param state Passive saved variant; callbacks cannot read or write.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun LotteryDetailPreview(
    @PreviewParameter(LotteryDetailPreviewProvider::class) state: LotteryDetailUiState,
) {
    val content = state as? LotteryDetailUiState.Content
    val owner = content?.membership == LotteryMembershipRead.NotApplicable
    CampsbridgeTheme {
        LotteryDetailScreen(
            state,
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            onParticipants = if (owner) ({}) else null,
            onReservation = if (owner) null else ({}),
            onBooking = if (owner) null else ({ _, _ -> }),
            onOpenSection = {},
        )
    }
}
