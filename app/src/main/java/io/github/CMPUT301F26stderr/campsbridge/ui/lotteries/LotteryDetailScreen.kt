package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Connects the context-qualified saved reader to its caller and source retries.
 * @param model Actual saved-record owner.
 * @param onBack Returns to the retained caller.
 * @param onContinue Opens setup for this saved identity.
 */
@Composable
fun LotteryDetailRoute(
    model: LotteryDetailViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED) model.refreshTime()
    }
    LotteryDetailScreen(
        state,
        onBack,
        model::retry,
        model::retryOffers,
        onContinue,
        model::retryMembership,
        model::retryOpportunity,
        model::join,
        model::requestLeave,
        model::dismissLeave,
        model::confirmLeave,
    )
}

/**
 * Shared owner/entrant overview of confirmed saved facts and independent participation reads.
 * @param state Saved record and independent allocation, membership and reservation outcomes.
 * @param onBack Returns to the actual caller.
 * @param onRetry Retries the parent observation.
 * @param onRetryOffers Retries allocations and referenced site labels.
 * @param onContinue Reopens this active unpublished draft by ID.
 * @param onRetryMembership Retries the independent own-entry source.
 * @param onRetryOpportunity Reconciles unknown reservation consumption.
 * @param onJoin Joins or rejoins an eligible waitlist.
 * @param onRequestLeave Opens withdrawal confirmation.
 * @param onDismissLeave Dismisses an unsubmitted withdrawal.
 * @param onConfirmLeave Submits an explicitly confirmed withdrawal.
 * @param modifier Caller layout.
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
    modifier: Modifier = Modifier,
) {
    AppScreen(stringResource(R.string.lottery_detail_title), modifier, onBack = onBack) {
        when (state) {
            LotteryDetailUiState.Loading -> {
                LoadingContent()
            }

            LotteryDetailUiState.Failure -> {
                FailureContent(stringResource(R.string.lottery_load_failed), onRetry)
            }

            LotteryDetailUiState.Unavailable -> {
                Text(stringResource(R.string.lottery_unavailable))
            }

            is LotteryDetailUiState.Content -> {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    val record = state.lottery
                    Text(record.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(
                            when {
                                record.cancellation != null -> R.string.lottery_cancelled_status
                                record.publishedAt != null -> R.string.lottery_published_status
                                else -> R.string.lottery_draft_status
                            },
                        ),
                    )
                    val duplicatePending =
                        state.lifecycle == LotteryLifecycle.DRAW_PENDING &&
                            (state.membership as? LotteryMembershipRead.Content)?.status ==
                            LotteryMembershipStatus.RESULTS_PENDING
                    if (state.lifecycle != LotteryLifecycle.DRAFT &&
                        state.lifecycle != LotteryLifecycle.CANCELLED && !duplicatePending
                    ) {
                        Text(lotteryLifecycleLabel(state.lifecycle))
                    }
                    if (record.description.isNotEmpty()) Text(record.description)
                    when (val membership = state.membership) {
                        LotteryMembershipRead.NotApplicable -> {
                            Unit
                        }

                        LotteryMembershipRead.Loading -> {
                            Text(
                                stringResource(R.string.lottery_membership_loading),
                            )
                        }

                        LotteryMembershipRead.Failure -> {
                            FailureContent(
                                stringResource(R.string.lottery_membership_failed),
                                onRetryMembership,
                            )
                        }

                        is LotteryMembershipRead.Content -> {
                            Text(
                                lotteryMembershipLabel(membership.status),
                            )
                        }
                    }
                    when (val opportunity = state.opportunity) {
                        LotteryOpportunityRead.NotApplicable -> {
                            Unit
                        }

                        LotteryOpportunityRead.Loading -> {
                            Text(stringResource(R.string.lottery_opportunity_loading))
                        }

                        LotteryOpportunityRead.Failure -> {
                            FailureContent(
                                stringResource(R.string.lottery_opportunity_failed),
                                onRetryOpportunity,
                            )
                        }

                        LotteryOpportunityRead.Unused -> {
                            Text(stringResource(R.string.lottery_opportunity_unused))
                        }

                        is LotteryOpportunityRead.Reserved -> {
                            Text(
                                stringResource(
                                    R.string.lottery_saved_reservation,
                                    opportunity.reservation.id,
                                ),
                            )
                        }

                        is LotteryOpportunityRead.Unavailable -> {
                            Text(stringResource(R.string.lottery_saved_reservation_unavailable))
                            Text(
                                stringResource(
                                    R.string.lottery_saved_reservation,
                                    opportunity.reservationId,
                                ),
                            )
                        }
                    }
                    LotteryMembershipActions(
                        state,
                        onJoin,
                        onRequestLeave,
                        onDismissLeave,
                        onConfirmLeave,
                    )
                    if (state.canContinue) {
                        AppActionButton(
                            stringResource(R.string.lottery_continue_setup),
                            onContinue,
                        )
                    }
                    DetailRow(
                        stringResource(R.string.lottery_winners_step),
                        record.configuration.winnerCount.toString(),
                    )
                    Text(stringResource(R.string.lottery_unlimited))
                    when (val offers = state.offers) {
                        LotteryOffersState.Loading -> {
                            LoadingContent()
                        }

                        LotteryOffersState.Failure -> {
                            FailureContent(
                                stringResource(R.string.lottery_offers_failed),
                                onRetryOffers,
                            )
                        }

                        is LotteryOffersState.Content -> {
                            if (offers.offers.isEmpty()) {
                                Text(
                                    stringResource(R.string.lottery_no_offers),
                                )
                            }
                            for (summary in offers.offers) {
                                Text(
                                    summary.name
                                        ?: stringResource(
                                            R.string.lottery_missing_campsite,
                                            summary.offer.campsiteId,
                                        ),
                                )
                                val locale = LocalConfiguration.current.locales[0]
                                for (night in summary.offer.nights) Text(formatDate(night, locale))
                                val count = summary.offer.nights.size
                                Text(
                                    pluralStringResource(
                                        R.plurals.lottery_allocated_nights,
                                        count,
                                        count,
                                    ),
                                )
                            }
                        }
                    }
                    val known = state.offers as? LotteryOffersState.Content
                    val zone =
                        known
                            ?.offers
                            ?.mapNotNull { it.timeZone }
                            ?.distinct()
                            ?.singleOrNull()
                            ?.let(ZoneId::of) ?: ZoneId.of("UTC")
                    val format =
                        DateTimeFormatter
                            .ofLocalizedDateTime(FormatStyle.MEDIUM)
                            .withLocale(LocalConfiguration.current.locales[0])
                            .withZone(zone)
                    Text(zone.id)
                    DetailRow(
                        stringResource(R.string.lottery_registration_step),
                        stringResource(
                            R.string.lottery_window_range,
                            format.format(record.configuration.registrationOpensAt),
                            format.format(record.configuration.registrationClosesAt),
                        ),
                    )
                    DetailRow(
                        stringResource(R.string.lottery_booking_step),
                        stringResource(
                            R.string.lottery_window_range,
                            format.format(record.configuration.bookingOpensAt),
                            format.format(record.configuration.bookingClosesAt),
                        ),
                    )
                }
            }
        }
    }
}

/** Localizes only confirmed or explicitly pending own participation facts.
 * @param status Display-safe status supplied by the controller.
 */
@Composable
private fun lotteryMembershipLabel(status: LotteryMembershipStatus): String =
    stringResource(
        when (status) {
            LotteryMembershipStatus.NOT_JOINED -> R.string.lottery_not_joined
            LotteryMembershipStatus.WAITING -> R.string.lottery_membership_waiting
            LotteryMembershipStatus.WITHDRAWN -> R.string.lottery_membership_withdrawn
            LotteryMembershipStatus.RESULTS_PENDING -> R.string.lottery_draw_pending
            LotteryMembershipStatus.WON -> R.string.lottery_membership_won
            LotteryMembershipStatus.LOST -> R.string.lottery_membership_lost
            LotteryMembershipStatus.DECLINED -> R.string.lottery_membership_declined
        },
    )

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

/** Supplies real-shaped saved content and read outcomes without constructing repositories. */
class LotteryDetailPreviewProvider : PreviewParameterProvider<LotteryDetailUiState> {
    override val values: Sequence<LotteryDetailUiState> =
        run {
            val site = previewCampsite()
            val record =
                Lottery(
                    "may-weekends",
                    "owner",
                    "May weekends 2027",
                    "Quiet riverside camping for the long weekend.",
                    LotteryConfiguration(
                        Instant.parse("2027-05-05T15:00:00Z"),
                        Instant.parse("2027-05-06T15:00:00Z"),
                        Instant.parse("2027-05-07T15:00:00Z"),
                        Instant.parse("2027-05-08T15:00:00Z"),
                        2,
                        null,
                    ),
                    0,
                    Instant.parse("2027-05-01T12:00:00Z"),
                )
            val offer =
                LotteryOffer(
                    site.id,
                    listOf(LocalDate.of(2027, 5, 14)),
                )
            val content =
                LotteryDetailUiState.Content(
                    record,
                    LotteryOffersState.Content(
                        listOf(
                            LotteryOfferSummary(
                                offer,
                                site.details.name,
                                site.details.timeZone,
                            ),
                        ),
                    ),
                    canContinue = true,
                    lifecycle = lotteryLifecycle(record, record.createdAt),
                )
            sequenceOf(
                content,
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.UPCOMING,
                ),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.DRAW_PENDING,
                    membership =
                        LotteryMembershipRead.Content(
                            LotteryMembershipStatus.RESULTS_PENDING,
                        ),
                ),
                content.copy(
                    lottery =
                        record.copy(
                            publishedAt = record.createdAt,
                            drawnAt = record.createdAt,
                        ),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.BOOKING_OPEN,
                    membership = LotteryMembershipRead.Content(LotteryMembershipStatus.WON),
                    opportunity = LotteryOpportunityRead.Unused,
                ),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.REGISTRATION_OPEN,
                    membership = LotteryMembershipRead.Failure,
                ),
                content.copy(
                    lottery =
                        record.copy(
                            publishedAt = record.createdAt,
                            drawnAt = record.createdAt,
                        ),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.BOOKING_OPEN,
                    membership = LotteryMembershipRead.Content(LotteryMembershipStatus.WON),
                    opportunity = LotteryOpportunityRead.Failure,
                ),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.REGISTRATION_OPEN,
                    membership = LotteryMembershipRead.Content(LotteryMembershipStatus.NOT_JOINED),
                    canJoin = true,
                ),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.REGISTRATION_OPEN,
                    membership = LotteryMembershipRead.Content(LotteryMembershipStatus.WITHDRAWN),
                    canJoin = true,
                    membershipAction = LotteryMembershipAction.JOIN,
                ),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.REGISTRATION_OPEN,
                    membership = LotteryMembershipRead.Content(LotteryMembershipStatus.WAITING),
                    canLeave = true,
                    isConfirmingLeave = true,
                ),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.REGISTRATION_OPEN,
                    membership = LotteryMembershipRead.Content(LotteryMembershipStatus.WITHDRAWN),
                    canJoin = true,
                    membershipError = LotteryMembershipActionError.FAILED,
                ),
                content.copy(offers = LotteryOffersState.Content(emptyList())),
                content.copy(offers = LotteryOffersState.Failure),
                LotteryDetailUiState.Loading,
                LotteryDetailUiState.Failure,
                LotteryDetailUiState.Unavailable,
            )
        }
}

/** Passive themed previews cover content, empty allocation and independent read failures.
 * @param state Supplied actual-shaped saved outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun LotteryDetailPreview(
    @PreviewParameter(LotteryDetailPreviewProvider::class) state: LotteryDetailUiState,
) {
    CampsbridgeTheme { LotteryDetailScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}
