/*
 * Reads captured recipients only for the currently displayed audience page.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Reads captured recipients only for the currently displayed audience page.
 * @param notifications Existing server audience query, never receipt or delivery mutations.
 * @param scope Parent log's coroutine lifetime.
 */
internal class NotificationLogAudienceReader(
    private val notifications: NotificationRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<NotificationLogAudienceState>(NotificationLogAudienceState.Idle)
    val state = mutableState.asStateFlow()
    private var id: String? = null
    private var job: Job? = null
    private var version = 0L
    private var closed = false

    /** Replaces the visible identity after invalidating the previous read.
     * @param notificationId Exact selected event, or null when the audience page leaves.
     */
    fun show(notificationId: String?) {
        if (closed || id == notificationId) return
        version++
        job?.cancel()
        job = null
        id = notificationId
        mutableState.value = NotificationLogAudienceState.Idle
        if (notificationId != null) load(notificationId)
    }

    /** Reloads the current audience after completion or interruption, coalescing active requests. */
    fun retry() {
        val selected = id ?: return
        if (!closed && job?.isActive != true &&
            state.value !is NotificationLogAudienceState.Idle
        ) {
            load(selected)
        }
    }

    /** Installs a generation guard before asking for the recorded audience.
     * @param selected Original event identity owned by this page.
     */
    private fun load(selected: String) {
        val token = ++version
        mutableState.value = NotificationLogAudienceState.Loading(selected)
        val pending =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val audience = notifications.getAudience(selected)
                    currentCoroutineContext().ensureActive()
                    if (!closed && version == token && id == selected) {
                        mutableState.value = NotificationLogAudienceState.Content(selected, audience)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!closed && version == token && id == selected) {
                        mutableState.value = NotificationLogAudienceState.Failure(selected)
                    }
                } finally {
                    if (version == token) job = null
                }
            }
        job = pending
        pending.start()
    }

    /** Retires callbacks before cancelling the graph's last audience request. */
    fun close() {
        closed = true
        version++
        job?.cancel()
    }
}
