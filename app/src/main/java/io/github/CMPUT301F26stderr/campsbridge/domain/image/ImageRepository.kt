package io.github.CMPUT301F26stderr.campsbridge.domain.image

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for attachment metadata, uploaded blobs, and retryable
 * publication/cleanup.
 */
interface ImageRepository {
    /**
     * Returns the published attachment for an image slot, or null when empty.
     *
     * @param target Account or campsite image slot.
     */
    suspend fun get(target: ImageTarget): ImageAttachment?

    /**
     * Observes the image slot, emitting null when no attachment is published.
     *
     * @param target Account or campsite image slot.
     */
    fun observe(target: ImageTarget): Flow<ImageAttachment?>

    /**
     * Observes all readable image attachments for browsing or moderation.
     */
    fun observeAll(): Flow<List<ImageAttachment>>

    /**
     * Records upload intent, uploads and closes the supplied stream, then switches the
     * attachment transactionally. Obsolete cleanup may be deferred; ambiguous publication
     * requires reconciliation.
     *
     * @param target Account or campsite image slot.
     * @param upload Image content and metadata to upload.
     */
    suspend fun replace(
        target: ImageTarget,
        upload: ImageUpload,
    ): ImageUpdateResult

    /**
     * Removes the attachment and queues obsolete-blob deletion; returns pending cleanup when
     * needed.
     *
     * @param target Account or campsite image slot.
     */
    suspend fun delete(target: ImageTarget): ImageUpdateResult

    /**
     * Resolves a current Storage download URL without persisting it in Firestore.
     *
     * @param blobId Immutable ID identifying one uploaded Storage object.
     */
    suspend fun downloadUrl(blobId: String): String

    /**
     * Deletes only an unattached obsolete/abandoned blob; rejects active uploads and
     * published blobs. Retains an unacknowledged intent when needed to catch late bytes.
     *
     * @param blobId Immutable ID identifying one uploaded Storage object.
     */
    suspend fun retryCleanup(blobId: String)
}
