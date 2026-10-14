package io.github.CMPUT301F26stderr.campsbridge.data.review

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reply

/**
 * Explicit Firestore codec for [Reply], deriving identity from the document path.
 */
object ReplyDocument : DocumentCodec<Reply>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: Reply): Map<String, Any?> =
        mapOf(
            "authorId" to value.authorId,
            "parentReplyId" to value.parentReplyId,
            "body" to value.body,
            "createdAt" to value.createdAt.stored(),
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): Reply =
        Reply(
            fields.id,
            fields.string("authorId"),
            fields.optionalString("parentReplyId"),
            fields.string("body"),
            fields.instant("createdAt"),
        )
}
