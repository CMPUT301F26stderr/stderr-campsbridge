package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.VisibleImageSlots
import kotlinx.coroutines.CoroutineScope

/** Parent-owned read-only image inspection lifetime shared by browse displays. */
class AdminInspectionController(images: ImageRepository, scope: CoroutineScope) {
    private val slots = VisibleImageSlots(images, scope)
    val photos = slots.state
    fun show(targets: Set<ImageTarget>) = slots.show(targets)
    fun retry(target: ImageTarget) = slots.retry(target)
    fun close() = slots.close()
}
