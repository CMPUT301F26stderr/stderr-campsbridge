package io.github.CMPUT301F26stderr.campsbridge.data.campsite

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.removal
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails

/**
 * Explicit Firestore codec for [Campsite], deriving identity from the document path.
 */
object CampsiteDocument : DocumentCodec<Campsite>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: Campsite): Map<String, Any?> =
        mapOf(
            "ownerId" to value.ownerId,
            "name" to value.details.name,
            "description" to value.details.description,
            "location" to value.details.location.stored(),
            "locationLabel" to value.details.locationLabel,
            "timeZone" to value.details.timeZone,
            "maxGuests" to value.details.maxGuests,
            "amenities" to value.details.amenities.sorted(),
            "tags" to value.details.tags.sorted(),
            "nightlyPriceCents" to value.details.nightlyPriceCents,
            "rules" to value.details.rules,
            "createdAt" to value.createdAt.stored(),
            "publishedAt" to value.publishedAt?.stored(),
            "removal" to value.removal?.stored(),
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): Campsite =
        Campsite(
            fields.id,
            fields.string("ownerId"),
            CampsiteDetails(
                fields.string("name"),
                fields.string("description"),
                fields.point("location"),
                fields.string("locationLabel"),
                fields.string("timeZone"),
                fields.long("maxGuests"),
                fields.stringSet("amenities"),
                fields.stringSet("tags"),
                fields.long("nightlyPriceCents"),
                fields.string("rules"),
            ),
            fields.instant("createdAt"),
            fields.optionalInstant("publishedAt"),
            fields.optionalNested("removal")?.removal(),
        )
}
