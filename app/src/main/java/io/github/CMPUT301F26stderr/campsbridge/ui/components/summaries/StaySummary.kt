package io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
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
 * @param image Optional caller-owned image presentation; this summary never resolves assets.
 */
@Composable
fun StaySummary(
    name: String,
    checkIn: LocalDate,
    checkOut: LocalDate,
    guests: Long,
    totalCents: Long,
    modifier: Modifier = Modifier,
    image: (@Composable () -> Unit)? = null,
) {
    require(checkOut > checkIn && guests > 0)
    val locale = LocalConfiguration.current.locales[0]
    val nights = ChronoUnit.DAYS.between(checkIn, checkOut)
    OutlinedCard(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            image?.invoke()
            Text(name, style = MaterialTheme.typography.titleLarge)
            DetailRow(
                stringResource(R.string.dates),
                stringResource(
                    R.string.stay_dates,
                    formatDate(checkIn, locale),
                    formatDate(checkOut, locale),
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                DetailRow(
                    stringResource(R.string.stay),
                    pluralStringResource(
                        R.plurals.nights,
                        nights.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        nights,
                    ),
                    Modifier.weight(1f),
                )
                DetailRow(
                    stringResource(R.string.guests),
                    pluralStringResource(
                        R.plurals.guests,
                        guests.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        guests,
                    ),
                    Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(stringResource(R.string.total), style = MaterialTheme.typography.titleMedium)
                Text(
                    formatCad(totalCents, locale),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
