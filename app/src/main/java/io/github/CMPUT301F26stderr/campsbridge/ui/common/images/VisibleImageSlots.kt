package io.github.CMPUT301F26stderr.campsbridge.ui.common.images

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns visible attachment displays independently of the parent list source and its membership.
 * All calls use the owning ViewModel's main-thread scope; closing does not cancel that scope.
 * @param images Current attachment and transient URL source.
 * @param scope Parent-owned observation lifetime.
 */
internal class VisibleImageSlots(
    private val images: ImageRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<Map<ImageTarget, ImageSlotState>>(emptyMap())
    val state = mutableState.asStateFlow()
    private val slots = mutableMapOf<ImageTarget, ImageSlotController>()
    private val jobs = mutableMapOf<ImageTarget, Job>()
    private var closed = false

    /** Keeps one reader per distinct currently permitted visible attachment target.
     * @param targets Currently permitted visible slot identities.
     */
    fun show(targets: Set<ImageTarget>) {
        if (closed) return
        for (id in slots.keys.toList()) {
            if (id !in targets) {
                jobs.remove(id)?.cancel()
                slots.remove(id)?.close()
            }
        }
        val pending = mutableListOf<Job>()
        for (id in targets) {
            if (id in slots) continue
            val slot = ImageSlotController(images, id, scope)
            slots[id] = slot
            val job =
                scope.launch(start = CoroutineStart.LAZY) {
                    slot.state.collect {
                        if (!closed && slots[id] === slot) publish()
                    }
                }
            jobs[id] = job
            pending += job
        }
        publish()
        pending.forEach { it.start() }
    }

    /** Retries only the current visible photo's failed read, never a mutation.
     * @param target Current visible slot identity.
     */
    fun retry(target: ImageTarget) {
        if (!closed) slots[target]?.retryDisplay()
    }

    /** Publishes immutable image snapshots without triggering the parent source projection. */
    private fun publish() {
        mutableState.value = slots.mapValues { it.value.state.value }
    }

    /** Retires all child readers while leaving the supplied parent scope untouched. */
    fun close() {
        if (closed) return
        closed = true
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        slots.values.forEach { it.close() }
        slots.clear()
        mutableState.value = emptyMap()
    }
}
