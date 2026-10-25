package io.github.CMPUT301F26stderr.campsbridge.ui.common.time

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Suspends until the injected clock reaches a deadline; cancellation propagates.
 * Callers own the coroutine and recalculate their next deadline after returning.
 * @param clock Application or controlled test time source.
 * @param deadline Inclusive instant for refreshing derived state, never a write instruction.
 */
suspend fun awaitDeadline(
    clock: Clock,
    deadline: Instant,
) {
    while (true) {
        currentCoroutineContext().ensureActive()
        val now = clock.instant()
        if (now >= deadline) return
        delay(Duration.between(now, deadline).toMillis().coerceAtLeast(1))
    }
}
