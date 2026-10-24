package io.github.CMPUT301F26stderr.campsbridge.platform.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stores only account-keyed local progress and workspace selection; failures propagate.
 * @param store One application-scoped Preferences DataStore.
 */
class DataStoreAppPreferences(
    private val store: DataStore<Preferences>,
) : LocalAppPreferences {
    /**
     * Observes confirmed local values. Unknown enum values fail rather than guess progress.
     * @param accountId Established identity owning these values.
     */
    override fun observe(accountId: String): Flow<LocalAccountProgress> =
        store.data.map { values ->
            LocalAccountProgress(
                setup =
                    values[setupKey(accountId)]?.let { SetupProgress.valueOf(it) }
                        ?: SetupProgress.NOT_STARTED,
                workspace = values[workspaceKey(accountId)]?.let { Role.valueOf(it) },
            )
        }

    /**
     * Returns after the wizard marker has been persisted, retaining the workspace key.
     * @param accountId Established identity owning these values.
     * @param progress New wizard marker.
     */
    override suspend fun setSetupProgress(
        accountId: String,
        progress: SetupProgress,
    ) {
        store.edit { it[setupKey(accountId)] = progress.name }
    }

    /**
     * Returns after the selection has been persisted, retaining the wizard marker.
     * @param accountId Established identity owning these values.
     * @param workspace Caller-validated capability preference.
     */
    override suspend fun setWorkspace(
        accountId: String,
        workspace: Role,
    ) {
        store.edit { it[workspaceKey(accountId)] = workspace.name }
    }

    /**
     * Identifies one account's wizard marker.
     * @param id Established identity.
     */
    private fun setupKey(id: String): Preferences.Key<String> =
        stringPreferencesKey("account.$id.setup")

    /**
     * Identifies one account's workspace preference.
     * @param id Established identity.
     */
    private fun workspaceKey(id: String): Preferences.Key<String> =
        stringPreferencesKey("account.$id.workspace")
}
