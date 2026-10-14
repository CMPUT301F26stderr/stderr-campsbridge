package io.github.CMPUT301F26stderr.campsbridge.data.image

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentCodec
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment

/**
 * Explicit Firestore codec for [ImageAttachment], deriving identity from the document path.
 */
object ImageAttachmentDocument : DocumentCodec<ImageAttachment>() {
    /**
     * Serializes the application model into explicitly named Firestore fields.
     *
     * @param value Application value to serialize.
     */
    override fun encode(value: ImageAttachment): Map<String, Any?> =
        mapOf(
            "blobId" to value.blobId,
            "uploadedAt" to value.uploadedAt.stored(),
        )

    /**
     * Reads and validates stored fields, recovering model identity from the document path.
     *
     * @param fields Strict field reader with the document’s path context.
     */
    override fun read(fields: Fields): ImageAttachment =
        ImageAttachment(
            DocumentKeys.target(fields.id),
            fields.string("blobId"),
            fields.instant("uploadedAt"),
        )
}
