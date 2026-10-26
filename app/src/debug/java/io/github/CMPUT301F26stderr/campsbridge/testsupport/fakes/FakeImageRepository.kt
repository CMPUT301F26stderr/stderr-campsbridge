package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpdateResult
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Controlled image boundary for presentation tests; never uploads or deletes Storage bytes. */
class FakeImageRepository : ImageRepository {
    val attachment = MutableStateFlow<ImageAttachment?>(null)
    var observationFailure: Exception? = null
    var observationGate: CompletableDeferred<Unit>? = null
    val streamFailure = MutableStateFlow<Exception?>(null)
    val observedTargets = mutableListOf<ImageTarget>()
    val replacementTargets = mutableListOf<ImageTarget>()
    val removalTargets = mutableListOf<ImageTarget>()
    var readFailure: Exception? = null
    var replaceFailure: Exception? = null
    var deleteFailure: Exception? = null
    var uploadGate: CompletableDeferred<Unit>? = null
    var deleteGate: CompletableDeferred<Unit>? = null
    var readGate: CompletableDeferred<Unit>? = null
    var replaceResult: ImageUpdateResult? = null
    var replaceCalls = 0
    var deleteCalls = 0
    var getCalls = 0
    var activeObservers = 0
    val downloads = mutableListOf<String>()
    val urlResults = mutableMapOf<String, String>()
    val urlFailures = mutableMapOf<String, Exception>()
    val urlGates = mutableMapOf<String, CompletableDeferred<String>>()

    /** Returns controlled server state, with optional latency/error.
     * @param target Requested image slot.
     */
    override suspend fun get(target: ImageTarget): ImageAttachment? {
        getCalls++
        readGate?.await()
        readFailure?.let { throw it }
        return attachment.value
    }

    /** Tracks a controlled image subscription.
     * @param target Requested image slot.
     */
    override fun observe(target: ImageTarget): Flow<ImageAttachment?> =
        flow {
            observedTargets.add(target)
            observationGate?.await()
            observationFailure?.let { throw it }
            activeObservers++
            try {
                emitAll(
                    combine(attachment, streamFailure) { current, failure ->
                        if (failure != null) throw failure
                        current
                    },
                )
            } finally {
                activeObservers--
            }
        }

    /** Exposes the controlled attachment as a list. */
    override fun observeAll(): Flow<List<ImageAttachment>> =
        attachment.map { listOfNotNull(it) }

    /** Returns the controlled publication outcome; does not consume image bytes.
     * @param target Requested image slot.
     * @param upload Captured application upload contract.
     */
    override suspend fun replace(
        target: ImageTarget,
        upload: ImageUpload,
    ): ImageUpdateResult {
        replacementTargets.add(target)
        replaceCalls++
        uploadGate?.await()
        replaceFailure?.let { throw it }
        val result =
            replaceResult
                ?: ImageUpdateResult(
                    ImageAttachment(target, "blob-$replaceCalls", Instant.EPOCH),
                    null,
                )
        attachment.value = result.attachment
        return result
    }

    /** Returns controlled removal after its optional gate completes.
     * @param target Requested image slot.
     */
    override suspend fun delete(target: ImageTarget): ImageUpdateResult {
        removalTargets.add(target)
        deleteCalls++
        deleteGate?.await()
        deleteFailure?.let { throw it }
        attachment.value = null
        return ImageUpdateResult(null, null)
    }

    /** Resolves a controlled display reference without network access.
     * @param blobId Requested immutable blob.
     */
    override suspend fun downloadUrl(blobId: String): String {
        downloads.add(blobId)
        urlFailures[blobId]?.let { throw it }
        return urlGates[blobId]?.await() ?: urlResults[blobId] ?: "file:///missing-fixture/$blobId"
    }

    /** Deliberately rejects cleanup from presentation callers.
     * @param blobId Blob the caller attempted to clean up.
     */
    override suspend fun retryCleanup(blobId: String) {
        error("Presentation must not delete blobs directly")
    }
}
