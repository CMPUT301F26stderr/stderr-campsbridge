package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryAllocatedNights
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryCampsiteChoices
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryLocalDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryScheduleError
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySchedulePoint
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryStep
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Reviews the single allocation, actual local windows and their explicitly chosen zone.
 * @param state Current raw input, derived configuration and validation outcomes.
 * @param onEdit Returns to a named step within this parent.
 * @param enabled Whether input may change while a save owns the parent.
 * @param onConfirmZone Accepts all displayed local times in a changed campsite zone.
 */
@Composable
fun LotteryReviewStep(
    state: LotterySetupUiState,
    onEdit: (LotteryStep) -> Unit,
    onConfirmZone: () -> Unit,
    enabled: Boolean = true,
) {
    val draft = state.draft
    val site =
        (state.choices as? LotteryCampsiteChoices.Content)
            ?.campsites
            ?.firstOrNull { it.id == draft.campsiteId }
    SummarySection(
        stringResource(R.string.lottery_basics),
        action = { EditLotteryStep(LotteryStep.BASICS, R.string.lottery_basics, onEdit, enabled) },
    ) {
        Text(draft.title, style = MaterialTheme.typography.bodyLarge)
        if (state.errors.titleRequired) {
            Text(
                stringResource(R.string.lottery_title_required),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (draft.description.isNotEmpty()) Text(draft.description)
    }
    SummarySection(
        stringResource(R.string.lottery_site_step),
        action = { EditLotteryStep(LotteryStep.SITE, R.string.lottery_site_step, onEdit, enabled) },
    ) {
        Text(site?.details?.name ?: stringResource(R.string.lottery_site_required))
        LotteryAllocatedNights(draft.nights)
        if (state.errors.allocationRequired) {
            Text(
                stringResource(R.string.lottery_allocation_required),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (site != null) {
        Text(
            stringResource(R.string.lottery_schedule_zone, site.details.timeZone),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    LotteryReviewWindow(
        R.string.lottery_registration_step,
        LotteryStep.REGISTRATION,
        listOf(LotterySchedulePoint.REGISTRATION_OPEN, LotterySchedulePoint.REGISTRATION_CLOSE),
        state,
        onEdit,
        enabled,
    )
    LotteryReviewWindow(
        R.string.lottery_booking_step,
        LotteryStep.BOOKING,
        listOf(LotterySchedulePoint.BOOKING_OPEN, LotterySchedulePoint.BOOKING_CLOSE),
        state,
        onEdit,
        enabled,
    )
    SummarySection(
        stringResource(R.string.lottery_winners_title),
        action = {
            EditLotteryStep(
                LotteryStep.WINNERS,
                R.string.lottery_winners_step,
                onEdit,
                enabled,
            )
        },
    ) {
        DetailRow(stringResource(R.string.lottery_winners_step), draft.maximumWinnersText)
        if (state.errors.winnerCount != null) {
            Text(
                stringResource(R.string.lottery_winners_invalid),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(stringResource(R.string.lottery_unlimited))
    }
    if (state.errors.zoneConfirmationRequired && site != null) {
        Text(stringResource(R.string.lottery_zone_review, site.details.timeZone))
        TextButton(onConfirmZone, enabled = enabled) {
            Text(stringResource(R.string.lottery_confirm_zone, site.details.timeZone))
        }
    }
    if (state.errors.windowsPending) Text(stringResource(R.string.lottery_window_pending))
    val windowError =
        when (state.errors.windows) {
            LotteryWindowInput.InvalidRegistration -> {
                R.string.lottery_registration_invalid
            }

            LotteryWindowInput.BookingBeforeRegistrationClose -> {
                R.string.lottery_booking_before_registration
            }

            LotteryWindowInput.InvalidBooking -> {
                R.string.lottery_booking_invalid
            }

            else -> {
                null
            }
        }
    if (windowError != null) Text(stringResource(windowError))
}

/** Keeps the raw local window and its existing named edit action in one review group.
 * @param title Localized window section.
 * @param step Existing wizard destination for editing this window.
 * @param points Opening and closing raw field identities in display order.
 * @param state Actual input and per-field feedback.
 * @param onEdit Opens the requested step in the same parent.
 * @param enabled Whether the parent currently permits editing.
 */
@Composable
private fun LotteryReviewWindow(
    @StringRes title: Int,
    step: LotteryStep,
    points: List<LotterySchedulePoint>,
    state: LotterySetupUiState,
    onEdit: (LotteryStep) -> Unit,
    enabled: Boolean,
) {
    SummarySection(
        stringResource(title),
        action = { EditLotteryStep(step, title, onEdit, enabled) },
    ) {
        for (point in points) {
            DetailRow(lotteryScheduleLabel(point), localScheduleLabel(state.draft.schedule[point]))
            val error =
                when (state.errors.schedule[point]) {
                    LotteryScheduleError.DATE_REQUIRED -> R.string.lottery_date_required
                    LotteryScheduleError.TIME_REQUIRED -> R.string.lottery_time_required
                    LotteryScheduleError.AMBIGUOUS_TIME -> R.string.lottery_time_ambiguous
                    null -> null
                }
            if (error != null) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Labels review edits by their destination instead of repeated ambiguous Edit buttons.
 * @param step Target step in the same parent.
 * @param title Localized section label resource.
 * @param onEdit Opens the requested local step.
 * @param enabled Whether this parent permits editing.
 */
@Composable
private fun EditLotteryStep(
    step: LotteryStep,
    @StringRes title: Int,
    onEdit: (LotteryStep) -> Unit,
    enabled: Boolean,
) {
    IconButton({ onEdit(step) }, enabled = enabled) {
        Icon(
            Icons.Outlined.Edit,
            contentDescription =
                stringResource(
                    R.string.lottery_edit_section,
                    stringResource(title),
                ),
            tint =
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
        )
    }
}

/** Formats raw explicit local fields without silently resolving a DST-invalid value.
 * @param input Current date/time pair, including incomplete fields.
 */
@Composable
private fun localScheduleLabel(input: LotteryLocalDateTime?): String {
    val locale = LocalConfiguration.current.locales[0]
    val absent = stringResource(R.string.choose_value)
    val date = input?.date?.let { formatDate(it, locale) } ?: absent
    val time =
        input?.time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
            ?: absent
    return stringResource(R.string.lottery_schedule_value, date, time)
}
