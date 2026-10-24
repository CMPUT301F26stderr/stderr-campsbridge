package io.github.CMPUT301F26stderr.campsbridge.platform.preferences

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import kotlinx.coroutines.flow.Flow

/** Local wizard progress; a profile may exist while setup is still unfinished. */
enum class SetupProgress { NOT_STARTED, IN_PROGRESS, COMPLETED }

/**
 * Installation-local state, independent for each account.
 * @param setup Persisted wizard progress.
 * @param workspace Last selected capability; never grants a role.
 */
data class LocalAccountProgress(
    val setup: SetupProgress = SetupProgress.NOT_STARTED,
    val workspace: Role? = null,
)

/** Owns only local wizard progress and workspace preference, not shared profile facts. */
interface LocalAppPreferences {
    /**
     * Observes confirmed local state and propagates read errors.
     * @param accountId Established identity.
     */
    fun observe(accountId: String): Flow<LocalAccountProgress>

    /** Saves progress, returning only after the write succeeds.
     * @param accountId Established identity.
     * @param progress New setup marker.
     */
    suspend fun setSetupProgress(
        accountId: String,
        progress: SetupProgress,
    )

    /** Saves a preference without changing setup progress.
     * @param accountId Established identity.
     * @param workspace Chosen capability, validated by the caller.
     */
    suspend fun setWorkspace(
        accountId: String,
        workspace: Role,
    )
}
