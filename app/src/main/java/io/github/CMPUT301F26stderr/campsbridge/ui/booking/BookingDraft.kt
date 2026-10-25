package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import java.time.LocalDate

/** Unsubmitted input; incomplete dates and raw guest text stay separate from domain requests.
 * @param checkIn Inclusive local arrival, or null before selection.
 * @param checkOut Exclusive local departure, or null before selection.
 * @param guestInput Raw positive-count input retained through read failure and recreation.
 */
data class BookingDraft(
    val checkIn: LocalDate? = null,
    val checkOut: LocalDate? = null,
    val guestInput: String = "1",
)
