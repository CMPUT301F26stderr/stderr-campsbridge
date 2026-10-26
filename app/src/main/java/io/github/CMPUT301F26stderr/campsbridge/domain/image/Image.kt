package io.github.CMPUT301F26stderr.campsbridge.domain.image

import java.time.Instant

/**
 * Parent entity types that support a single uploaded image attachment.
 */
enum class ImageTargetType {
    ACCOUNT,
    CAMPSITE,
}

/**
 * Stable account or campsite image slot, independent of individual uploaded blobs.
 *
 * @param type Parent entity category for the image slot.
 * @param id Parent account or campsite ID.
 */
data class ImageTarget(
    val type: ImageTargetType,
    val id: String,
)

/**
 * Published association between a parent image slot and uploaded Storage bytes.
 *
 * @param target Account or campsite image slot.
 * @param blobId Immutable ID identifying one uploaded Storage object.
 * @param uploadedAt Time the current attachment was published.
 */
data class ImageAttachment(
    val target: ImageTarget,
    val blobId: String,
    val uploadedAt: Instant,
)

/**
 * The repository opens and closes this lazy, repeatable content on an IO dispatcher.
 *
 * @param contentType Image MIME type supplied as Storage metadata.
 * @param openStream Opens image content; the repository closes the returned stream.
 */
data class ImageUpload(
    val contentType: String,
    val openStream: () -> java.io.InputStream,
)

/**
 * Publication succeeded even if obsolete object cleanup needs retry.
 *
 * @param attachment Published attachment, or null after removal.
 * @param obsoleteBlobId Blob still awaiting cleanup, or null if cleanup completed.
 */
data class ImageUpdateResult(
    val attachment: ImageAttachment?,
    val obsoleteBlobId: String?,
)
