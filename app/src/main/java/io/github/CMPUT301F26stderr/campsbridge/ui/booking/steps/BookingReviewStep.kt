package io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingAgreement
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingStep
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingStepError
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AttachmentImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.StaySummary
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Prototype 07.05 with the same draft, explicit changed-price approval and real edit links.
 * @param state Current stay and approval facts; this content never creates payment records.
 * @param onEdit Returns to a step under this parent.
 * @param onQuote Approves the exact amount currently displayed.
 * @param onPolicy Opens the full current policy without altering agreement.
 * @param enabled Whether draft controls and navigation are permitted.
 * @param photo Independent current campsite image read.
 * @param onRetryPhoto Retries only the failed image source.
 */
@Composable
fun BookingReviewStep(
    state: BookingUiState.Content,
    onEdit: (BookingStep) -> Unit,
    onQuote: (Long) -> Unit,
    onPolicy: () -> Unit,
    enabled: Boolean,
    photo: ImageSlotState,
    onRetryPhoto: () -> Unit,
) {
    val start = state.draft.checkIn
    val end = state.draft.checkOut
    val guests = state.guestCount
    val quote = state.quote as? BookingQuote.Value
    if (start != null && end != null && end > start && guests != null && guests > 0 && quote != null) {
        StaySummary(
            state.campsite.details.name,
            start,
            end,
            guests,
            quote.totalCents,
            image = {
                AttachmentImage(
                    photo,
                    state.campsite.details.name,
                    onRetryPhoto,
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium),
                )
            },
        )
    } else {
        SummarySection(state.campsite.details.name) {
            val locale = LocalConfiguration.current.locales[0]
            Text(
                if (start != null && end != null && end > start) {
                    stringResource(R.string.stay_dates, formatDate(start, locale), formatDate(end, locale))
                } else {
                    stringResource(R.string.booking_range_invalid)
                },
            )
            Text(
                if (guests != null && guests > 0) {
                    pluralStringResource(R.plurals.guests, guests.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), guests)
                } else {
                    stringResource(R.string.booking_guests_required)
                },
            )
            if (quote != null) {
                DetailRow(stringResource(R.string.total), formatCad(quote.totalCents, locale))
            } else {
                Text(
                    stringResource(
                        if (state.quote == BookingQuote.Overflow) {
                            R.string.booking_quote_overflow
                        } else {
                            R.string.booking_quote_missing
                        },
                    ),
                )
            }
        }
    }
    if (state.error == BookingStepError.Dates(StaySelectionInput.UNAVAILABLE_NIGHTS)) {
        Text(stringResource(R.string.booking_conflict), color = MaterialTheme.colorScheme.error)
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        TextButton({ onEdit(BookingStep.DATES) }, enabled = enabled) {
            Text(stringResource(R.string.booking_edit_dates))
        }
        TextButton({ onEdit(BookingStep.GUESTS) }, enabled = enabled) {
            Text(stringResource(R.string.booking_edit_guests))
        }
        TextButton({ onEdit(BookingStep.TERMS) }, enabled = enabled) {
            Text(stringResource(R.string.booking_edit_terms))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        if (state.draft.agreement != BookingAgreement(state.campsite.details.rules, BookingPolicy.VERSION)) {
            Text(
                stringResource(R.string.booking_rules_changed),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(stringResource(R.string.booking_rules_accepted))
        }
        TextButton(
            onPolicy,
            enabled = enabled,
        ) { Text(stringResource(R.string.refund_policy_title)) }
    }
    if (quote != null && state.draft.reviewedTotalCents != quote.totalCents) {
        Text(stringResource(R.string.booking_quote_changed))
        val locale = LocalConfiguration.current.locales[0]
        state.draft.reviewedTotalCents?.let {
            Text(stringResource(R.string.booking_previous_total, formatCad(it, locale)))
        }
        TextButton({ onQuote(quote.totalCents) }, enabled = enabled) {
            Text(
                stringResource(R.string.booking_approve_quote, formatCad(quote.totalCents, locale)),
            )
        }
    }
    Text(stringResource(R.string.booking_payment_notice))
}
