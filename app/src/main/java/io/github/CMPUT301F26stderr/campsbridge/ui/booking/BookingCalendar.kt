package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarState

/** Declares the calendar projection; BK2 selection behavior is pending the prepared tests.
 * @param state Source-backed dates-page facts.
 */
internal fun bookingCalendarState(state: BookingUiState.Content): CalendarState =
    CalendarState(state.month)
