/*
 * Owns one root removal beyond profile/session navigation disposal.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.platform.identity.IdentityProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns one root removal beyond profile/session navigation disposal.
 * @param identity Existing device identity source, including restored identity.
 * @param accounts Original target's saved removal fact for restored recovery.
 * @param moderation Existing account-marking and dependent-cleanup operation.
 * @param savedState Original confirmed target/actor primitives.
 */
class AccountRemovalViewModel(
    private val identity: IdentityProvider,
    private val accounts: AccountRepository,
    private val moderation: ModerationRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val restored =
        savedState.get<String>(TARGET)?.let { target ->
            savedState.get<String>(ACTOR)?.let { AccountRemovalRequest(target, it) }
        }
    private val mutableState =
        MutableStateFlow(
            AccountRemovalUiState(
                phase = if (restored == null) AccountRemovalPhase.IDLE else AccountRemovalPhase.RESTORING,
                request = restored,
            ),
        )
    val state = mutableState.asStateFlow()
    private var session: SessionState = SessionState.Initializing

    init {
        restored?.let(::checkRecovery)
    }

    /** Supplies actual root session facts without rebinding a retained request.
     * @param session Current confirmed startup/profile outcome.
     */
    fun updateSession(session: SessionState) {
        this.session = session
        val request = state.value.request
        if (state.value.phase == AccountRemovalPhase.CONFIRMING &&
            (session !is SessionState.Ready || session.account.id != request?.actorId)
        ) {
            mutableState.value = AccountRemovalUiState()
        }
        if (state.value.phase == AccountRemovalPhase.COMPLETE && session is SessionState.Ready &&
            session.account.id != request?.accountId
        ) {
            mutableState.value = AccountRemovalUiState()
        }
    }

    /** Requests confirmation for the actual current account without writing.
     * @param accountId Current ready profile identity.
     */
    fun request(accountId: String) {
        val current = session as? SessionState.Ready ?: return
        if (state.value.phase !in setOf(AccountRemovalPhase.IDLE, AccountRemovalPhase.NOT_STARTED) ||
            !current.account.isActive || current.account.id != accountId
        ) {
            return
        }
        val old = state.value.request
        if (old != null && old != AccountRemovalRequest(accountId, accountId)) return
        mutableState.value =
            AccountRemovalUiState(AccountRemovalPhase.CONFIRMING, AccountRemovalRequest(accountId, accountId))
    }

    /** Dismisses an unsubmitted confirmation or a request proved not to have started. */
    fun dismiss() {
        if (state.value.phase !in
            setOf(AccountRemovalPhase.CONFIRMING, AccountRemovalPhase.NOT_STARTED)
        ) {
            return
        }
        clearRetained()
        mutableState.value = AccountRemovalUiState()
    }

    /** Captures one original same-actor request before any suspension. */
    fun confirm() {
        if (state.value.phase != AccountRemovalPhase.CONFIRMING) return
        val current = session as? SessionState.Ready ?: return
        val request = state.value.request ?: return
        if (current.account.id != request.actorId || !current.account.isActive ||
            request.accountId != request.actorId
        ) {
            return
        }
        savedState[TARGET] = request.accountId
        savedState[ACTOR] = request.actorId
        remove(request)
    }

    /** Explicitly retries only an eligible retained cleanup. */
    fun retry() {
        if (state.value.phase == AccountRemovalPhase.RETRY) state.value.request?.let(::remove)
    }

    /** Rechecks restored identity/removal facts without automatically invoking removal. */
    fun retryRecovery() {
        if (state.value.phase in setOf(AccountRemovalPhase.RECOVERY_FAILED, AccountRemovalPhase.UNAVAILABLE)) {
            state.value.request?.let(::checkRecovery)
        }
    }

    /** Revalidates actual identity before repeating the original idempotent account operation.
     * @param request Original confirmed target and actor.
     */
    private fun remove(request: AccountRemovalRequest) {
        mutableState.value = state.value.copy(phase = AccountRemovalPhase.REMOVING)
        viewModelScope.launch {
            try {
                val actual = identity.establishIdentity()
                currentCoroutineContext().ensureActive()
                if (actual != request.actorId || request.accountId != request.actorId) {
                    mutableState.value = state.value.copy(phase = AccountRemovalPhase.UNAVAILABLE)
                    return@launch
                }
                moderation.removeAccount(request.accountId, request.actorId)
                currentCoroutineContext().ensureActive()
                clearRetained()
                mutableState.value = AccountRemovalUiState(AccountRemovalPhase.COMPLETE, request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = AccountRemovalUiState(AccountRemovalPhase.RETRY, request)
            }
        }
    }

    /** Establishes original saved removal facts before offering an explicit restored Retry.
     * @param request Retained original request; another account never substitutes for it.
     */
    private fun checkRecovery(request: AccountRemovalRequest) {
        mutableState.value = AccountRemovalUiState(AccountRemovalPhase.RESTORING, request)
        viewModelScope.launch {
            try {
                val actual = identity.establishIdentity()
                currentCoroutineContext().ensureActive()
                if (actual != request.actorId || request.accountId != request.actorId) {
                    mutableState.value = AccountRemovalUiState(AccountRemovalPhase.UNAVAILABLE, request)
                    return@launch
                }
                val account = accounts.get(request.accountId)
                currentCoroutineContext().ensureActive()
                val phase =
                    when {
                        account?.id != request.accountId -> AccountRemovalPhase.UNAVAILABLE
                        account.removal?.actorId == request.actorId -> AccountRemovalPhase.RETRY
                        account.isActive -> AccountRemovalPhase.NOT_STARTED
                        else -> AccountRemovalPhase.UNAVAILABLE
                    }
                mutableState.value = AccountRemovalUiState(phase, request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = AccountRemovalUiState(AccountRemovalPhase.RECOVERY_FAILED, request)
            }
        }
    }

    /** Retires local recovery identity only after completion or an unsubmitted cancellation. */
    private fun clearRetained() {
        savedState.remove<String>(TARGET)
        savedState.remove<String>(ACTOR)
    }

    /** Original target/actor primitives survive root reconstruction without replaying writes. */
    private companion object {
        const val TARGET = "accountRemovalTarget"
        const val ACTOR = "accountRemovalActor"
    }
}
