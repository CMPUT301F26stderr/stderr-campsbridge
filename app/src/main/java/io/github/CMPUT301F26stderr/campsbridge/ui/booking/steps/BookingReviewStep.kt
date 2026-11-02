package io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.temporal.ChronoUnit

/** Prototype 07.05/07.07/07.09 facts and explicit edits; creates no booking/payment records.
 * @param state Caller-owned stay, source facts, agreement and quote.
 * @param onEdit Requests the named wizard step.
 * @param onQuote Reports the displayed CAD cents for renewed approval.
 * @param onPolicy Opens the current packaged policy.
 * @param enabled Whether input actions are currently permitted.
 * @param photo Independently supplied current campsite image.
 * @param onRetryPhoto Retries only image presentation.
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
    val locale = LocalConfiguration.current.locales[0]
    val start = state.draft.checkIn
    val end = state.draft.checkOut
    val guests = state.guestCount
    val quote = state.quote as? BookingQuote.Value
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            AttachmentImage(
                photo,
                state.campsite.details.name,
                onRetryPhoto,
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium),
            )
            Text(stringResource(R.string.campsite), style = MaterialTheme.typography.labelMedium)
            Text(state.campsite.details.name, style = MaterialTheme.typography.titleLarge)
        }
    }
    val dates =
        if (start != null && end != null && end > start) {
            val nights = ChronoUnit.DAYS.between(start, end)
            stringResource(
                R.string.booking_dates_summary,
                stringResource(
                    R.string.stay_dates,
                    formatDate(start, locale),
                    formatDate(end, locale),
                ),
                pluralStringResource(
                    R.plurals.nights,
                    nights.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    nights,
                ),
            )
        } else {
            stringResource(R.string.booking_range_invalid)
        }
    ReviewSection(
        stringResource(R.string.dates),
        dates,
        stringResource(R.string.booking_edit_dates),
        { onEdit(BookingStep.DATES) },
        enabled,
        warning =
            if (state.error == BookingStepError.Dates(StaySelectionInput.UNAVAILABLE_NIGHTS)) {
                stringResource(R.string.booking_conflict)
            } else {
                null
            },
    )
    ReviewSection(
        stringResource(R.string.guests),
        if (guests != null && guests > 0) {
            pluralStringResource(
                R.plurals.guests,
                guests.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                guests,
            )
        } else {
            stringResource(R.string.booking_guests_required)
        },
        stringResource(R.string.booking_edit_guests),
        { onEdit(BookingStep.GUESTS) },
        enabled,
    )
    val accepted =
        state.draft.agreement ==
            BookingAgreement(state.campsite.details.rules, BookingPolicy.VERSION)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(
                stringResource(R.string.booking_terms_title),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                stringResource(
                    if (accepted) {
                        R.string.booking_rules_accepted
                    } else {
                        R.string.booking_rules_changed
                    },
                ),
                color =
                    if (accepted) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                TextButton({ onEdit(BookingStep.TERMS) }, enabled = enabled) {
                    Text(stringResource(R.string.booking_edit_terms))
                }
                TextButton(onPolicy, enabled = enabled) {
                    Text(stringResource(R.string.refund_policy_title))
                }
            }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            if (quote != null) {
                val changed = state.draft.reviewedTotalCents != quote.totalCents
                if (changed) {
                    Text(stringResource(R.string.booking_quote_changed))
                    state.draft.reviewedTotalCents?.let {
                        Text(stringResource(R.string.booking_previous_total, formatCad(it, locale)))
                    }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(
                            if (changed) R.string.booking_total_updated else R.string.total,
                        ),
                    )
                    Text(
                        formatCad(quote.totalCents, locale),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (changed) {
                    TextButton({ onQuote(quote.totalCents) }, enabled = enabled) {
                        Text(
                            stringResource(
                                R.string.booking_approve_quote,
                                formatCad(quote.totalCents, locale),
                            ),
                        )
                    }
                }
            } else {
                Text(
                    stringResource(
                        if (state.quote ==
                            BookingQuote.Overflow
                        ) {
                            R.string.booking_quote_overflow
                        } else {
                            R.string.booking_quote_missing
                        },
                    ),
                )
            }
            Text(
                stringResource(R.string.reservation_simulated_payment),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** A wrapped review fact/edit card that also supports inline date-conflict feedback.
 * @param title Fact label.
 * @param value Supplied formatted fact.
 * @param actionLabel Explicit edit destination.
 * @param onAction Parent-owned edit callback.
 * @param enabled Whether editing is permitted.
 * @param warning Optional current conflict feedback.
 */
@Composable
private fun ReviewSection(
    title: String,
    value: String,
    actionLabel: String,
    onAction: () -> Unit,
    enabled: Boolean,
    warning: String? = null,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(title, style = MaterialTheme.typography.labelMedium)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onAction, enabled = enabled) { Text(actionLabel) }
            }
            warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
