package io.github.CMPUT301F26stderr.campsbridge.platform.images

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload

/**
 * Transient local selection; bytes remain lazy and are never saved in UI state.
 * @param previewReference Local display reference valid for the current app session.
 * @param upload Metadata and fresh-stream factory consumed only by an explicit save.
 */
data class SelectedPhoto(
    val previewReference: String,
    val upload: ImageUpload,
)
