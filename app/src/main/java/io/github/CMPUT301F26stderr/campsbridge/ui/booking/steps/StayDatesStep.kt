package io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.bookingCalendarState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CampsiteCalendar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Prototype 07.02 with shared calendar, actual deadline and explicit occupied-night feedback.
 * @param state Current draft and confirmed inventory; no writes occur in this content.
 * @param onMonth Changes the displayed month only.
 * @param onDate Delivers a selectable cell to the parent.
 * @param onClear Clears all endpoints.
 * @param onRetryVisible Retries only failed displayed inventory.
 * @param onRetrySelected Retries only failed stay confirmation.
 * @param enabled Whether draft actions are permitted.
 */
@Composable
fun StayDatesStep(
    state: BookingUiState.Content,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
    onClear: () -> Unit,
    onRetryVisible: () -> Unit,
    onRetrySelected: () -> Unit,
    enabled: Boolean,
) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        stringResource(
            R.string.booking_deadline,
            formatDateTime(
                state.lottery.configuration.bookingClosesAt,
                ZoneId.of(state.campsite.details.timeZone),
                locale,
            ),
        ),
    )
    Text(stringResource(R.string.booking_date_hint))
    if (state.eligibilityPending) Text(stringResource(R.string.booking_eligibility_pending))
    when (state.calendar.visible) {
        CalendarInventoryRead.Loading -> {
            LoadingContent()
        }

        CalendarInventoryRead.Failure -> {
            FailureContent(
                stringResource(R.string.booking_inventory_failed),
                onRetryVisible,
                enabled,
            )
        }

        else -> {
            Unit
        }
    }
    CampsiteCalendar(
        bookingCalendarState(state),
        onMonth,
        onDate,
        Modifier.testTag("booking-calendar"),
        enabled = enabled,
    )
    state.draft.checkIn?.let { Text(stringResource(R.string.arrival_date, formatDate(it, locale))) }
    state.draft.checkOut?.let {
        Text(
            stringResource(R.string.checkout_date, formatDate(it, locale)),
        )
    }
    if (state.draft.checkIn != null || state.draft.checkOut != null) {
        TextButton(
            onClear,
            enabled = enabled,
        ) { Text(stringResource(R.string.booking_clear_dates)) }
    }
    if (state.calendar.selected == CalendarInventoryRead.Failure &&
        state.calendar.visible != CalendarInventoryRead.Failure
    ) {
        FailureContent(
            stringResource(R.string.booking_selected_inventory_failed),
            onRetrySelected,
            enabled,
        )
    }
}
