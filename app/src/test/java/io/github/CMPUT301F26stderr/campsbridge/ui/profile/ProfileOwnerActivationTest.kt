package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Existing-account hosting contracts; activation is separate from profile editor completion. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileOwnerActivationTest {
    @get:Rule val main = MainDispatcherRule()
    private val original = sampleAccount().copy(phone = "7805550123")
    private val accounts = FakeAccountRepository(original).apply { allowWrites = true }
    private val images = FakeImageRepository()
    private val store = ViewModelStore()
    private var created = 0

    /** Releases pending capability work and every account/image reader. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, accounts.activeObservers)
        assertEquals(0, images.activeObservers)
    }

    /** Activation freezes conflicting actions and preserves identity, contacts and prior roles. */
    @Test fun heldActivationUsesTheSameAccountAndGuardsDuplicateActions() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            accounts.ownerGate = gate
            val model = model()
            runCurrent()
            model.enableOwner()
            assertEquals(ProfileOperation.ENABLING_OWNER, content(model).operation)
            model.enableOwner()
            model.beginPersonalEdit()
            assertFalse(model.cancelOwnerActivation())
            runCurrent()
            assertFalse(model.ownerWorkspaceRequested.value)
            assertEquals(1, accounts.ownerWrites)
            assertNull(content(model).personalDraft)
            gate.complete(Unit)
            runCurrent()
            assertEquals(original.copy(roles = original.roles + Role.OWNER), accounts.account.value)
            assertEquals(listOf(original.id), accounts.ownerIds)
            assertEquals(0, accounts.createCalls)
            assertEquals(0, accounts.profileWrites)
            assertTrue(model.ownerWorkspaceRequested.value)
            assertNull(model.completion.value)
            assertEquals(ProfileOperation.IDLE, content(model).operation)
        }

    /** A write acknowledgement alone cannot navigate before the capability is observed. */
    @Test fun acknowledgedWriteWaitsForTheExistingAccountObserver() =
        runTest {
            accounts.ownerHandler = {}
            val model = model()
            runCurrent()
            model.enableOwner()
            runCurrent()
            assertEquals(1, accounts.ownerWrites)
            assertEquals(ProfileOperation.ENABLING_OWNER, content(model).operation)
            assertFalse(model.ownerWorkspaceRequested.value)
            assertEquals(1, accounts.activeObservers)
            accounts.account.value = original.copy(roles = original.roles + Role.OWNER)
            runCurrent()
            assertTrue(model.ownerWorkspaceRequested.value)
            assertEquals(ProfileOperation.IDLE, content(model).operation)
        }

    /** An already granted capability requests only navigation, then explicit acknowledgement clears it. */
    @Test fun existingOwnerDoesNotWriteTheCapabilityAgain() =
        runTest {
            accounts.account.value = original.copy(roles = original.roles + Role.OWNER)
            val saved = SavedStateHandle()
            val model = model(saved)
            runCurrent()
            model.enableOwner()
            model.enableOwner()
            runCurrent()
            assertTrue(model.ownerWorkspaceRequested.value)
            assertEquals(0, accounts.writes)
            assertNull(model.completion.value)
            model.acknowledgeOwnerWorkspaceRequest()
            assertFalse(model.ownerWorkspaceRequested.value)
            val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            store.clear()
            runCurrent()
            val restored = model(snapshot)
            runCurrent()
            assertFalse(restored.ownerWorkspaceRequested.value)
            assertEquals(0, accounts.writes)
        }

    /** A failed capability write leaves the same profile usable and retries explicitly. */
    @Test fun failedActivationRetainsTheProfileAndCanRetry() =
        runTest {
            accounts.ownerFailure = IOException("offline")
            val model = model()
            runCurrent()
            model.enableOwner()
            runCurrent()
            assertTrue(content(model).ownerActivationFailed)
            assertFalse(model.ownerWorkspaceRequested.value)
            assertEquals(original, content(model).account)
            assertEquals(ProfileOperation.IDLE, content(model).operation)
            accounts.ownerFailure = null
            model.enableOwner()
            runCurrent()
            assertTrue(model.ownerWorkspaceRequested.value)
            assertFalse(content(model).ownerActivationFailed)
            assertEquals(2, accounts.ownerWrites)
            assertEquals(0, accounts.createCalls)
        }

    /** Server reconciliation of a lost acknowledgement prevents a duplicate role write. */
    @Test fun lostAcknowledgementRetriesTheConfirmedRoleWithoutAnotherWrite() =
        runTest {
            accounts.ownerHandler = {
                accounts.account.value = original.copy(roles = original.roles + Role.OWNER)
                throw IOException("acknowledgement lost")
            }
            val model = model()
            runCurrent()
            model.enableOwner()
            runCurrent()
            assertTrue(content(model).ownerActivationFailed)
            assertFalse(model.ownerWorkspaceRequested.value)
            model.enableOwner()
            runCurrent()
            assertTrue(model.ownerWorkspaceRequested.value)
            assertEquals(1, accounts.ownerWrites)
            assertTrue(accounts.readIds.all { it == original.id })
        }

    /** Reconstruction checks the saved attempt without automatically replaying a capability write. */
    @Test fun restoredAttemptReadsWhetherHostingWasActuallyGranted() =
        runTest {
            for (granted in listOf(false, true)) {
                accounts.account.value =
                    if (granted) original.copy(roles = original.roles + Role.OWNER) else original
                val restored =
                    model(SavedStateHandle(mapOf("profileOwnerActivationPending" to true)))
                runCurrent()
                assertEquals(granted, restored.ownerWorkspaceRequested.value)
                assertEquals(!granted, content(restored).ownerActivationFailed)
                assertEquals(ProfileOperation.IDLE, content(restored).operation)
                assertEquals(0, accounts.ownerWrites)
            }
            assertEquals(listOf(original.id, original.id), accounts.readIds)
        }

    /** A source failure while waiting for observed capability permits read recovery without regranting. */
    @Test fun failedObservationCanRecoverTheConfirmedCapability() =
        runTest {
            accounts.ownerHandler = {}
            val model = model()
            runCurrent()
            model.enableOwner()
            runCurrent()
            accounts.streamFailure.value = IOException("observer offline")
            runCurrent()
            assertEquals(ProfileUiState.Failure, model.state.value)
            assertFalse(model.ownerWorkspaceRequested.value)
            accounts.account.value = original.copy(roles = original.roles + Role.OWNER)
            accounts.streamFailure.value = null
            model.retry()
            runCurrent()
            assertTrue(model.ownerWorkspaceRequested.value)
            assertEquals(1, accounts.ownerWrites)
            assertEquals(ProfileOperation.IDLE, content(model).operation)
        }

    /** Cancelling the introduction never writes; leaving after confirmation does not revoke hosting. */
    @Test fun cancellingOnlyClearsNavigationIntent() =
        runTest {
            val model = model()
            runCurrent()
            assertTrue(model.cancelOwnerActivation())
            assertEquals(0, accounts.writes)
            model.enableOwner()
            runCurrent()
            assertTrue(model.ownerWorkspaceRequested.value)
            assertTrue(model.cancelOwnerActivation())
            assertFalse(model.ownerWorkspaceRequested.value)
            assertTrue(Role.OWNER in accounts.account.value!!.roles)
            assertEquals(1, accounts.ownerWrites)
        }

    /** Releasing the graph cancels a held write without creating navigation or failure results. */
    @Test fun clearingTheGraphCancelsHeldActivation() =
        runTest {
            accounts.ownerGate = CompletableDeferred()
            val model = model()
            runCurrent()
            model.enableOwner()
            runCurrent()
            val before = model.state.value
            store.clear()
            runCurrent()
            accounts.ownerGate!!.complete(Unit)
            runCurrent()
            assertEquals(before, model.state.value)
            assertFalse(model.ownerWorkspaceRequested.value)
            assertEquals(original, accounts.account.value)
        }

    /** Creates one graph-owned profile with no reserved validator dependency for hosting.
     * @param saved Primitive editor/activation restoration store.
     */
    private fun model(saved: SavedStateHandle = SavedStateHandle()): ProfileViewModel =
        ProfileViewModel(original.id, accounts, images, saved).also {
            store.put("profile-${created++}", it)
        }

    /** Requires observed content before checking activation fields.
     * @param model Existing-profile owner under test.
     */
    private fun content(model: ProfileViewModel): ProfileUiState.Content {
        assertTrue(model.state.value is ProfileUiState.Content)
        return model.state.value as ProfileUiState.Content
    }
}
