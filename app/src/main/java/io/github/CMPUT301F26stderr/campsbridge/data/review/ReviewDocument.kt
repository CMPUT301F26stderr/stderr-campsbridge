package io.github.CMPUT301F26stderr.campsbridge.data.review

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review

/**
 * Explicit Firestore codec for [Review], deriving identity from the document path.
 */
object ReviewDocument : DocumentCodec<Review>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: Review): Map<String, Any?> =
        mapOf(
            "rating" to value.rating,
            "body" to value.body,
            "createdAt" to value.createdAt.stored(),
            "updatedAt" to value.updatedAt.stored(),
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): Review =
        Review(
            fields.parentId,
            fields.id,
            fields.long("rating"),
            fields.optionalString("body"),
            fields.instant("createdAt"),
            fields.instant("updatedAt"),
        )
}
