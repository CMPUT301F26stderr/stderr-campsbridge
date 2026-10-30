package io.github.CMPUT301F26stderr.campsbridge.ui.common.preview

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import java.time.Instant
import java.time.LocalDate

/** Supplies a passive complete record to main-source previews, without a fake backend. */
internal fun previewReservation(): Reservation {
    val at = Instant.parse("2027-04-21T15:00:00Z")
    return Reservation(
        "reservation-river",
        "may-weekends",
        "camper",
        "river",
        LocalDate.of(2027, 5, 14),
        LocalDate.of(2027, 5, 16),
        4,
        BookedCampsite("River Camp", "Near Jasper", Coordinates(53.0, -118.0), "America/Edmonton"),
        AcceptedTerms("Quiet hours after 10 pm.\nPack out all litter.", BookingPolicy.VERSION, at),
        at,
        Payment(13000, at),
    )
}
