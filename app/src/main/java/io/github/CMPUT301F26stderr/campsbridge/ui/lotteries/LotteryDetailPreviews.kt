package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import java.time.Instant
import java.time.LocalDate

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
                                site,
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
                    participation = LotteryMembershipUiState(canJoin = true),
                ),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.REGISTRATION_OPEN,
                    membership = LotteryMembershipRead.Content(LotteryMembershipStatus.WITHDRAWN),
                    participation =
                        LotteryMembershipUiState(
                            canJoin = true,
                            action = LotteryMembershipAction.JOIN,
                        ),
                ),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.REGISTRATION_OPEN,
                    membership = LotteryMembershipRead.Content(LotteryMembershipStatus.WAITING),
                    participation =
                        LotteryMembershipUiState(
                            canLeave = true,
                            isConfirmingLeave = true,
                        ),
                ),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.REGISTRATION_OPEN,
                    membership = LotteryMembershipRead.Content(LotteryMembershipStatus.WITHDRAWN),
                    participation =
                        LotteryMembershipUiState(
                            canJoin = true,
                            error = LotteryMembershipActionError.FAILED,
                        ),
                ),
                content.copy(offers = LotteryOffersState.Content(emptyList())),
                content.copy(offers = LotteryOffersState.Failure),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.DRAW_PENDING,
                    canDraw = true,
                ),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.DRAW_PENDING,
                    canDraw = true,
                    isDrawing = true,
                ),
                content.copy(
                    lottery = record.copy(publishedAt = record.createdAt),
                    canContinue = false,
                    lifecycle = LotteryLifecycle.DRAW_PENDING,
                    canDraw = true,
                    drawFailed = true,
                ),
                LotteryDetailUiState.Loading,
                LotteryDetailUiState.Failure,
                LotteryDetailUiState.Unavailable,
            )
        }
}

/** Provides independent offered-site outcomes without repositories or successful fake writes. */
internal class LotteryOffersPreviewProvider : PreviewParameterProvider<LotteryDetailUiState> {
    override val values: Sequence<LotteryDetailUiState> =
        run {
            val normal =
                LotteryDetailPreviewProvider().values.elementAt(
                    1,
                ) as LotteryDetailUiState.Content
            val offer = (normal.offers as LotteryOffersState.Content).offers.single()
            val site = requireNotNull(offer.campsite)
            val longName = "Riverside camping beneath the mountains and the old pine forest"
            val longLocation = "Along the upper river valley near Jasper National Park, Alberta"
            sequenceOf(
                normal,
                normal.copy(offers = LotteryOffersState.Loading),
                normal.copy(offers = LotteryOffersState.Failure),
                normal.copy(offers = LotteryOffersState.Content(emptyList())),
                normal.copy(
                    offers = LotteryOffersState.Content(listOf(offer.copy(campsite = null))),
                ),
                normal.copy(
                    offers =
                        LotteryOffersState.Content(
                            listOf(
                                offer.copy(
                                    campsite =
                                        site.copy(
                                            details =
                                                site.details.copy(
                                                    name = longName,
                                                    locationLabel = longLocation,
                                                ),
                                        ),
                                ),
                            ),
                        ),
                ),
            )
        }
}

/** Shows the saved, site-local schedule without acquiring another reader. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LotterySchedulePreview() {
    CampsbridgeTheme {
        LotteryScheduleScreen(LotteryDetailPreviewProvider().values.elementAt(1), {}, {}, {})
    }
}

/** Shows explicit UTC fallback while the independent campsite read is unavailable. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LotteryScheduleUnavailableZonePreview() {
    val state = LotteryDetailPreviewProvider().values.elementAt(11)
    CampsbridgeTheme { LotteryScheduleScreen(state, {}, {}, {}) }
}

/** Shows confirmed allocation facts and independent empty/missing/failure states.
 * @param state Passive allocation variant; callbacks perform no reads or writes.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LotteryOffersPreview(
    @PreviewParameter(LotteryOffersPreviewProvider::class) state: LotteryDetailUiState,
) {
    CampsbridgeTheme { LotteryOffersScreen(state, {}, {}, {}, onCampsite = {}) }
}
