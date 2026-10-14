package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.cancellation
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration

/**
 * Explicit Firestore codec for [Lottery], deriving identity from the document path.
 */
object LotteryDocument : DocumentCodec<Lottery>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: Lottery): Map<String, Any?> =
        mapOf(
            "ownerId" to value.ownerId,
            "title" to value.title,
            "description" to value.description,
            "registrationOpensAt" to value.configuration.registrationOpensAt.stored(),
            "registrationClosesAt" to value.configuration.registrationClosesAt.stored(),
            "bookingOpensAt" to value.configuration.bookingOpensAt.stored(),
            "bookingClosesAt" to value.configuration.bookingClosesAt.stored(),
            "winnerCount" to value.configuration.winnerCount,
            "waitlistCapacity" to value.configuration.waitlistCapacity,
            "waitingCount" to value.waitingCount,
            "createdAt" to value.createdAt.stored(),
            "publishedAt" to value.publishedAt?.stored(),
            "drawnAt" to value.drawnAt?.stored(),
            "cancellation" to value.cancellation?.stored(),
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): Lottery =
        Lottery(
            fields.id,
            fields.string("ownerId"),
            fields.string("title"),
            fields.string("description"),
            LotteryConfiguration(
                fields.instant("registrationOpensAt"),
                fields.instant("registrationClosesAt"),
                fields.instant("bookingOpensAt"),
                fields.instant("bookingClosesAt"),
                fields.long("winnerCount"),
                fields.optionalLong("waitlistCapacity"),
            ),
            fields.long("waitingCount"),
            fields.instant("createdAt"),
            fields.optionalInstant("publishedAt"),
            fields.optionalInstant("drawnAt"),
            fields.optionalNested("cancellation")?.cancellation(),
        )
}
