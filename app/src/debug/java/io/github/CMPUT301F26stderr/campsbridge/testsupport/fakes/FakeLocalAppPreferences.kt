package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAccountProgress
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Controlled local reads/writes; each instance starts with fresh account-local values. */
class FakeLocalAppPreferences : LocalAppPreferences {
    private val values = mutableMapOf<String, MutableStateFlow<LocalAccountProgress>>()
    val progress = values.getOrPut("account-1") { MutableStateFlow(LocalAccountProgress()) }
    var readGate: CompletableDeferred<Unit>? = null
    var writeGate: CompletableDeferred<Unit>? = null
    var readFailure: Exception? = null
    var writeFailure: Exception? = null
    var workspaceWrites = 0
    var setupFailureOn: SetupProgress? = null
    var setupFailure: Exception? = null

    /** Emits controlled values only after the optional read gate resolves.
     * @param accountId Identity owning the local state.
     */
    override fun observe(accountId: String): Flow<LocalAccountProgress> =
        flow {
            readGate?.await()
            readFailure?.let { throw it }
            emitAll(values.getOrPut(accountId) { MutableStateFlow(LocalAccountProgress()) })
        }

    /** Confirms setup only after controlled persistence completes.
     * @param accountId Identity owning the local state.
     * @param progress New wizard marker.
     */
    override suspend fun setSetupProgress(
        accountId: String,
        progress: SetupProgress,
    ) {
        writeGate?.await()
        writeFailure?.let { throw it }
        if (progress == setupFailureOn) setupFailure?.let { throw it }
        val state = values.getOrPut(accountId) { MutableStateFlow(LocalAccountProgress()) }
        state.value = state.value.copy(setup = progress)
    }

    /** Confirms a workspace only after controlled persistence completes.
     * @param accountId Identity owning the local state.
     * @param workspace Selected capability.
     */
    override suspend fun setWorkspace(
        accountId: String,
        workspace: Role,
    ) {
        workspaceWrites++
        writeGate?.await()
        writeFailure?.let { throw it }
        val state = values.getOrPut(accountId) { MutableStateFlow(LocalAccountProgress()) }
        state.value = state.value.copy(workspace = workspace)
    }
}
