package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.DateInputDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.PickerField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.TimeInputDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryCampsiteChoices
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryLocalDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryScheduleError
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySchedulePoint
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupUiState
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Reuses one local date/time composition for the two distinct schedule steps.
 * @param state Current form and per-input feedback.
 * @param opening Actual registration or booking opening point.
 * @param closing Corresponding closing point.
 * @param initialMonth Explicit site's clock month when no date is selected.
 * @param onDate Updates one local date after native confirmation.
 * @param onTime Updates one local time after native confirmation.
 */
@Composable
fun LotteryWindowStep(
    state: LotterySetupUiState,
    opening: LotterySchedulePoint,
    closing: LotterySchedulePoint,
    initialMonth: YearMonth?,
    onDate: (LotterySchedulePoint, LocalDate) -> Unit,
    onTime: (LotterySchedulePoint, LocalTime) -> Unit,
) {
    val site =
        (state.choices as? LotteryCampsiteChoices.Content)
            ?.campsites
            ?.firstOrNull { it.id == state.draft.campsiteId }
    if (site == null) {
        Text(stringResource(R.string.lottery_selected_site_unavailable))
    } else {
        Text(site.details.timeZone)
    }
    for (point in listOf(opening, closing)) {
        Text(lotteryScheduleLabel(point), style = MaterialTheme.typography.titleMedium)
        LotteryScheduleField(
            point,
            state.draft.schedule[point] ?: LotteryLocalDateTime(),
            state.errors.schedule[point],
            initialMonth,
            site != null,
            { onDate(point, it) },
            { onTime(point, it) },
        )
    }
}

/** Native picker state stays local; only Confirm changes the parent's explicit input.
 * @param point Fixed schedule identity for distinct saved dialog state.
 * @param value Current raw date/time pair.
 * @param error Explicit field feedback.
 * @param initialMonth Selected site's clock month.
 * @param enabled Whether the actual selected site/zone is known.
 * @param onDate Confirms a local date.
 * @param onTime Confirms a local time.
 */
@Composable
private fun LotteryScheduleField(
    point: LotterySchedulePoint,
    value: LotteryLocalDateTime,
    error: LotteryScheduleError?,
    initialMonth: YearMonth?,
    enabled: Boolean,
    onDate: (LocalDate) -> Unit,
    onTime: (LocalTime) -> Unit,
) {
    var dateOpen by rememberSaveable(point) { mutableStateOf(false) }
    var timeOpen by rememberSaveable(point) { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val label = lotteryScheduleLabel(point)
    PickerField(
        stringResource(R.string.lottery_date_label, label),
        value.date?.let { formatDate(it, locale) },
        { dateOpen = true },
        enabled = enabled,
        errorMessage =
            if (error == LotteryScheduleError.DATE_REQUIRED) {
                stringResource(R.string.lottery_date_required)
            } else {
                null
            },
    )
    PickerField(
        stringResource(R.string.lottery_time_label, label),
        value.time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)),
        { timeOpen = true },
        enabled = enabled,
        errorMessage =
            when (error) {
                LotteryScheduleError.TIME_REQUIRED -> {
                    stringResource(R.string.lottery_time_required)
                }

                LotteryScheduleError.AMBIGUOUS_TIME -> {
                    stringResource(
                        R.string.lottery_time_ambiguous,
                    )
                }

                else -> {
                    null
                }
            },
    )
    if (dateOpen && enabled) {
        DateInputDialog(value.date, {
            onDate(it)
            dateOpen = false
        }, { dateOpen = false }, initialMonth)
    }
    if (timeOpen && enabled) {
        TimeInputDialog(value.time ?: LocalTime.of(9, 0), {
            onTime(it)
            timeOpen = false
        }, { timeOpen = false })
    }
}

/** Resolves one fixed schedule label shared by pickers and review rows.
 * @param point Actual local schedule field.
 */
@Composable
internal fun lotteryScheduleLabel(point: LotterySchedulePoint): String =
    stringResource(
        when (point) {
            LotterySchedulePoint.REGISTRATION_OPEN -> R.string.lottery_registration_opening
            LotterySchedulePoint.REGISTRATION_CLOSE -> R.string.lottery_registration_closing
            LotterySchedulePoint.BOOKING_OPEN -> R.string.lottery_booking_opening
            LotterySchedulePoint.BOOKING_CLOSE -> R.string.lottery_booking_closing
        },
    )
