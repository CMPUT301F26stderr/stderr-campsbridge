package io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.CountStepper

/** Prototype 07.03 with one controlled raw count and the existing bounded stepper.
 * @param state Current raw guests and current capacity.
 * @param onGuests Replaces raw guest input; neither control saves a reservation.
 * @param enabled Whether this step may change its draft.
 */
@Composable
fun GuestsStep(
    state: BookingUiState.Content,
    onGuests: (String) -> Unit,
    enabled: Boolean,
) {
    AppTextField(
        state.draft.guestInput,
        onGuests,
        stringResource(R.string.booking_guest_count),
        Modifier.testTag("booking-guests"),
        enabled = enabled,
        supportingText = stringResource(R.string.up_to_guests, state.campsite.details.maxGuests),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    state.guestCount?.takeIf { it <= state.campsite.details.maxGuests }?.let { count ->
        CountStepper(
            stringResource(R.string.guests),
            count,
            1,
            state.campsite.details.maxGuests,
            { onGuests(it.toString()) },
            enabled = enabled,
        )
    }
}
