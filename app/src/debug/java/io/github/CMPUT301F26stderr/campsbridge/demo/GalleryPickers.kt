package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.DateInputDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.DateRangeInputDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.PickerField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.pickers.TimeInputDialog
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Real Material picker dialogs; applied values change only on explicit confirmation. */
@Composable
internal fun GalleryPickers() {
    var date by remember { mutableStateOf(GalleryDate) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var start by remember { mutableStateOf(GalleryDate) }
    var end by remember { mutableStateOf(GalleryDate.plusDays(2)) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    PickerField(stringResource(R.string.gallery_opening_date), formatDate(date, locale), {
        dialog =
            "date"
    })
    PickerField(
        stringResource(
            R.string.gallery_opening_time,
        ),
        time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)),
        {
            dialog =
                "time"
        },
    )
    PickerField(
        stringResource(
            R.string.gallery_stay_dates,
        ),
        stringResource(R.string.stay_dates, formatDate(start, locale), formatDate(end, locale)),
        {
            dialog =
                "range"
        },
    )
    when (dialog) {
        "date" -> {
            DateInputDialog(date, {
                date = it
                dialog = null
            }, { dialog = null })
        }

        "range" -> {
            DateRangeInputDialog(start, end, { from, until ->
                start = from
                end = until
                dialog =
                    null
            }, { dialog = null })
        }

        "time" -> {
            TimeInputDialog(time, {
                time = it
                dialog = null
            }, { dialog = null })
        }
    }
}
