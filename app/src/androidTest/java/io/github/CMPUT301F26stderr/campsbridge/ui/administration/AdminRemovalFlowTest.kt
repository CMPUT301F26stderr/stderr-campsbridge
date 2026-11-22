package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
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

/** Shared confirmation contracts and real gallery-to-inspection image removal navigation. */
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
    private val appHosts = mutableListOf<TestAppHost>()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()

    @After fun close() {
        compose.runOnIdle {
            gates.forEach { it.complete(Unit) }
            store.clear()
            rootStore.clear()
            appHosts.forEach { it.close() }
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
                    onRequestRootRemoval = root::request,
                )
                AccountRemovalHost(rootState, root::confirm, root::dismiss, root::retry, root::retryRecovery, {
                    root.request(admin.id)
                })
            }
        }
    }

    /** Removes a profile image through the actual gallery while preserving its account. */
    @Test fun galleryProfileImageRemovalKeepsItsParentAndCancellationChangesNothing() {
        galleryRemoval(ImageTarget(ImageTargetType.ACCOUNT, owner.id), "Profile")
    }

    /** Removes a campsite image through the actual gallery while preserving its listing. */
    @Test fun galleryCampsiteImageRemovalKeepsItsParentAndCancellationChangesNothing() {
        galleryRemoval(ImageTarget(ImageTargetType.CAMPSITE, site.id), "Campsite")
    }

    /** Exercises confirmation, cancellation and removal through the saved gallery route.
     * @param target Actual profile or campsite attachment slot.
     * @param kind Visible gallery target label.
     */
    private fun galleryRemoval(
        target: ImageTarget,
        kind: String,
    ) {
        val host = TestAppHost().also(appHosts::add)
        val parent = FakeAccountRepository(owner)
        host.accounts.account.value = admin
        host.adminAccounts = parent
        host.campsites.records.value = listOf(site)
        host.images.attachment.value = attachment.copy(target = target)
        // A broken display must still leave metadata available for inspection/removal.
        host.images.urlFailures[attachment.blobId] = IOException("thumbnail offline")
        compose.setContent { host.Content() }
        compose.onNodeWithText("Images").performClick()
        compose.onNodeWithText("$kind \u00b7 ${target.id}").performClick()
        compose.onNodeWithText("Remove image").performScrollTo().performClick()
        compose
            .onNodeWithText(
                "Remove the image attached to ${target.id}. The profile or campsite will remain.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(0, host.images.deleteCalls)
            assertEquals(
                attachment.blobId,
                host.images.attachment.value
                    ?.blobId,
            )
        }
        compose.onNodeWithText("Remove image").performScrollTo().performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Removal for ${target.id} is complete.").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("No image is attached to this target.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(target), host.images.removalTargets)
            assertEquals(owner, parent.account.value)
            assertEquals(listOf(site), host.campsites.records.value)
            assertTrue(host.moderation.requests.isEmpty())
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("No matching records").assertIsDisplayed()
    }

    /** Cancelling confirmation leaves the inspected account unchanged. */
    @Test fun cancellingAccountConfirmationChangesNothing() {
        content()
        compose.onNodeWithText("Remove profile").performClick()
        compose.onNodeWithText("Remove profile?").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Remove profile ${owner.id} and its personal details. Affected active participation and listings will be removed, with cancellations and simulated refunds. History is retained.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertTrue(moderation.requests.isEmpty())
            assertEquals(0, photos.deleteCalls)
        }
    }

    /** A committed mark does not report success while cleanup is running. */
    @Test fun heldAccountMarkIsNotShownAsCompletedCleanup() {
        val gate = CompletableDeferred<Unit>().also(gates::add)
        moderation.removeAccountHandler =
            { id, actor, _ ->
                records[id] = owner.copy(removal = Removal(Instant.EPOCH, actor))
                gate.await()
            }
        content()
        compose.onNodeWithText("Remove profile").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Finishing removal for ${owner.id}.").assertIsDisplayed()
        compose.onNodeWithText("Removal for ${owner.id} is complete.").assertDoesNotExist()
        compose.onNodeWithText("Retry removal").assertDoesNotExist()
        compose.runOnIdle { gate.complete(Unit) }
        compose.onNodeWithText("Removal for ${owner.id} is complete.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("account:${owner.id}"), moderation.requests) }
    }

    /** Retries committed campsite cleanup without switching to another selection. */
    @Test fun campsiteFailureShowsCommittedRemovalAndRetriesTheSameTarget() {
        moderation.removeCampsiteHandler = { _, actor, _ ->
            sites.records.value = listOf(site.copy(removal = Removal(Instant.EPOCH, actor)))
            throw IOException("cleanup")
        }
        content()
        compose.onNodeWithText("Remove campsite").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("The record is marked removed. Dependent cleanup has not finished.").assertIsDisplayed()
        compose.runOnIdle {
            model.requestAccount(owner)
            moderation.removeCampsiteHandler = { _, _, _ -> }
        }
        compose.onNodeWithText("Removal did not finish").assertIsDisplayed()
        compose.onNodeWithText("Retry removal").performClick()
        compose.onNodeWithText("Removal for ${site.id} is complete.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(List(2) { "campsite:${site.id}" }, moderation.requests) }
    }

    /** Retries the detached blob while preserving replacement images and parent records. */
    @Test fun detachedImageCleanupDoesNotDeleteANewAttachmentOrItsParent() {
        photos.deleteResult = ImageUpdateResult(null, attachment.blobId)
        content()
        compose.onNodeWithText("Remove image").performClick()
        compose
            .onNodeWithText(
                "Remove the image attached to ${owner.id}. The profile or campsite will remain.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Remove").performClick()
        compose
            .onNodeWithText(
                "The image is detached. Cleanup of the old file is still pending.",
            ).assertIsDisplayed()
        compose.runOnIdle { photos.attachment.value = attachment.copy(blobId = "new-live-image") }
        compose.onNodeWithText("Retry removal").performClick()
        compose.onNodeWithText("Removal for ${owner.id} is complete.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, photos.deleteCalls)
            assertEquals(listOf(attachment.blobId), cleanupCalls)
            assertEquals("new-live-image", photos.attachment.value?.blobId)
            assertTrue(moderation.requests.isEmpty())
            assertEquals(owner, records[owner.id])
        }
    }

    @Test fun cancellingImageConfirmationLeavesAttachmentAndBytesAlone() {
        content()
        compose.onNodeWithText("Remove image").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(0, photos.deleteCalls)
            assertTrue(cleanupCalls.isEmpty())
            assertEquals(attachment, photos.attachment.value)
        }
    }

    /** Missing targets show failure without writing another record. */
    @Test fun missingTargetIsExplainedWithoutReportingSuccessOrTouchingAnotherRecord() {
        content()
        compose.onNodeWithText("Remove profile").performClick()
        compose.runOnIdle { records.remove(owner.id) }
        compose.onNodeWithText("Remove").performClick()
        compose
            .onNodeWithText(
                "The original target is missing or unavailable. Return to inspection to check its current state.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Removal for ${owner.id} is complete.").assertDoesNotExist()
        compose.runOnIdle { assertTrue(moderation.requests.isEmpty()) }
    }

    @Test fun currentAdminUsesTheAcceptedRootFlowAndOutlivesTheAdminOwner() {
        val gate = CompletableDeferred<Unit>().also(gates::add)
        moderation.removeAccountHandler =
            { id, actor, _ ->
                records[id] = admin.copy(removal = Removal(Instant.EPOCH, actor))
                gate.await()
            }
        content()
        compose.runOnIdle { model.requestAccount(admin) }
        compose.onNodeWithText("Delete profile?").assertIsDisplayed()
        compose.onNodeWithText("Delete permanently").performClick()
        compose.runOnIdle {
            root.updateSession(SessionState.Unavailable)
            store.clear()
            assertEquals(AccountRemovalPhase.REMOVING, root.state.value.phase)
            gate.complete(Unit)
        }
        compose.waitUntil(5_000) { root.state.value.phase == AccountRemovalPhase.COMPLETE }
        compose.runOnIdle { assertEquals(listOf("account:${admin.id}"), moderation.requests) }
    }

    @Test fun nonAdminSessionHasNoModerationActions() {
        content(SessionState.Ready(admin.copy(roles = setOf(Role.OWNER)), Role.OWNER))
        compose.onNodeWithText("Remove profile").assertDoesNotExist()
        compose.onNodeWithText("Remove campsite").assertDoesNotExist()
        compose.onNodeWithText("Remove image").assertDoesNotExist()
        compose.runOnIdle { assertTrue(moderation.requests.isEmpty()) }
    }
}
