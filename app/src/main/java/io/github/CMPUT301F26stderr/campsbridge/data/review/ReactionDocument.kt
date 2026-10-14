package io.github.CMPUT301F26stderr.campsbridge.data.review

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reaction

/**
 * Explicit Firestore codec for [Reaction], deriving identity from the document path.
 */
object ReactionDocument : DocumentCodec<Reaction>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: Reaction): Map<String, Any?> =
        mapOf("type" to value.type.name)

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): Reaction =
        Reaction(fields.id, fields.enum("type"))
}
