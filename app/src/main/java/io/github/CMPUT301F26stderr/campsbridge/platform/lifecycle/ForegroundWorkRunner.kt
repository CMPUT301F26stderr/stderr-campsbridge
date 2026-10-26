package io.github.CMPUT301F26stderr.campsbridge.platform.lifecycle

import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Gives one ready account foreground ownership of persistence recovery.
 * @param work Account-scoped persistence coordinator.
 * @param onFailure Nonthrowing diagnostic callback for retryable work failures.
 */
class ForegroundWorkRunner(
    private val work: PendingWork,
    private val onFailure: (Exception) -> Unit,
) {
    /**
     * Cancels recovery on background or account/capability changes; equal sessions are retained.
     * @param sessions Ready account, or null while initializing, setting up or unavailable.
     * @param foreground Whether the Android host is at least STARTED.
     */
    suspend fun run(
        sessions: Flow<WorkSession?>,
        foreground: Flow<Boolean>,
    ) {
        combine(sessions, foreground) { session, active -> session.takeIf { active } }
            .distinctUntilChanged()
            .collectLatest { session ->
                if (session != null) work.run(session, onFailure)
            }
    }
}
