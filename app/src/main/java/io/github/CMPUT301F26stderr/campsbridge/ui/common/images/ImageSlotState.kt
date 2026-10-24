package io.github.CMPUT301F26stderr.campsbridge.ui.common.images

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto

/** Attachment reads must distinguish unresolved, confirmed, and failed results. */
enum class ImageLoadState { LOADING, READY, FAILED }

/** Only one mutation/reconciliation may be pending in an image slot. */
enum class ImageOperation { IDLE, SAVING, REMOVING, RECONCILING }

/** User actions whose recoverable failure preserves the previous image and selection. */
enum class ImageFailure { SAVE, REMOVE, SELECTION }

/**
 * Image presentation facts and pending work; URLs and selections remain transient.
 * @param loadState Attachment read outcome.
 * @param attachment Last known published attachment, not an unsaved candidate.
 * @param displayUrl Transient resolved display reference for that attachment.
 * @param isResolvingUrl A display URL is pending.
 * @param displayFailed URL resolution failed, separately from attachment absence.
 * @param candidate Unsaved local selection.
 * @param operation Pending explicit action.
 * @param failure Recoverable user-action failure.
 * @param unconfirmedBlobId Uploaded blob awaiting server reconciliation.
 */
data class ImageSlotState(
    val loadState: ImageLoadState = ImageLoadState.LOADING,
    val attachment: ImageAttachment? = null,
    val displayUrl: String? = null,
    val isResolvingUrl: Boolean = false,
    val displayFailed: Boolean = false,
    val candidate: SelectedPhoto? = null,
    val operation: ImageOperation = ImageOperation.IDLE,
    val failure: ImageFailure? = null,
    val unconfirmedBlobId: String? = null,
) {
    /** A new local choice is safe when no mutation or confirmation is pending. */
    val canChoose: Boolean get() = operation == ImageOperation.IDLE && unconfirmedBlobId == null

    /** Saving additionally requires known attachment state and an unsaved candidate. */
    val canSave: Boolean get() = canChoose && loadState == ImageLoadState.READY && candidate != null

    /** Removing additionally requires a saved image without unsaved input. */
    val canRemove: Boolean get() =
        canChoose && loadState == ImageLoadState.READY &&
            candidate == null &&
            attachment != null
}
