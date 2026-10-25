package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps

import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
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
    DetailRow(stringResource(R.string.lottery_title), draft.title)
    if (state.errors.titleRequired) Text(stringResource(R.string.lottery_title_required))
    if (draft.description.isNotEmpty()) Text(draft.description)
    EditLotteryStep(LotteryStep.BASICS, R.string.lottery_basics, onEdit, enabled)
    Text(site?.details?.name ?: stringResource(R.string.lottery_site_required))
    val locale = LocalConfiguration.current.locales[0]
    for (night in draft.nights.sorted()) Text(formatDate(night, locale))
    val count = draft.nights.size
    Text(pluralStringResource(R.plurals.lottery_allocated_nights, count, count))
    if (state.errors.allocationRequired) Text(stringResource(R.string.lottery_allocation_required))
    EditLotteryStep(LotteryStep.SITE, R.string.lottery_site_step, onEdit, enabled)
    if (site != null) Text(site.details.timeZone)
    for (point in LotterySchedulePoint.entries) {
        DetailRow(lotteryScheduleLabel(point), localScheduleLabel(draft.schedule[point]))
        val scheduleError =
            when (state.errors.schedule[point]) {
                LotteryScheduleError.DATE_REQUIRED -> R.string.lottery_date_required
                LotteryScheduleError.TIME_REQUIRED -> R.string.lottery_time_required
                LotteryScheduleError.AMBIGUOUS_TIME -> R.string.lottery_time_ambiguous
                null -> null
            }
        if (scheduleError != null) Text(stringResource(scheduleError))
        if (point == LotterySchedulePoint.REGISTRATION_CLOSE) {
            EditLotteryStep(
                LotteryStep.REGISTRATION,
                R.string.lottery_registration_step,
                onEdit,
                enabled,
            )
        } else if (point == LotterySchedulePoint.BOOKING_CLOSE) {
            EditLotteryStep(LotteryStep.BOOKING, R.string.lottery_booking_step, onEdit, enabled)
        }
    }
    DetailRow(stringResource(R.string.lottery_winners_step), draft.maximumWinnersText)
    if (state.errors.winnerCount != null) Text(stringResource(R.string.lottery_winners_invalid))
    Text(stringResource(R.string.lottery_unlimited))
    EditLotteryStep(LotteryStep.WINNERS, R.string.lottery_winners_step, onEdit, enabled)
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
    TextButton({ onEdit(step) }, enabled = enabled) {
        Text(stringResource(R.string.lottery_edit_section, stringResource(title)))
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
