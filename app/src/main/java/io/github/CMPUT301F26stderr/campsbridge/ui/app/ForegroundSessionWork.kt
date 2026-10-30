package io.github.CMPUT301F26stderr.campsbridge.ui.app

import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Composes alert initialization/observation with the existing session's durable due work.
 * @param pendingWork Existing persistence owner; its scheduling rules remain unchanged.
 * @param alerts Confirmed-history and independent alert coordination for the same session.
 */
class ForegroundSessionWork(
    private val pendingWork: PendingWork,
    private val alerts: NotificationAlertCoordinator,
) : PendingWork {
    /** Establishes history before normal startup, then retains both cancellable lanes.
     * @param session Same ready identity/capabilities passed to the existing foreground runner.
     * @param onFailure Nonthrowing diagnostics; failed alerts must not stop durable delivery.
     */
    override suspend fun run(
        session: WorkSession,
        onFailure: (Exception) -> Unit,
    ) {
        coroutineScope {
            val initialIds =
                try {
                    alerts.prepareHistory(session.accountId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    currentCoroutineContext().ensureActive()
                    onFailure(failure)
                    null
                }
            currentCoroutineContext().ensureActive()
            launch { alerts.run(session, initialIds, onFailure) }
            pendingWork.run(session, onFailure)
            awaitCancellation()
        }
    }
}
