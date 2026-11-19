package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeIdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** AR-01 through AR-03 keep confirmed cleanup alive and bind restored recovery to its original actor. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountRemovalViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val actor = CampingFixtures.account("account-1")
    private val removed = actor.copy(removal = Removal(CampingFixtures.now, actor.id))
    private val accounts = FakeAccountRepository(actor)
    private val identity = FakeIdentityProvider()
    private val moderation = FakeModerationRepository()
    private val calls = mutableListOf<Pair<String, String>>()
    private val store = ViewModelStore()

    /** AR-01: confirmation/dismissal do not write; one Confirm captures the original actor and target. */
    @Test fun confirmationIsExplicitAndPendingIdentityCannotBeReplaced() =
        test {
            val gate = CompletableDeferred<Unit>()
            moderation.removeAccountHandler = { target, sender, _ ->
                calls += target to sender
                gate.await()
            }
            val model = model()
            model.updateSession(SessionState.Ready(actor, Role.ENTRANT))
            model.request(actor.id)
            assertEquals(AccountRemovalPhase.CONFIRMING, model.state.value.phase)
            assertTrue(calls.isEmpty())
            model.dismiss()
            assertEquals(AccountRemovalPhase.IDLE, model.state.value.phase)
            model.request(actor.id)
            model.confirm()
            model.confirm()
            runCurrent()
            val original = AccountRemovalRequest(actor.id, actor.id)
            assertEquals(original, model.state.value.request)
            assertEquals(listOf(actor.id to actor.id), calls)
            model.updateSession(SessionState.Ready(actor.copy(id = "different-person"), Role.ENTRANT))
            model.request("different-person")
            assertEquals(original, model.state.value.request)
            assertEquals(AccountRemovalPhase.REMOVING, model.state.value.phase)
            gate.complete(Unit)
            runCurrent()
        }

    /** AR-02: an unavailable profile never settles pending cleanup; failures retain same-actor Retry. */
    @Test fun removedAccountObservationDoesNotCompleteOrCancelRootCleanup() =
        test {
            for (fails in listOf(false, true)) {
                accounts.account.value = actor
                calls.clear()
                val gate = CompletableDeferred<Unit>()
                moderation.removeAccountHandler = { target, sender, _ ->
                    calls += target to sender
                    accounts.account.value = removed
                    gate.await()
                }
                val model = model(key = "case-$fails")
                model.updateSession(SessionState.Ready(actor, Role.ENTRANT))
                model.request(actor.id)
                model.confirm()
                runCurrent()
                model.updateSession(SessionState.Unavailable)
                assertEquals(AccountRemovalPhase.REMOVING, model.state.value.phase)
                assertEquals(AccountRemovalRequest(actor.id, actor.id), model.state.value.request)
                if (fails) gate.completeExceptionally(IOException("cleanup offline")) else gate.complete(Unit)
                runCurrent()
                assertEquals(
                    if (fails) AccountRemovalPhase.RETRY else AccountRemovalPhase.COMPLETE,
                    model.state.value.phase,
                )
                if (fails) {
                    assertEquals(removed, accounts.account.value)
                    moderation.removeAccountHandler = { target, sender, _ -> calls += target to sender }
                    model.retry()
                    model.retry()
                    runCurrent()
                    assertEquals(List(2) { actor.id to actor.id }, calls)
                    assertEquals(AccountRemovalPhase.COMPLETE, model.state.value.phase)
                }
            }
        }

    /** AR-03: restored primitives require matching identity and a saved same-actor removal before Retry. */
    @Test fun restorationChecksOriginalRemovalFactsWithoutAutomaticallyReplaying() =
        test {
            val saved = SavedStateHandle()
            moderation.removeAccountHandler = { target, sender, _ ->
                calls += target to sender
                accounts.account.value = removed
                throw IOException("incomplete cleanup")
            }
            val first = model(saved = saved)
            first.updateSession(SessionState.Ready(actor, Role.ENTRANT))
            first.request(actor.id)
            first.confirm()
            runCurrent()
            val snapshot = saved.keys().associateWith { saved.get<Any?>(it) }
            store.clear()
            runCurrent()
            for (kind in 0..4) {
                calls.clear()
                accounts.readFailure = if (kind == 2) IOException("profile offline") else null
                accounts.account.value =
                    when (kind) {
                        3 -> actor
                        4 -> null
                        else -> removed
                    }
                identity.pending = CompletableDeferred(if (kind == 1) "different-person" else actor.id)
                val restored = model(key = "restored-$kind", saved = SavedStateHandle(snapshot))
                runCurrent()
                val expected =
                    when (kind) {
                        0 -> AccountRemovalPhase.RETRY
                        2 -> AccountRemovalPhase.RECOVERY_FAILED
                        3 -> AccountRemovalPhase.NOT_STARTED
                        else -> AccountRemovalPhase.UNAVAILABLE
                    }
                assertEquals(expected, restored.state.value.phase)
                assertEquals(AccountRemovalRequest(actor.id, actor.id), restored.state.value.request)
                assertTrue(calls.isEmpty())
                moderation.removeAccountHandler = { target, sender, _ -> calls += target to sender }
                restored.retry()
                runCurrent()
                assertEquals(if (kind == 0) listOf(actor.id to actor.id) else emptyList<Pair<String, String>>(), calls)
                if (kind == 3) {
                    restored.updateSession(SessionState.Ready(actor, Role.ENTRANT))
                    restored.request(actor.id)
                    assertEquals(AccountRemovalPhase.CONFIRMING, restored.state.value.phase)
                    assertTrue(calls.isEmpty())
                }
                store.clear()
                runCurrent()
            }
        }

    /** Constructs an independently owned root controller with retained primitives.
     * @param key Actual model lifetime key.
     * @param saved Restored original target/actor state.
     */
    private fun model(
        key: String = "removal",
        saved: SavedStateHandle = SavedStateHandle(),
    ) =
        AccountRemovalViewModel(identity, accounts, moderation, saved).also { store.put(key, it) }

    /** Releases the original root and all held operations around each fixed contract.
     * @param block Declared confirmation/cleanup/recovery outcomes.
     */
    private fun test(block: suspend TestScope.() -> Unit) =
        runTest {
            try {
                block()
            } finally {
                store.clear()
                runCurrent()
            }
        }
}
