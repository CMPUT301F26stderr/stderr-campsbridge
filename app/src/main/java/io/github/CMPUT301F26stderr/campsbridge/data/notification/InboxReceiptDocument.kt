package io.github.CMPUT301F26stderr.campsbridge.data.notification

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt

/**
 * Explicit Firestore codec for [InboxReceipt], deriving identity from the document path.
 */
object InboxReceiptDocument : DocumentCodec<InboxReceipt>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: InboxReceipt): Map<String, Any?> =
        mapOf("readAt" to value.readAt?.stored())

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): InboxReceipt =
        InboxReceipt(fields.id, fields.optionalInstant("readAt"))
}
