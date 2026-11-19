package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpdateResult
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeIdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AccountRemovalHost
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AccountRemovalPhase
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AccountRemovalViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Hosted action/confirmation contracts; production inspection-slot wiring is an owner handoff. */
class AdminRemovalFlowTest {
    @get:Rule val compose = createComposeRule()
    private val admin = sampleAccount(setOf(Role.ADMIN))
    private val owner = sampleAccount(setOf(Role.OWNER)).copy(id = "owner/one", name = "Taylor Owner")
    private val site = sampleCampsite("site/one", owner.id)
    private val records = mutableMapOf(admin.id to admin, owner.id to owner)
    private var readFailure: Exception? = null
    private val accounts =
        object : AccountRepository by FakeAccountRepository() {
            override suspend fun get(id: String) =
                readFailure?.let { throw it } ?: records[id]
        }
    private val sites = FakeCampsiteRepository(listOf(site))
    private val photos = FakeImageRepository()
    private val attachment =
        ImageAttachment(ImageTarget(ImageTargetType.ACCOUNT, owner.id), "original-blob", Instant.EPOCH)
    private val cleanupCalls = mutableListOf<String>()
    private val images =
        object : ImageRepository by photos {
            override suspend fun retryCleanup(blobId: String) {
                cleanupCalls += blobId
            }
        }
    private val moderation =
        FakeModerationRepository().apply {
            removeAccountHandler = { _, _, _ -> }
            removeCampsiteHandler = { _, _, _ -> }
        }
    private val store = ViewModelStore()
    private val rootStore = ViewModelStore()
    private val model =
        AdminRemovalViewModel(admin.id, accounts, sites, images, moderation, SavedStateHandle()).also {
            store.put("admin", it)
        }
    private val root =
        AccountRemovalViewModel(FakeIdentityProvider(), accounts, moderation, SavedStateHandle()).also {
            rootStore.put("root", it)
        }
    private val gates = mutableListOf<CompletableDeferred<Unit>>()

    @After fun close() {
        compose.runOnIdle {
            gates.forEach { it.complete(Unit) }
            store.clear()
            rootStore.clear()
        }
    }

    private fun content(session: SessionState.Ready = SessionState.Ready(admin, Role.ADMIN)) {
        photos.attachment.value = attachment
        root.updateSession(session)
        compose.setContent {
            val rootState by root.state.collectAsStateWithLifecycle()
            CampsbridgeTheme {
                Column {
                    AdminRemovalAccountAction(model, session, owner)
                    AdminRemovalCampsiteAction(model, session, site)
                    AdminRemovalImageAction(model, session, attachment)
                }
                AdminRemovalHost(
                    model,
                    session,
                    AdminRemovalWarnings("Test account warning", "Test campsite warning", "Test image warning"),
                    onRequestRootRemoval = root::request,
                )
                AccountRemovalHost(rootState, root::confirm, root::dismiss, root::retry, root::retryRecovery, {
                    root.request(admin.id)
                })
            }
        }
    }

    @Test fun cancellingAccountConfirmationChangesNothing() {
        content()
        compose.onNodeWithText("Remove profile").performClick()
        compose.onNodeWithText("Taylor Owner").assertIsDisplayed()
        compose.onNodeWithText("Target ID: ${owner.id}").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertTrue(moderation.requests.isEmpty())
            assertEquals(0, photos.deleteCalls)
        }
    }

    @Test fun heldAccountMarkIsNotShownAsCompletedCleanup() {
        val gate = CompletableDeferred<Unit>().also(gates::add)
        moderation.removeAccountHandler =
            { id, actor, _ ->
                records[id] = owner.copy(removal = Removal(Instant.EPOCH, actor))
                gate.await()
            }
        content()
        compose.onNodeWithText("Remove profile").performClick()
        compose.onNodeWithText("Confirm removal").performClick()
        compose.onNodeWithText("Removing the original target...").assertIsDisplayed()
        compose.onNodeWithText("Removal and cleanup completed.").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.runOnIdle { gate.complete(Unit) }
        compose.onNodeWithText("Removal and cleanup completed.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("account:${owner.id}"), moderation.requests) }
    }

    @Test fun campsiteFailureShowsCommittedRemovalAndRetriesTheSameTarget() {
        moderation.removeCampsiteHandler = { _, actor, _ ->
            sites.records.value = listOf(site.copy(removal = Removal(Instant.EPOCH, actor)))
            throw IOException("cleanup")
        }
        content()
        compose.onNodeWithText("Remove campsite").performClick()
        compose.onNodeWithText("Confirm removal").performClick()
        compose.onNodeWithText("The record is marked removed. Dependent cleanup has not finished.").assertIsDisplayed()
        compose.runOnIdle {
            model.requestAccount(owner)
            moderation.removeCampsiteHandler = { _, _, _ -> }
        }
        compose.onNodeWithText("Target ID: ${site.id}").assertIsDisplayed()
        compose.onNodeWithText("Retry cleanup").performClick()
        compose.onNodeWithText("Removal and cleanup completed.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(List(2) { "campsite:${site.id}" }, moderation.requests) }
    }

