package io.github.CMPUT301F26stderr.campsbridge.domain.maintenance

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role

/** Capabilities of an active, setup-complete account; independent of the selected UI workspace.
 * @param accountId Established account identity.
 * @param roles All current account capabilities.
 */
data class WorkSession(
    val accountId: String,
    val roles: Set<Role>,
)

/** Foreground persistence coordination without SDK paths or Android lifecycle dependencies. */
interface PendingWork {
    /**
     * Observes relevant work and processes due operations until its caller cancels the session.
     * Independent failures are reported and retried; cancellation must propagate.
     * @param session Active account whose capabilities determine discoverable work.
     * @param onFailure Nonthrowing diagnostic callback; a failure is never reported as success.
     */
    suspend fun run(
        session: WorkSession,
        onFailure: (Exception) -> Unit,
    )
}
