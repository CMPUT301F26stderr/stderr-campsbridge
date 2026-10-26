package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState

/** Retains only the active root flow's navigation stores across configuration changes. */
class SessionNavigationOwner :
    ViewModel(),
    ViewModelStoreOwner {
    private var activeKey: String? = null
    val scopeKey: String? get() = activeKey
    private var store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    /** Selects one setup/account scope, releasing a former root flow completely.
     * @param key Setup/account identity, or null outside navigable session content.
     * @return Former scope key when its saved navigation state should be discarded.
     */
    private fun activate(key: String?): String? {
        if (key == activeKey) return null
        val previousKey = activeKey
        store.clear()
        store = ViewModelStore()
        activeKey = key
        return previousKey
    }

    /** Resolves a root outcome to its navigation lifetime.
     * @param session Current root state.
     * @return Retired scope whose saved navigation state can be discarded.
     */
    fun update(session: SessionState): String? =
        when (session) {
            is SessionState.Ready -> activate("ready/" + session.account.id)
            is SessionState.SetupRequired -> activate("setup/" + session.accountId)
            SessionState.Unavailable -> activate(null)
            SessionState.Initializing, SessionState.Failure -> null
        }

    /** Releases every saved navigation stack when the Android host is finally cleared. */
    override fun onCleared() {
        store.clear()
        super.onCleared()
    }
}
