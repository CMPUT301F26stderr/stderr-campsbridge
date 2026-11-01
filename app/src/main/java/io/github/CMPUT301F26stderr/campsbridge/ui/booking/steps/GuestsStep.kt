package io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.CountInput

/** Prototype 07.03 with one raw count and integrated bounded increment/decrement actions.
 * @param state Current raw guests and current capacity.
 * @param onGuests Replaces raw guest input without saving a reservation.
 * @param enabled Whether this step may change its draft.
 */
@Composable
fun GuestsStep(
    state: BookingUiState.Content,
    onGuests: (String) -> Unit,
    enabled: Boolean,
) {
    val locale = LocalConfiguration.current.locales[0]
    val start = state.draft.checkIn
    val end = state.draft.checkOut
    if (start != null && end != null) {
        Text(
            stringResource(R.string.stay_dates, formatDate(start, locale), formatDate(end, locale)),
        )
    }
    CountInput(
        state.draft.guestInput,
        state.guestCount,
        onGuests,
        stringResource(R.string.booking_guest_count),
        stringResource(R.string.guests),
        1,
        state.campsite.details.maxGuests,
        Modifier.testTag("booking-guests"),
        enabled = enabled,
        supportingText = stringResource(R.string.up_to_guests, state.campsite.details.maxGuests),
    )
}
