package io.github.CMPUT301F26stderr.campsbridge.data.image

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.removal
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImagePublicationException
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpdateResult
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.util.UUID

/**
 * Firebase-backed implementation for attachment metadata, uploaded blobs, and retryable
 * publication/cleanup.
 *
 * @param db Firestore client used for queries and coordinated writes.
 * @param storage Storage client that owns uploaded image bytes.
 * @param clock Injectable time source for recorded timestamps and deadline checks.
 */
class FirebaseImageRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val clock: Clock,
) : ImageRepository {
    /**
     * Returns the published attachment for an image slot, or null when empty.
     *
     * @param target Account or campsite image slot.
     */
    override suspend fun get(target: ImageTarget) =
        db.document(FirestorePaths.image(target)).read(ImageAttachmentDocument)

    /**
     * Observes the image slot, emitting null when no attachment is published.
     *
     * @param target Account or campsite image slot.
     */
    override fun observe(target: ImageTarget) =
        db.document(FirestorePaths.image(target)).observe(ImageAttachmentDocument)

    /**
     * Observes all readable image attachments for browsing or moderation.
     */
    override fun observeAll() =
        db.collection("images").observe(ImageAttachmentDocument)

    /**
     * Resolves a current Storage download URL without persisting it in Firestore.
     *
     * @param blobId Immutable ID identifying one uploaded Storage object.
     */
    override suspend fun downloadUrl(blobId: String): String =
        storage.reference
            .child(DocumentKeys.blob(blobId))
            .downloadUrl
            .await()
            .toString()

    /**
     * Records upload intent, uploads and closes the supplied stream, then switches the
     * attachment transactionally. Obsolete cleanup may be deferred; ambiguous publication
     * requires reconciliation.
     *
     * @param target Account or campsite image slot.
     * @param upload Image content and metadata to upload.
     */
    override suspend fun replace(
        target: ImageTarget,
        upload: ImageUpload,
    ): ImageUpdateResult {
        require(upload.contentType.startsWith("image/"))
        val blobId = UUID.randomUUID().toString()
        val uploadRef = db.collection("_imageUploads").document(blobId)
        // Record intent before any bytes are sent. A later maintenance pass can find abandoned
        // uploads.
        uploadRef
            .set(
                mapOf(
                    "attachmentId" to DocumentKeys.attachment(target),
                    "expiresAt" to clock.instant().plusSeconds(900).stored(),
                    "abandoned" to false,
                    "uploadFinished" to false,
                ),
            ).await()
        val objectRef = storage.reference.child(DocumentKeys.blob(blobId))
        upload.openStream().use { stream ->
            val task =
                objectRef.putStream(
                    stream,
                    StorageMetadata
                        .Builder()
                        .setContentType(upload.contentType)
                        .setCustomMetadata("attachmentId", DocumentKeys.attachment(target))
                        .build(),
                )
            try {
                withTimeout(600_000) { task.await() }
            } catch (cancelled: CancellationException) {
                task.cancel()
                throw cancelled
            }
        }
        uploadRef.update("uploadFinished", true).await()
        val attachment = ImageAttachment(target, blobId, clock.instant())
        val old =
            try {
                db.atomic { tx ->
                    when (target.type) {
                        ImageTargetType.ACCOUNT -> {
                            tx.activeAccount(db, target.id)
                        }

                        ImageTargetType.CAMPSITE -> {
                            val site =
                                tx.require(
                                    db.document(FirestorePaths.campsite(target.id)),
                                    CampsiteDocument,
                                )
                            requireOperation(site.removal == null, INACTIVE, "Campsite removed")
                            tx.activeAccount(db, site.ownerId, Role.OWNER)
                        }
                    }
                    val ref = db.document(FirestorePaths.image(target))
                    val previous = tx.read(ref, ImageAttachmentDocument)
                    val pending = tx.get(uploadRef)
                    requireOperation(
                        pending.exists() && pending.getBoolean("abandoned") == false,
                        CONFLICT,
                        "Upload was abandoned; retry with a new upload",
                    )
                    tx.set(ref, ImageAttachmentDocument.encode(attachment))
                    tx.delete(uploadRef)
                    if (previous != null) {
                        tx.set(
                            db.collection("_storageCleanup").document(previous.blobId),
                            mapOf(
                                "queuedAt" to clock.instant().stored(),
                                "attachmentId" to DocumentKeys.attachment(target),
                            ),
                        )
                    }
                    previous
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // An acknowledgement can be lost after commit. Never delete a possibly published
                // blob.
                throw ImagePublicationException(target, blobId, failure)
            }
        val pending = old?.blobId?.let { cleanupOrDefer(it) }
        return ImageUpdateResult(attachment, pending)
    }

    /**
     * Removes the attachment and queues obsolete-blob deletion; returns pending cleanup when
     * needed.
     *
     * @param target Account or campsite image slot.
     */
    override suspend fun delete(target: ImageTarget): ImageUpdateResult {
        val old =
            db.atomic { tx ->
                val ref = db.document(FirestorePaths.image(target))
                val previous = tx.read(ref, ImageAttachmentDocument)
                if (previous != null) {
                    tx.delete(ref)
                    tx.set(
                        db.collection("_storageCleanup").document(previous.blobId),
                        mapOf(
                            "queuedAt" to clock.instant().stored(),
                            "attachmentId" to DocumentKeys.attachment(target),
                        ),
                    )
                }
                previous
            }
        return ImageUpdateResult(null, old?.blobId?.let { cleanupOrDefer(it) })
    }

    /**
     * Attempts obsolete-blob cleanup, returning its ID on failure while propagating
     * cancellation.
     *
     * @param blobId Immutable ID identifying one uploaded Storage object.
     */
    private suspend fun cleanupOrDefer(blobId: String): String? =
        try {
            retryCleanup(blobId)
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            blobId
        }

    /**
     * Deletes only an unattached obsolete/abandoned blob; rejects active uploads and
     * published blobs. Retains an unacknowledged intent when needed to catch late bytes.
     *
     * @param blobId Immutable ID identifying one uploaded Storage object.
     */
    override suspend fun retryCleanup(blobId: String) {
        DocumentKeys.id(blobId)
        val pendingRef = db.collection("_imageUploads").document(blobId)
        val pending = pendingRef.get(Source.SERVER).await()
        if (pending.exists()) {
            val claimed = abandonExpiredUpload(blobId)
            requireOperation(claimed, CONFLICT, "Upload is still active or attached")
        }
        requireOperation(
            db
                .collection("images")
                .whereEqualTo("blobId", blobId)
                .limit(1)
                .get(Source.SERVER)
                .await()
                .isEmpty,
            CONFLICT,
            "Blob is still attached",
        )
        var removedObject = true
        try {
            storage.reference
                .child(DocumentKeys.blob(blobId))
                .delete()
                .await()
        } catch (failure: StorageException) {
            if (failure.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw failure
            removedObject = false
        }
        db
            .collection("_storageCleanup")
            .document(blobId)
            .delete()
            .await()
        // Retain an abandoned intent when no object exists and the uploader never acknowledged
        // completion: a late upload can then be discovered and deleted on the next maintenance
        // pass.
        if (pending.exists() && (removedObject || pending.getBoolean("uploadFinished") == true)) {
            pendingRef.delete().await()
        }
    }

    /**
     * Claims an expired unattached upload transactionally, conflicting with publication.
     * Returns false for an active upload, missing intent, or already attached blob.
     *
     * @param blobId Immutable ID identifying one uploaded Storage object.
     */
    private suspend fun abandonExpiredUpload(blobId: String): Boolean =
        db.atomic { tx ->
            val pendingRef = db.collection("_imageUploads").document(blobId)
            val pending = tx.get(pendingRef)
            if (!pending.exists()) return@atomic false
            val fields = Fields(pending.reference.path, pending.data!!)
            val attachmentRef = db.collection("images").document(fields.string("attachmentId"))
            val attachment = tx.read(attachmentRef, ImageAttachmentDocument)
            if (attachment?.blobId == blobId) {
                tx.delete(pendingRef)
                return@atomic false
            }
            if (fields.instant("expiresAt") > clock.instant() && !fields.boolean("abandoned")) {
                return@atomic false
            }
            // Publication also reads this record, so it conflicts with the abandonment claim.
            tx.update(pendingRef, "abandoned", true)
            true
        }

    /**
     * Retries queued obsolete deletions and expired uploads; failures leave durable work for
     * another call.
     */
    suspend fun resumeCleanup() {
        for (
        job in
        db
            .collection("_storageCleanup")
            .get(Source.SERVER)
            .await()
            .documents
        ) {
            retryCleanup(
                job.id,
            )
        }
        for (job in db
            .collection("_imageUploads")
            .get(Source.SERVER)
            .await()
            .documents) {
            if (abandonExpiredUpload(job.id)) retryCleanup(job.id)
        }
    }
}
