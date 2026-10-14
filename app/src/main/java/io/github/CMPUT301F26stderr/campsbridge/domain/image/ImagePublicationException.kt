package io.github.CMPUT301F26stderr.campsbridge.domain.image

/**
 * Upload succeeded, but publication acknowledgement failed. Reconcile the attachment before
 * cleanup.
 *
 * @param target Account or campsite image slot.
 * @param uploadedBlobId Uploaded blob ID to inspect before cleanup or retry.
 * @param cause Underlying failure retained for diagnosis.
 */
class ImagePublicationException(
    val target: ImageTarget,
    val uploadedBlobId: String,
    cause: Throwable,
) : Exception("Image publication needs reconciliation", cause)
