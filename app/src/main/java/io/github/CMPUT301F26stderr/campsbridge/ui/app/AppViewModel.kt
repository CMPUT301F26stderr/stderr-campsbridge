package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.platform.identity.IdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAccountProgress
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Owns startup and the confirmed workspace; performs no profile writes or global maintenance.
 * @param identity Established/restored device identity.
 * @param accounts Shared profile observations.
 * @param preferences Account-keyed installation-local state.
 */
class AppViewModel(
    private val identity: IdentityProvider,
    private val accounts: AccountRepository,
    private val preferences: LocalAppPreferences,
) : ViewModel() {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Initializing)
    val state = mutableState.asStateFlow()
    private var bootstrapJob: Job? = null
    private var selectionJob: Job? = null
    private var generation = 0
    private var accountId: String? = null
    private var account: Account? = null
    private var progress: LocalAccountProgress? = null
    private var selecting = false
    private var selectionFailed = false

    init {
        retry()
    }

    /** Starts or retries startup, ignoring repeated requests during an active observation. */
    fun retry() {
        if (bootstrapJob?.isActive == true) return
        val attempt = ++generation
        selectionJob?.cancel()
        selecting = false
        selectionFailed = false
        account = null
        progress = null
        accountId = null
        mutableState.value = SessionState.Initializing
        bootstrapJob =
            viewModelScope.launch {
                try {
                    val id = identity.establishIdentity()
                    check(id.isNotBlank()) { "Identity must not be blank" }
                    accountId = id
                    combine(accounts.observe(id), preferences.observe(id)) { record, local ->
                        record to
                            local
                    }.collect { (record, local) ->
                        account = record
                        progress = local
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation == attempt) {
                        generation++
                        selectionJob?.cancel()
                        mutableState.value = SessionState.Failure
                    }
                }
            }
    }

    /**
     * Persists a permitted choice before publishing it; a late result is revalidated.
     * @param role Requested workspace, accepted only from current capabilities.
     */
    fun selectWorkspace(role: Role) {
        val ready = state.value as? SessionState.Ready ?: return
        if (selecting || role !in ready.account.roles || role == ready.workspace) return
        val id = accountId ?: return
        val attempt = generation
        selecting = true
        selectionFailed = false
        publish()
        selectionJob =
            viewModelScope.launch {
                try {
                    preferences.setWorkspace(id, role)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation == attempt) selectionFailed = true
                } finally {
                    if (generation == attempt) {
                        selecting = false
                        publish()
                    }
                }
            }
    }

    /** Maps the latest jointly resolved profile/progress without creating a profile. */
    private fun publish() {
        val id = accountId ?: return
        val local = progress ?: return
        val record = account
        mutableState.value =
            when {
                record == null -> {
                    SessionState.SetupRequired(id)
                }

                !record.isActive -> {
                    SessionState.Unavailable
                }

                local.setup == SetupProgress.IN_PROGRESS -> {
                    SessionState.SetupRequired(id, record)
                }

                else -> {
                    val permitted = permittedWorkspace(record.roles, local.workspace)
                    if (permitted == null) {
                        SessionState.Unavailable
                    } else {
                        SessionState.Ready(record, permitted, selecting, selectionFailed)
                    }
                }
            }
    }

    /** Invalidates pending completions when the lifecycle owner releases this model. */
    override fun onCleared() {
        generation++
        super.onCleared()
    }
}
