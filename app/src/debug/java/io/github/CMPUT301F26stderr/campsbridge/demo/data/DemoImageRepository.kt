/*
 * Saves local photo bytes before committing their attachment relationships.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo.data

import android.util.Log
import io.github.CMPUT301F26stderr.campsbridge.data.image.ImageAttachmentDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpdateResult
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.util.UUID
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths as Paths

/** Saves local photo bytes before committing their attachment relationships.
 * @param directory Scenario-owned private cache directory; bytes are never uploaded.
 * @param clock Timestamp source for local attachments.
 * @param samples Packaged read-only photo URLs keyed by their fixture slots.
 * @param shared Optional durable state; isolated examples keep their private lifecycle.
 */
class DemoImageRepository(
    private val directory: File,
    private val clock: Clock = Clock.systemUTC(),
    samples: Map<ImageTarget, String> = emptyMap(),
    private val shared: DemoStore? = null,
) : ImageRepository {
    private val sampleAttachments =
        samples.keys.associateWith {
            ImageAttachment(it, "sample-${it.type}-${it.id}", clock.instant())
        }
    private val sampleUrls =
        sampleAttachments
            .map { (target, attachment) ->
                attachment.blobId to
                    samples.getValue(target)
            }.toMap()
    private val store =
        shared
            ?: DemoStore(
                sampleAttachments.entries.associate {
                    Paths.image(it.key) to
                        ImageAttachmentDocument.encode(it.value)
                },
            )
    private val lock = Mutex()

    @Volatile private var closed = false

    /** Returns the current local attachment.
     * @param target Requested demo slot.
     */
    override suspend fun get(target: ImageTarget) =
        store.read { get(Paths.image(target), ImageAttachmentDocument) }

    /** Observes a local slot without network access.
     * @param target Requested demo slot.
     */
    override fun observe(target: ImageTarget) =
        store.observe { get(Paths.image(target), ImageAttachmentDocument) }

    /** Observes all attachments in this scenario. */
    override fun observeAll() =
        store.observe { list("images", ImageAttachmentDocument) }

    /** Copies and closes selected bytes before publishing a local attachment.
     * @param target Slot to replace.
     * @param upload Repeatable image stream supplied by the ordinary photo reader.
     */
    override suspend fun replace(
        target: ImageTarget,
        upload: ImageUpload,
    ): ImageUpdateResult =
        lock.withLock {
            withContext(Dispatchers.IO) {
                check(!closed) { "Demo session closed" }
                check(directory.isDirectory || directory.mkdirs())
                val file = File(directory, UUID.randomUUID().toString())
                try {
                    upload.openStream().use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                    currentCoroutineContext().ensureActive()
                } catch (failure: Exception) {
                    file.delete()
                    throw failure
                }
                val attachment = ImageAttachment(target, file.name, clock.instant())
                val old = get(target)
                try {
                    store.change { put(Paths.image(target), ImageAttachmentDocument, attachment) }
                } catch (
                    failure: Exception,
                ) {
                    file.delete()
                    throw failure
                }
                val obsolete =
                    old?.blobId?.takeUnless {
                        it in sampleUrls ||
                            File(directory, it).delete()
                    }
                ImageUpdateResult(attachment, obsolete)
            }
        }

    /** Removes the local relationship and attempts cache cleanup; repeated removal is harmless.
     * @param target Slot to clear.
     */
    override suspend fun delete(target: ImageTarget): ImageUpdateResult =
        lock.withLock {
            withContext(Dispatchers.IO) {
                check(!closed) { "Demo session closed" }
                val old = get(target)
                store.change { raw.remove(Paths.image(target)) }
                val obsolete =
                    old?.blobId?.takeUnless {
                        it in sampleUrls ||
                            File(directory, it).delete()
                    }
                ImageUpdateResult(null, obsolete)
            }
        }

    /** Returns a private file reference understood by the existing image loader.
     * @param blobId Published local blob identity.
     */
    override suspend fun downloadUrl(blobId: String): String =
        withContext(Dispatchers.IO) {
            sampleUrls[blobId] ?: run {
                val file = File(directory, blobId)
                check(file.isFile)
                file.toURI().toString()
            }
        }

    /** Deletes only an unattached demo blob.
     * @param blobId Local blob identity to clean up.
     */
    override suspend fun retryCleanup(blobId: String) =
        lock.withLock {
            withContext(Dispatchers.IO) {
                check(store.read { list("images", ImageAttachmentDocument).none { it.blobId == blobId } })
                if (blobId !in sampleUrls) {
                    val file = File(directory, blobId)
                    check(!file.exists() || file.delete())
                }
            }
        }

    /** Stops writes; isolated examples remove their cache while shared photos remain saved. */
    suspend fun close() {
        closed = true
        lock.withLock {
            withContext(Dispatchers.IO) {
                if (shared != null) return@withContext
                try {
                    if (directory.exists() && !directory.deleteRecursively()) {
                        Log.w("DemoImages", "Could not remove retired demo photo cache")
                    }
                } catch (failure: SecurityException) {
                    Log.w("DemoImages", "Could not remove retired demo photo cache", failure)
                }
            }
        }
    }
}
