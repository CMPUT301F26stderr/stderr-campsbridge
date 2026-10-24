package io.github.CMPUT301F26stderr.campsbridge.ui.common.images

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImagePublicationException
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns one image slot's preview, explicit writes, and transient display references.
 * Call actions on the parent dispatcher (normally Main); the parent owns its lifetime.
 * @param images Persistence operations; never raw Storage/document access.
 * @param target Required, immutable parent identity.
 * @param parentScope Owner lifetime and dispatcher, usually viewModelScope.
 */
class ImageSlotController(
    private val images: ImageRepository,
    private val target: ImageTarget,
    parentScope: CoroutineScope,
) : AutoCloseable {
    private val owner = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)
    private val mutableState = MutableStateFlow(ImageSlotState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private var urlJob: Job? = null
    private var urlGeneration = 0

    init {
        observeAttachment()
    }

    /**
     * Changes only the local preview. A cancelled picker leaves an earlier selection intact.
     * @param photo Selected content, or null for picker cancellation.
     */
    fun select(photo: SelectedPhoto?) {
        if (photo == null || !scope.isActive || !state.value.canChoose) return
        stopUrl()
        mutableState.value =
            state.value.copy(candidate = photo, failure = null, isResolvingUrl = false)
    }

    /** Discards only unsaved input, leaving the last known published attachment intact. */
    fun discard() {
        if (!scope.isActive || !state.value.canChoose) return
        mutableState.value = state.value.copy(candidate = null, failure = null)
        resolveIfNeeded()
    }

    /** Reports inaccessible picker content without removing the existing image or candidate. */
    fun selectionFailed() {
        if (!scope.isActive || !state.value.canChoose) return
        mutableState.value = state.value.copy(failure = ImageFailure.SELECTION)
    }

    /** Saves once, confirming publication only after the repository operation returns. */
    fun save() {
        val current = state.value
        val selected = current.candidate ?: return
        if (!scope.isActive || !current.canSave) return
        mutableState.value = current.copy(operation = ImageOperation.SAVING, failure = null)
        scope.launch {
            try {
                val result = images.replace(target, selected.upload)
                currentCoroutineContext().ensureActive()
                val published =
                    checkNotNull(result.attachment) { "Replacement did not return an attachment" }
                mutableState.value =
                    state.value.copy(operation = ImageOperation.IDLE, candidate = null)
                acceptAttachment(published)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (uncertain: ImagePublicationException) {
                currentCoroutineContext().ensureActive()
                mutableState.value =
                    state.value.copy(
                        operation = ImageOperation.IDLE,
                        unconfirmedBlobId = uncertain.uploadedBlobId,
                    )
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value =
                    state.value.copy(operation = ImageOperation.IDLE, failure = ImageFailure.SAVE)
            } finally {
                if (currentCoroutineContext().isActive) {
                    mutableState.value = state.value.copy(operation = ImageOperation.IDLE)
                }
            }
        }
    }

    /** Removes the saved attachment; retry is idempotent even if an earlier delete committed. */
    fun remove() {
        val current = state.value
        if (!scope.isActive || !current.canRemove) return
        mutableState.value = current.copy(operation = ImageOperation.REMOVING, failure = null)
        scope.launch {
            try {
                val result = images.delete(target)
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(operation = ImageOperation.IDLE)
                acceptAttachment(result.attachment)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value =
                    state.value.copy(operation = ImageOperation.IDLE, failure = ImageFailure.REMOVE)
            } finally {
                if (currentCoroutineContext().isActive) {
                    mutableState.value = state.value.copy(operation = ImageOperation.IDLE)
                }
            }
        }
    }

    /** Retries the failed stage; URL failures never cause reupload and ambiguity requires a read. */
    fun retry() {
        val current = state.value
        if (!scope.isActive || current.operation != ImageOperation.IDLE) return
        when {
            current.unconfirmedBlobId != null -> {
                reconcile(current.unconfirmedBlobId)
            }

            current.loadState == ImageLoadState.FAILED -> {
                observeAttachment()
            }

            current.failure == ImageFailure.SAVE -> {
                save()
            }

            current.failure == ImageFailure.REMOVE -> {
                remove()
            }

            current.displayFailed -> {
                mutableState.value = current.copy(displayFailed = false)
                resolveIfNeeded()
            }

            current.failure == null -> {
                observeAttachment()
            }
        }
    }

    /**
     * Confirms the attempted blob with a server read; a mismatch never authorizes deletion/reupload.
     * @param uploadedBlobId Uploaded object whose publication acknowledgement was lost.
     */
    private fun reconcile(uploadedBlobId: String) {
        mutableState.value = state.value.copy(operation = ImageOperation.RECONCILING)
        scope.launch {
            try {
                val confirmed = images.get(target)
                currentCoroutineContext().ensureActive()
                if (confirmed?.blobId == uploadedBlobId) {
                    mutableState.value =
                        state.value.copy(candidate = null, unconfirmedBlobId = null, failure = null)
                }
                mutableState.value = state.value.copy(operation = ImageOperation.IDLE)
                acceptAttachment(confirmed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(operation = ImageOperation.IDLE)
            } finally {
                if (currentCoroutineContext().isActive) {
                    mutableState.value = state.value.copy(operation = ImageOperation.IDLE)
                }
            }
        }
    }

    /** Replaces the attachment subscription while keeping existing facts and input visible. */
    private fun observeAttachment() {
        observation?.cancel()
        mutableState.value = state.value.copy(loadState = ImageLoadState.LOADING)
        observation =
            scope.launch {
                try {
                    images.observe(target).collect { attachment ->
                        // A listener can emit before a write is acknowledged; the operation owns success.
                        if (state.value.operation ==
                            ImageOperation.IDLE
                        ) {
                            acceptAttachment(attachment)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    mutableState.value = state.value.copy(loadState = ImageLoadState.FAILED)
                }
            }
    }

    /**
     * Updates known attachment facts without discarding a selection or clearing action failures.
     * @param attachment Observed or explicitly confirmed attachment.
     */
    private fun acceptAttachment(attachment: ImageAttachment?) {
        val changed = state.value.attachment?.blobId != attachment?.blobId
        if (changed) stopUrl()
        mutableState.value =
            state.value.copy(
                loadState = ImageLoadState.READY,
                attachment = attachment,
                displayUrl = if (changed || attachment == null) null else state.value.displayUrl,
                isResolvingUrl =
                    if (changed ||
                        attachment == null
                    ) {
                        false
                    } else {
                        state.value.isResolvingUrl
                    },
                displayFailed =
                    if (changed ||
                        attachment == null
                    ) {
                        false
                    } else {
                        state.value.displayFailed
                    },
            )
        resolveIfNeeded()
    }

    /** Resolves only a currently needed reference and rejects superseded URL completions. */
    private fun resolveIfNeeded() {
        val current = state.value
        val attachment = current.attachment ?: return
        if (current.candidate != null || current.displayUrl != null || current.isResolvingUrl ||
            current.displayFailed
        ) {
            return
        }
        stopUrl()
        val request = urlGeneration
        mutableState.value = current.copy(isResolvingUrl = true)
        urlJob =
            scope.launch {
                try {
                    val url = images.downloadUrl(attachment.blobId)
                    currentCoroutineContext().ensureActive()
                    if (request == urlGeneration) {
                        mutableState.value =
                            state.value.copy(displayUrl = url, isResolvingUrl = false)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (request == urlGeneration) {
                        mutableState.value =
                            state.value.copy(displayFailed = true, isResolvingUrl = false)
                    }
                }
            }
    }

    /** Invalidates the previous display request without deleting or reuploading anything. */
    private fun stopUrl() {
        urlGeneration++
        urlJob?.cancel()
    }

    /** Releases this slot's work, also automatically cancelled when the parent is cleared. */
    override fun close() {
        scope.cancel()
    }
}
