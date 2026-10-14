package io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import java.time.Instant
import java.time.LocalDate

/** Valid starting values; each test changes only the facts relevant to its scenario. */
object CampingFixtures {
    val now: Instant = Instant.parse("2027-05-01T12:00:00.123456789Z")
    val arrival: LocalDate = LocalDate.parse("2027-05-15")
    val location: Coordinates = Coordinates(53.0, -118.0)

    /** Builds an active entrant profile.
     *
     * @param id Account identity. */
    fun account(id: String = "entrant") =
        Account(
            id,
            "Camper",
            "camper@example.test",
            "7805550100",
            setOf(Role.ENTRANT),
            Preferences(setOf("quiet"), setOf("water")),
            AccountSettings(),
            now,
        )

    /** Builds a published campsite.
     *
     * @param id Listing identity.
     * @param ownerId Listing owner. */
    fun campsite(
        id: String = "site",
        ownerId: String = "owner",
    ) =
        Campsite(
            id,
            ownerId,
            CampsiteDetails(
                "River Camp",
                "Quiet riverside camping",
                location,
                "Jasper",
                "America/Edmonton",
                8,
                setOf("water"),
                setOf("quiet"),
                6500,
                "Be kind",
            ),
            now,
            now,
        )

    /** Builds a published lottery before registration.
     *
     * @param id Draw identity.
     * @param ownerId Draw owner. */
    fun lottery(
        id: String = "draw",
        ownerId: String = "owner",
    ) =
        Lottery(
            id,
            ownerId,
            "May camping",
            "Two-night stays",
            LotteryConfiguration(
                now.plusSeconds(60),
                now.plusSeconds(120),
                now.plusSeconds(120),
                now.plusSeconds(240),
                2,
                null,
            ),
            0,
            now,
            now,
        )

    /** Builds a two-night reservation with historical terms and location. */
    fun reservation() =
        Reservation(
            "reservation",
            "draw",
            "entrant",
            "site",
            arrival,
            arrival.plusDays(2),
            4,
            BookedCampsite("River Camp", "Jasper", location, "America/Edmonton"),
            AcceptedTerms("Be kind", BookingPolicy.VERSION, now),
            now,
        )

    /** Builds an accepted quote matching the campsite and two-night stay. */
    fun bookingRequest() =
        BookingRequest(
            "draw",
            "entrant",
            "site",
            arrival,
            arrival.plusDays(2),
            4,
            13000,
            "Be kind",
            BookingPolicy.VERSION,
        )
}
