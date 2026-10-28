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
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingStep
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AttachmentImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.StaySummary
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
    if (start != null && end != null && end > start && guests != null && quote != null) {
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
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
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
        if (state.draft.agreement == null) {
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
        TextButton({ onQuote(quote.totalCents) }, enabled = enabled) {
            Text(
                stringResource(R.string.booking_approve_quote, formatCad(quote.totalCents, locale)),
            )
        }
    }
    if (state.quote == BookingQuote.Overflow) Text(stringResource(R.string.booking_quote_overflow))
    Text(stringResource(R.string.reservation_simulated_payment))
}
