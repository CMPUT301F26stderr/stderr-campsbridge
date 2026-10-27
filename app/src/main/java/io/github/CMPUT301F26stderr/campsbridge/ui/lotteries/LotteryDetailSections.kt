package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components.CampsiteFacts
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.TagLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId

/** Uses one known campsite zone, with an explicitly labelled UTC display otherwise.
 * @param offers Current allocations and their already-read campsite records.
 */
internal fun lotteryDisplayZone(offers: LotteryOffersState): ZoneId =
    (offers as? LotteryOffersState.Content)
        ?.offers
        ?.mapNotNull { it.campsite?.details?.timeZone }
        ?.distinct()
        ?.singleOrNull()
        ?.let(ZoneId::of) ?: ZoneId.of("UTC")

/** Frames saved detail children without acquiring another ViewModel or reader.
 * @param title Localized child title.
 * @param state Shared parent's current read outcome.
 * @param onBack Returns within the actual saved graph.
 * @param onRetry Retries only the parent source.
 * @param modifier Caller layout.
 * @param bottomContent Optional bounded caller-owned actions.
 * @param content Saved facts, only rendered for current confirmed parent content.
 */
@Composable
internal fun LotteryDetailFrame(
    title: String,
    state: LotteryDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContent: @Composable () -> Unit = {},
    content: @Composable ColumnScope.(LotteryDetailUiState.Content) -> Unit,
) {
    AppScreen(title, modifier, onBack = onBack, bottomContent = bottomContent) {
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
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(
                            rememberScrollState(),
                        ).padding(bottom = Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) { content(state) }
            }
        }
    }
}

/** Groups real registration/booking windows and allocation rules for the same saved parent.
 * @param state Shared parent and independent campsite read.
 * @param onBack Returns to its retained overview.
 * @param onRetry Retries only the parent source.
 * @param onRetryOffers Retries only allocations/campsite reads needed for the display zone.
 */
@Composable
fun LotteryScheduleScreen(
    state: LotteryDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryOffers: () -> Unit,
) {
    LotteryDetailFrame(
        stringResource(R.string.lottery_schedule_title),
        state,
        onBack,
        onRetry,
    ) { current ->
        val record = current.lottery
        val configuration = record.configuration
        val zone = lotteryDisplayZone(current.offers)
        Text(record.title, style = MaterialTheme.typography.headlineSmall)
        if (record.description.isNotBlank()) Text(record.description)
        Text(
            stringResource(R.string.lottery_schedule_zone, zone.id),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (current.offers == LotteryOffersState.Failure) {
            FailureContent(stringResource(R.string.lottery_offers_failed), onRetryOffers)
        }
        LotteryWindowCard(
            stringResource(R.string.lottery_registration_step),
            configuration.registrationOpensAt,
            configuration.registrationClosesAt,
            zone,
        )
        LotteryWindowCard(
            stringResource(R.string.lottery_booking_step),
            configuration.bookingOpensAt,
            configuration.bookingClosesAt,
            zone,
        )
        SummarySection(stringResource(R.string.lottery_allocation_rules)) {
            DetailRow(
                stringResource(R.string.lottery_winners_step),
                configuration.winnerCount.toString(),
            )
            DetailRow(
                stringResource(R.string.lottery_registration_limit),
                configuration.waitlistCapacity?.toString()
                    ?: stringResource(R.string.lottery_unlimited),
            )
        }
    }
}

/** Presents exact allocations and current campsite facts with an optional real calendar route.
 * @param state Shared parent and independent offer result.
 * @param onBack Returns to the retained overview.
 * @param onRetry Retries only the parent source.
 * @param onRetryOffers Retries only allocations and referenced campsites.
 * @param onCampsite Registered saved availability reader, absent in isolated hosts.
 */
@Composable
fun LotteryOffersScreen(
    state: LotteryDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryOffers: () -> Unit,
    onCampsite: ((String) -> Unit)? = null,
) {
    LotteryDetailFrame(
        stringResource(R.string.lottery_campsites_title),
        state,
        onBack,
        onRetry,
    ) { current ->
        Text(current.lottery.title, style = MaterialTheme.typography.headlineSmall)
        when (val offers = current.offers) {
            LotteryOffersState.Loading -> {
                LoadingContent()
            }

            LotteryOffersState.Failure -> {
                FailureContent(stringResource(R.string.lottery_offers_failed), onRetryOffers)
            }

            is LotteryOffersState.Content -> {
                if (offers.offers.isEmpty()) Text(stringResource(R.string.lottery_no_offers))
                for (summary in offers.offers) {
                    val site = summary.campsite
                    SummarySection(
                        site?.details?.name ?: stringResource(
                            R.string.lottery_unavailable_campsite,
                        ),
                    ) {
                        if (site != null) {
                            Text(
                                site.details.locationLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CampsiteFacts(site)
                        } else {
                            Text(
                                stringResource(R.string.campsite_reference),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(summary.offer.campsiteId)
                        }
                        val count = summary.offer.nights.size
                        Text(
                            pluralStringResource(R.plurals.lottery_allocated_nights, count, count),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        val locale = LocalConfiguration.current.locales[0]
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                            verticalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            for (night in summary.offer.nights.sorted()) {
                                TagLabel(
                                    formatDate(night, locale),
                                )
                            }
                        }
                        if (site != null && onCampsite != null) {
                            TextButton({ onCampsite(summary.offer.campsiteId) }) {
                                Text(stringResource(R.string.lottery_view_calendar))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Keeps each saved time window together without second-precision or device-zone conversion.
 * @param title Localized window heading.
 * @param opening Saved opening instant.
 * @param closing Saved closing instant.
 * @param zone Known campsite zone or explicitly labelled UTC.
 */
@Composable
private fun LotteryWindowCard(
    title: String,
    opening: Instant,
    closing: Instant,
    zone: ZoneId,
) {
    val locale = LocalConfiguration.current.locales[0]
    SummarySection(title) {
        DetailRow(
            stringResource(R.string.lottery_window_opens),
            formatDateTime(opening, zone, locale),
        )
        DetailRow(
            stringResource(R.string.lottery_window_closes),
            formatDateTime(closing, zone, locale),
        )
    }
}
