package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.platform.identity.IdentityProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val mutableState = MutableStateFlow(AccountRemovalUiState())
    val state = mutableState.asStateFlow()

    /** Receives the root session while action implementation is pending.
     * @param session Current confirmed startup/profile outcome.
     */
    fun updateSession(session: SessionState) =
        Unit

    /** Declares the current-profile confirmation action before its implementation.
     * @param accountId Current ready profile identity.
     */
    fun request(accountId: String) =
        Unit

    /** Declares dismissal of an unsubmitted removal confirmation. */
    fun dismiss() =
        Unit

    /** Declares explicit confirmation of the current original-account request. */
    fun confirm() =
        Unit

    /** Declares explicit retry of retained cleanup. */
    fun retry() =
        Unit

    /** Declares read-only reinspection of a restored request. */
    fun retryRecovery() =
        Unit
}
