package io.github.CMPUT301F26stderr.campsbridge.data.messaging

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message

/**
 * Explicit Firestore codec for [Message], deriving identity from the document path.
 */
object MessageDocument : DocumentCodec<Message>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: Message): Map<String, Any?> =
        mapOf(
            "reservationId" to value.reservationId,
            "senderId" to value.senderId,
            "body" to value.body,
            "createdAt" to value.createdAt.stored(),
            "readAt" to value.readAt?.stored(),
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): Message =
        Message(
            fields.id,
            fields.string("reservationId"),
            fields.string("senderId"),
            fields.string("body"),
            fields.instant("createdAt"),
            fields.optionalInstant("readAt"),
        )
}
