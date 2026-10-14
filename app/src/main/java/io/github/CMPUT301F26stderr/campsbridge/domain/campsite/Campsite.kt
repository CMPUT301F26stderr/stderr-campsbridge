package io.github.CMPUT301F26stderr.campsbridge.domain.campsite

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Editable listing facts with positive capacity, nonnegative pricing, and a valid timezone.
 *
 * @param name Display name shown in the application.
 * @param description Listing or lottery description.
 * @param location Campsite coordinates used for map display.
 * @param locationLabel Human-readable campsite location.
 * @param timeZone IANA timezone used to interpret local stay dates.
 * @param maxGuests Maximum group size for one stay.
 * @param amenities Unique facilities or preferred facilities.
 * @param tags Unique campsite characteristics or preferred characteristics.
 * @param nightlyPriceCents Nonnegative nightly CAD price in integer cents.
 * @param rules Campsite rules presented to entrants.
 */
data class CampsiteDetails(
    val name: String,
    val description: String,
    val location: Coordinates,
    val locationLabel: String,
    val timeZone: String,
    val maxGuests: Long,
    val amenities: Set<String>,
    val tags: Set<String>,
    val nightlyPriceCents: Long,
    val rules: String,
) {
    init {
        require(name.isNotBlank())
        require(maxGuests > 0)
        require(nightlyPriceCents >= 0)
        ZoneId.of(timeZone)
    }
}

/**
 * Owner-linked listing whose publication and removal determine discovery visibility.
 *
 * @param id Campsite document ID.
 * @param ownerId Account ID of the campsite or lottery owner.
 * @param details Editable campsite facts to store.
 * @param createdAt Original record creation time.
 * @param publishedAt First publication time, or null while still a draft.
 * @param removal Removal metadata, or null for an account/listing not marked removed.
 */
data class Campsite(
    val id: String,
    val ownerId: String,
    val details: CampsiteDetails,
    val createdAt: Instant,
    val publishedAt: Instant? = null,
    val removal: Removal? = null,
) {
    val isPublished: Boolean
        get() = publishedAt != null && removal == null
}

/**
 * Offered inventory and optional reservation claim for one local campsite date.
 *
 * @param date Local campsite calendar date.
 * @param isOffered Whether the owner offers this night as inventory.
 * @param reservationId Reservation document ID.
 */
data class CalendarNight(
    val date: LocalDate,
    val isOffered: Boolean,
    val reservationId: String? = null,
) {
    val isFree: Boolean
        get() = isOffered && reservationId == null
}
