package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAccountProgress
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Scenario-local progress; never reads or changes the Firebase installation's DataStore.
 * @param initial Starting progress indexed by demo account.
 */
class DemoAppPreferences(
    initial: Map<String, LocalAccountProgress>,
) : LocalAppPreferences {
    private val records = MutableStateFlow(initial)

    /** Observes one account's current local choices.
     * @param accountId Demo identity.
     */
    override fun observe(accountId: String) =
        records.map { it[accountId] ?: LocalAccountProgress() }.distinctUntilChanged()

    /** Changes only the selected account's setup marker.
     * @param accountId Demo identity.
     * @param progress Requested marker.
     */
    override suspend fun setSetupProgress(
        accountId: String,
        progress: SetupProgress,
    ) {
        records.update { current ->
            current +
                (accountId to (current[accountId] ?: LocalAccountProgress()).copy(setup = progress))
        }
    }

    /** Changes only the selected account's workspace preference.
     * @param accountId Demo identity.
     * @param workspace Requested workspace, validated by the controller.
     */
    override suspend fun setWorkspace(
        accountId: String,
        workspace: Role,
    ) {
        records.update { current ->
            current +
                (
                    accountId to
                        (current[accountId] ?: LocalAccountProgress()).copy(workspace = workspace)
                )
        }
    }
}
