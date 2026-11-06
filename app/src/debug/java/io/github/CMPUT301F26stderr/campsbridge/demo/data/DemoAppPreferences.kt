/*
 * Scenario-local progress; never reads or changes the Firebase installation's DataStore.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.demo.DemoScenario
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAccountProgress
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress

/** Scenario-local progress; never reads or changes the Firebase installation's DataStore.
 * @param initial Starting progress indexed by demo account.
 * @param store Shared durable local state.
 * @param scenario Default workspace/setup for this person.
 */
class DemoAppPreferences(
    initial: Map<String, LocalAccountProgress>,
    private val store: DemoStore = DemoStore(),
    private val scenario: DemoScenario? = null,
) : LocalAppPreferences {
    private val defaults = initial

    /** Restores this person's progress without changing any other demo identity.
     * @param accountId Stable person.
     */
    private fun DemoDocuments.progress(accountId: String): LocalAccountProgress {
        val saved = raw["_demo/progress/$accountId"]
        return saved?.let {
            LocalAccountProgress(
                SetupProgress.valueOf(it["setup"] as String),
                (it["workspace"] as? String)?.let(Role::valueOf),
            )
        }
            ?: defaults[accountId]
            ?: if (scenario != null &&
                scenario != DemoScenario.NEW_ACCOUNT
            ) {
                LocalAccountProgress(SetupProgress.COMPLETED, scenario.workspace)
            } else {
                LocalAccountProgress()
            }
    }

    /** Observes one account's current local choices.
     * @param accountId Demo identity.
     */
    override fun observe(accountId: String) =
        store.observe { progress(accountId) }

    /** Changes only the selected account's setup marker.
     * @param accountId Demo identity.
     * @param progress Requested marker.
     */
    override suspend fun setSetupProgress(
        accountId: String,
        progress: SetupProgress,
    ) {
        store.change {
            val changed = progress(accountId).copy(setup = progress)
            raw["_demo/progress/$accountId"] =
                mapOf("setup" to changed.setup.name, "workspace" to changed.workspace?.name)
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
        store.change {
            val changed = progress(accountId).copy(workspace = workspace)
            raw["_demo/progress/$accountId"] =
                mapOf("setup" to changed.setup.name, "workspace" to changed.workspace?.name)
        }
    }
}
