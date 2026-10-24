package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpdateResult
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.util.UUID

/** Saves demo photos in a private cache directory; attachment relationships stay in memory.
 * @param directory Scenario-owned private cache directory; bytes are never uploaded.
 * @param clock Timestamp source for local attachments.
 */
class DemoImageRepository(
    private val directory: File,
    private val clock: Clock = Clock.systemUTC(),
) : ImageRepository {
    private val attachments = MutableStateFlow(emptyMap<ImageTarget, ImageAttachment>())
    private val lock = Mutex()

    /** Returns the current local attachment.
     * @param target Requested demo slot.
     */
    override suspend fun get(target: ImageTarget) =
        attachments.value[target]

    /** Observes a local slot without network access.
     * @param target Requested demo slot.
     */
    override fun observe(target: ImageTarget) =
        attachments.map { it[target] }.distinctUntilChanged()

    /** Observes all attachments in this scenario. */
    override fun observeAll() =
        attachments.map { it.values.toList() }

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
                val old = attachments.value[target]
                attachments.value = attachments.value + (target to attachment)
                val obsolete = old?.blobId?.takeUnless { File(directory, it).delete() }
                ImageUpdateResult(attachment, obsolete)
            }
        }

    /** Removes the local relationship and attempts cache cleanup; repeated removal is harmless.
     * @param target Slot to clear.
     */
    override suspend fun delete(target: ImageTarget): ImageUpdateResult =
        lock.withLock {
            withContext(Dispatchers.IO) {
                val old = attachments.value[target]
                attachments.value = attachments.value - target
                val obsolete = old?.blobId?.takeUnless { File(directory, it).delete() }
                ImageUpdateResult(null, obsolete)
            }
        }

    /** Returns a private file reference understood by the existing image loader.
     * @param blobId Published local blob identity.
     */
    override suspend fun downloadUrl(blobId: String): String =
        withContext(Dispatchers.IO) {
            val file = File(directory, blobId)
            check(file.isFile)
            file.toURI().toString()
        }

    /** Deletes only an unattached demo blob.
     * @param blobId Local blob identity to clean up.
     */
    override suspend fun retryCleanup(blobId: String) =
        lock.withLock {
            withContext(Dispatchers.IO) {
                check(attachments.value.values.none { it.blobId == blobId })
                val file = File(directory, blobId)
                check(!file.exists() || file.delete())
            }
        }
}
