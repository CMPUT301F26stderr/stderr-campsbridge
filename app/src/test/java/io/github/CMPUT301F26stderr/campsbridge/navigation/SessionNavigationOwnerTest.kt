package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies navigation retention and cleanup independently of feature controllers. */
class SessionNavigationOwnerTest {
    /** Returning to the same root scope keeps saved-stack model stores. */
    @Test fun sameScopeRetainsItsStore() {
        val owner = SessionNavigationOwner()
        owner.update(ready("account-1"))
        val store = owner.viewModelStore
        val child = Child().also { store.put("child", it) }
        owner.update(ready("account-1"))
        assertSame(store, owner.viewModelStore)
        assertFalse(child.cleared)
        store.clear()
    }

    /** Setup completion, identity changes and unavailable roots clear the previous scope. */
    @Test fun changedRootScopeClearsFormerStore() {
        val owner = SessionNavigationOwner()
        for (session in listOf(
            SessionState.SetupRequired("account-1"),
            ready("account-1"),
            ready("account-2"),
            SessionState.Unavailable,
        )) {
            val previous = owner.viewModelStore
            val child = Child().also { previous.put("child", it) }
            owner.update(session)
            assertTrue(child.cleared)
            assertNotSame(previous, owner.viewModelStore)
        }
        owner.viewModelStore.clear()
    }

    /** Clearing the host also clears its retained navigation stores. */
    @Test fun clearingHostReleasesNavigationScope() {
        val owner = SessionNavigationOwner()
        val host = ViewModelStore().apply { put("navigation", owner) }
        owner.update(ready("account-1"))
        val child = Child().also { owner.viewModelStore.put("child", it) }
        host.clear()
        assertTrue(child.cleared)
    }

    /** Unknown startup/read outcomes retain the active account's drafts and completions. */
    @Test fun transientFailureAndRetryKeepTheCurrentScope() {
        val owner = SessionNavigationOwner()
        val ready = SessionState.Ready(sampleAccount(), Role.ENTRANT)
        owner.update(ready)
        val store = owner.viewModelStore
        val child = Child().also { store.put("child", it) }
        owner.update(SessionState.Failure)
        owner.update(SessionState.Initializing)
        owner.update(ready)
        assertSame(store, owner.viewModelStore)
        assertFalse(child.cleared)
        owner.update(SessionState.Unavailable)
        assertTrue(child.cleared)
    }

    /** Supplies a resolved account scope without platform dependencies.
     * @param id Stable account fixture identity.
     */
    private fun ready(id: String): SessionState.Ready =
        SessionState.Ready(sampleAccount().copy(id = id), Role.ENTRANT)

    /** Minimal lifecycle witness; it has no feature logic or repository dependency. */
    private class Child : ViewModel() {
        var cleared = false

        /** Records disposal by the owning store. */
        override fun onCleared() {
            cleared = true
        }
    }
}
