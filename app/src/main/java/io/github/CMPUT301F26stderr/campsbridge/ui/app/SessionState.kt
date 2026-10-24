package io.github.CMPUT301F26stderr.campsbridge.ui.app

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role

/** Root session outcomes; identity alone does not imply a completed account. */
sealed interface SessionState {
    /** Identity or required reads are pending. */
    data object Initializing : SessionState

    /** First-time or interrupted setup needs the onboarding flow.
     * @param accountId Established identity.
     * @param account Existing active profile, retained for accessibility during interrupted setup.
     */
    data class SetupRequired(
        val accountId: String,
        val account: Account? = null,
    ) : SessionState

    /** An active account and confirmed workspace.
     * @param account Current shared profile.
     * @param workspace Selected permitted capability.
     * @param isSelectingWorkspace A local selection write is pending.
     * @param selectionFailed The last selection failed; current choice is retained.
     */
    data class Ready(
        val account: Account,
        val workspace: Role,
        val isSelectingWorkspace: Boolean = false,
        val selectionFailed: Boolean = false,
    ) : SessionState

    /** Removed accounts have no active feature actions. */
    data object Unavailable : SessionState

    /** Startup failed and can be explicitly retried. */
    data object Failure : SessionState
}
