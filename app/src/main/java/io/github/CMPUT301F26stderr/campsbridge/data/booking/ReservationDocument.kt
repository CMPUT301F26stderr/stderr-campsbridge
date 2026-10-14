package io.github.CMPUT301F26stderr.campsbridge.data.booking

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.cancellation
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation

/**
 * Explicit Firestore codec for [Reservation], deriving identity from the document path.
 */
object ReservationDocument : DocumentCodec<Reservation>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: Reservation): Map<String, Any?> =
        mapOf(
            "lotteryId" to value.lotteryId,
            "entrantId" to value.entrantId,
            "campsiteId" to value.campsiteId,
            "checkInDate" to value.checkInDate.toString(),
            "checkOutDate" to value.checkOutDate.toString(),
            "guestCount" to value.guestCount,
            "bookedCampsite" to
                value.bookedCampsite.let {
                    mapOf(
                        "name" to it.name,
                        "locationLabel" to it.locationLabel,
                        "location" to it.location.stored(),
                        "timeZone" to it.timeZone,
                    )
                },
            "acceptedTerms" to
                value.acceptedTerms.let {
                    mapOf(
                        "rules" to it.rules,
                        "cancellationPolicyVersion" to it.cancellationPolicyVersion,
                        "acceptedAt" to it.acceptedAt.stored(),
                    )
                },
            "createdAt" to value.createdAt.stored(),
            "cancellation" to
                value.cancellation?.let {
                    mapOf(
                        "at" to it.at.stored(),
                        "actorId" to it.actorId,
                        "initiatedAs" to it.initiatedAs.name,
                        "reason" to it.reason,
                    )
                },
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): Reservation {
        require(
            fields.id ==
                DocumentKeys.reservation(fields.string("lotteryId"), fields.string("entrantId")),
        )
        return Reservation(
            fields.id,
            fields.string("lotteryId"),
            fields.string("entrantId"),
            fields.string("campsiteId"),
            fields.date("checkInDate"),
            fields.date("checkOutDate"),
            fields.long("guestCount"),
            fields.nested("bookedCampsite").let {
                BookedCampsite(
                    it.string("name"),
                    it.string("locationLabel"),
                    it.point("location"),
                    it.string("timeZone"),
                )
            },
            fields.nested("acceptedTerms").let {
                AcceptedTerms(
                    it.string("rules"),
                    it.string("cancellationPolicyVersion"),
                    it.instant("acceptedAt"),
                )
            },
            fields.instant("createdAt"),
            fields.optionalNested("cancellation")?.let {
                ReservationCancellation(
                    it.instant("at"),
                    it.string("actorId"),
                    it.enum("initiatedAs"),
                    it.optionalString("reason"),
                )
            },
        )
    }
}
