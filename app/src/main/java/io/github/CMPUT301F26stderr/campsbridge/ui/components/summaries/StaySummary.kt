package io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Shared draft/confirmed stay facts without implying booking or payment success.
 * @param name Campsite display name.
 * @param checkIn Inclusive local arrival date.
 * @param checkOut Exclusive local departure date.
 * @param guests Positive party size.
 * @param totalCents Nonnegative quoted or recorded total.
 * @param modifier Caller layout.
 */
@Composable
fun StaySummary(
    name: String,
    checkIn: LocalDate,
    checkOut: LocalDate,
    guests: Long,
    totalCents: Long,
    modifier: Modifier = Modifier,
) {
    require(checkOut > checkIn && guests > 0)
    val locale = LocalConfiguration.current.locales[0]
    val nights = ChronoUnit.DAYS.between(checkIn, checkOut)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        DetailRow(stringResource(R.string.campsite), name)
        DetailRow(
            stringResource(R.string.dates),
            stringResource(
                R.string.stay_dates,
                formatDate(checkIn, locale),
                formatDate(checkOut, locale),
            ),
        )
        DetailRow(
            stringResource(R.string.stay),
            pluralStringResource(
                R.plurals.nights,
                nights.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                nights,
            ),
        )
        DetailRow(
            stringResource(R.string.guests),
            pluralStringResource(
                R.plurals.guests,
                guests.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                guests,
            ),
        )
        HorizontalDivider()
        DetailRow(stringResource(R.string.total), formatCad(totalCents, locale))
    }
}
