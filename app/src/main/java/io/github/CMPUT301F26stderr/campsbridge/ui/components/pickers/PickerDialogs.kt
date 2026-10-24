package io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.dateToPickerMillis
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.common.pickerMillisToDate
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime

/** Native Material date selection; changes remain temporary until Confirm.
 * @param value Previously applied date.
 * @param onConfirm Applies a nonnull date.
 * @param onDismiss Cancels without applying edits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInputDialog(
    value: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberDatePickerState(initialSelectedDateMillis = value?.let(::dateToPickerMillis))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerMillisToDate(state.selectedDateMillis)?.let(onConfirm) },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state)
    }
}

/** Material range selection returning arrival and exclusive checkout, without inventory decisions.
 * @param start Previously applied arrival.
 * @param end Previously applied checkout, which must follow arrival.
 * @param onConfirm Applies a complete positive-length stay.
 * @param onDismiss Cancels without applying temporary changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeInputDialog(
    start: LocalDate?,
    end: LocalDate?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    require((start == null && end == null) || (start != null && end != null && end > start))
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = start?.let(::dateToPickerMillis),
            initialSelectedEndDateMillis = end?.let(::dateToPickerMillis),
        )
    val from = pickerMillisToDate(state.selectedStartDateMillis)
    val until = pickerMillisToDate(state.selectedEndDateMillis)
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(
            onClick = {
                if (from != null && until != null &&
                    until > from
                ) {
                    onConfirm(from, until)
                }
            },
            enabled = from != null && until != null && until > from,
        ) { Text(stringResource(R.string.confirm)) }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }) {
        DateRangePicker(
            state,
            modifier = Modifier.fillMaxWidth().height(500.dp),
            headline = { RangeHeadline(from, until) },
        )
    }
}

/** Keeps both complete dates readable inside the native range picker's compact header.
 * @param start Draft arrival, or null while awaiting selection.
 * @param end Draft checkout, or null while awaiting selection.
 */
@Composable
private fun RangeHeadline(
    start: LocalDate?,
    end: LocalDate?,
) {
    val locale = LocalConfiguration.current.locales[0]
    val choose = stringResource(R.string.choose_value)
    Column(Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small)) {
        Text(
            stringResource(R.string.arrival_date, start?.let { formatDate(it, locale) } ?: choose),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.checkout_date, end?.let { formatDate(it, locale) } ?: choose),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Material wall-clock selection; timezone conversion belongs to the caller.
 * @param value Previously applied time.
 * @param onConfirm Applies the selected time.
 * @param onDismiss Cancels without applying edits.
 * @param is24Hour Whether to use 24-hour presentation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInputDialog(
    value: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    is24Hour: Boolean = false,
) {
    val state = rememberTimePickerState(value.hour, value.minute, is24Hour)
    var keyboard by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_time)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (keyboard) TimeInput(state) else TimePicker(state)
                TextButton(onClick = { keyboard = !keyboard }) {
                    Text(
                        stringResource(if (keyboard) R.string.use_clock else R.string.use_keyboard),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(state.hour, state.minute))
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
