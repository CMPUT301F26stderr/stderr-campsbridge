package io.github.CMPUT301F26stderr.campsbridge.data.booking

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment

/**
 * Explicit Firestore codec for [Payment], deriving identity from the document path.
 */
object PaymentDocument : DocumentCodec<Payment>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: Payment): Map<String, Any?> =
        mapOf(
            "amountCents" to value.amountCents,
            "paidAt" to value.paidAt.stored(),
            "refundedAt" to value.refundedAt?.stored(),
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): Payment =
        Payment(
            fields.id,
            fields.long("amountCents"),
            fields.instant("paidAt"),
            fields.optionalInstant("refundedAt"),
        )
}
