package io.github.CMPUT301F26stderr.campsbridge.demo

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.platform.assets.SceneryPhoto
import java.time.Instant
import java.time.LocalDate

internal val GalleryDate: LocalDate = LocalDate.of(2027, 5, 14)
internal val GalleryInstant: Instant = Instant.parse("2027-04-14T15:00:00Z")
internal val GALLERY_PHOTO = SceneryPhoto.JASPER.reference

/** Builds one realistic listing without SDK or repository initialization.
 * @param name Localized example name.
 * @param location Localized example location.
 * @param description Localized example description.
 * @param rules Localized example rules.
 */
internal fun galleryCampsite(
    name: String,
    location: String,
    description: String,
    rules: String,
): Campsite =
    Campsite(
        "site",
        "owner",
        CampsiteDetails(
            name,
            description,
            Coordinates(52.87, -118.08),
            location,
            "America/Edmonton",
            8,
            setOf("water"),
            setOf("quiet"),
            6500,
            rules,
        ),
        GalleryInstant,
        GalleryInstant,
    )

/** Builds a published lottery for content examples.
 * @param title Localized example title.
 */
internal fun galleryLottery(title: String): Lottery =
    Lottery(
        "lottery",
        "owner",
        title,
        "",
        LotteryConfiguration(
            GalleryInstant,
            GalleryInstant.plusSeconds(86400 * 6),
            GalleryInstant.plusSeconds(86400 * 6),
            GalleryInstant.plusSeconds(86400 * 10),
            5,
            50,
        ),
        12,
        GalleryInstant,
        GalleryInstant,
    )

/** Builds recorded facts for a two-night confirmed stay.
 * @param site Existing example listing.
 */
internal fun galleryReservation(site: Campsite): Reservation =
    Reservation(
        "reservation",
        "lottery",
        "entrant",
        site.id,
        GalleryDate,
        GalleryDate.plusDays(2),
        4,
        BookedCampsite(
            site.details.name,
            site.details.locationLabel,
            site.details.location,
            site.details.timeZone,
        ),
        AcceptedTerms(site.details.rules, BookingPolicy.VERSION, GalleryInstant),
        GalleryInstant,
        Payment(13000, GalleryInstant),
    )
