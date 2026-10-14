package io.github.CMPUT301F26stderr.campsbridge.data.campsite

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.parseDate
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight

/**
 * Explicit Firestore codec for [CalendarNight], deriving identity from the document path.
 */
object CalendarNightDocument : DocumentCodec<CalendarNight>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: CalendarNight): Map<String, Any?> =
        mapOf(
            "isOffered" to value.isOffered,
            "reservationId" to value.reservationId,
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): CalendarNight =
        CalendarNight(
            parseDate(fields.id),
            fields.boolean("isOffered"),
            fields.optionalString("reservationId"),
        )
}
