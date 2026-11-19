package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpdateResult
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** AD4/AD5 contracts: frozen targets, sectional cleanup and root-owned self-removal. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminRemovalViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun confirmationAndCancellationDoNotWrite() =
        scenario { f ->
            f.model.requestAccount(f.owner)
            assertEquals(AdminRemovalPhase.CONFIRMING, f.model.state.value.phase)
            assertTrue(f.moderation.requests.isEmpty())
            f.model.dismiss()
            f.model.confirm()
            runCurrent()
            assertEquals(AdminRemovalPhase.IDLE, f.model.state.value.phase)
            assertTrue(f.moderation.requests.isEmpty())
            assertEquals(0, f.photos.deleteCalls)
        }

    @Test fun heldRemovalIgnoresDuplicateConfirmationAndNewSelections() =
        scenario { f ->
            val gate = CompletableDeferred<Unit>()
            f.moderation.removeAccountHandler = { id, actor, _ ->
                assertEquals(f.owner.id, id)
                assertEquals(f.admin.id, actor)
                f.accountRecords[id] = f.owner.copy(removal = Removal(Instant.EPOCH, actor))
                gate.await()
            }
            f.model.requestAccount(f.owner)
            f.model.confirm()
            f.model.confirm()
            runCurrent()
            val request = f.model.state.value.request
            assertEquals(AdminRemovalPhase.REMOVING, f.model.state.value.phase)
            f.model.requestCampsite(f.site)
            f.model.dismiss()
            assertEquals(request, f.model.state.value.request)
            assertEquals(listOf("account:${f.owner.id}"), f.moderation.requests)
            gate.complete(Unit)
            runCurrent()
            assertEquals(AdminRemovalPhase.COMPLETE, f.model.state.value.phase)
        }

    @Test fun committedAccountFailureRetainsOriginalTargetUntilCleanupFinishes() =
        scenario { f ->
            f.moderation.removeAccountHandler = { id, actor, _ ->
                f.accountRecords[id] = f.owner.copy(removal = Removal(Instant.EPOCH, actor))
                throw IOException("dependent cancellations offline")
            }
            f.model.requestAccount(f.owner)
            f.model.confirm()
            runCurrent()
            assertEquals(AdminRemovalPhase.CLEANUP_PENDING, f.model.state.value.phase)
            assertTrue(f.model.state.value.committed)
            f.model.requestCampsite(f.site)
            f.moderation.removeAccountHandler = { _, _, _ -> }
            f.model.retry()
            f.model.retry()
            runCurrent()
            assertEquals(List(2) { "account:${f.owner.id}" }, f.moderation.requests)
            assertEquals(AdminRemovalPhase.COMPLETE, f.model.state.value.phase)
        }

    @Test fun campsiteUsesModerationAndItsOwnConfirmedCleanupResult() =
        scenario { f ->
            f.moderation.removeCampsiteHandler = { id, actor, _ ->
                f.sites.records.value = listOf(f.site.copy(removal = Removal(Instant.EPOCH, actor)))
                assertEquals(f.site.id, id)
                throw IOException("cleanup")
            }
            f.model.requestCampsite(f.site)
            f.model.confirm()
            runCurrent()
            assertEquals(AdminRemovalPhase.CLEANUP_PENDING, f.model.state.value.phase)
            f.moderation.removeCampsiteHandler = { _, _, _ -> }
            f.model.retry()
            runCurrent()
            assertEquals(List(2) { "campsite:${f.site.id}" }, f.moderation.requests)
            assertEquals(AdminRemovalPhase.COMPLETE, f.model.state.value.phase)
            assertEquals(0, f.sites.writeCalls)
        }

    @Test fun currentAdminIsHandedToRootWithoutAnAdminOwnedWrite() =
        scenario { f ->
            f.model.requestAccount(f.admin)
            assertEquals(AdminRemovalPhase.ROOT_HANDOFF, f.model.state.value.phase)
            f.model.confirm()
            f.model.retry()
            runCurrent()
            assertTrue(f.moderation.requests.isEmpty())
            assertEquals(
                f.admin.id,
                (
                    f.model.state.value.request
                        ?.target as AdminRemovalTarget.Account
                ).id,
            )
            f.model.acknowledgeRootHandoff()
            assertEquals(AdminRemovalPhase.IDLE, f.model.state.value.phase)
        }

    @Test fun imageCleanupRetriesOnlyTheOriginalBlobAfterReplacement() =
        scenario { f ->
            f.photos.deleteResult = ImageUpdateResult(null, f.attachment.blobId)
            f.model.requestImage(f.attachment)
            f.model.confirm()
            runCurrent()
            assertEquals(AdminRemovalPhase.CLEANUP_PENDING, f.model.state.value.phase)
            assertTrue(f.model.state.value.committed)
            f.photos.attachment.value = f.attachment.copy(blobId = "new-live-image")
            f.cleanupFailure = IOException("bytes offline")
            f.model.retry()
            runCurrent()
            assertEquals(AdminRemovalPhase.CLEANUP_PENDING, f.model.state.value.phase)
            f.cleanupFailure = null
            f.model.retry()
            f.model.retry()
            runCurrent()
            assertEquals(listOf(f.attachment.blobId, f.attachment.blobId), f.cleanupCalls)
            assertEquals(1, f.photos.deleteCalls)
            assertEquals(
                "new-live-image",
                f.photos.attachment.value
                    ?.blobId,
            )
            assertEquals(AdminRemovalPhase.COMPLETE, f.model.state.value.phase)
            assertTrue(f.moderation.requests.isEmpty())
        }

    @Test fun imageChangedDuringConfirmationIsNeverDeleted() =
        scenario { f ->
            f.model.requestImage(f.attachment)
            f.photos.attachment.value = f.attachment.copy(blobId = "replacement")
            f.model.confirm()
            runCurrent()
            assertEquals(AdminRemovalPhase.UNAVAILABLE, f.model.state.value.phase)
            assertEquals(AdminRemovalProblem.CHANGED, f.model.state.value.problem)
            assertEquals(0, f.photos.deleteCalls)
        }

    @Test fun alreadyMissingImageIsExplicitAndDoesNotDelete() =
        scenario { f ->
            f.model.requestImage(f.attachment)
            f.photos.attachment.value = null
            f.model.confirm()
            runCurrent()
            assertEquals(AdminRemovalPhase.UNAVAILABLE, f.model.state.value.phase)
            assertEquals(AdminRemovalProblem.MISSING, f.model.state.value.problem)
            assertEquals(0, f.photos.deleteCalls)
        }

    @Test fun uncertainImageRemovalRecoversWithoutDeletingItsReplacement() =
        scenario { f ->
            val images =
                object : ImageRepository by f.images {
                    override suspend fun delete(target: ImageTarget): ImageUpdateResult {
                        f.photos.attachment.value = f.attachment.copy(blobId = "new-image")
                        throw IOException("lost acknowledgement")
                    }
                }
            f.recreate(images = images)
            f.model.requestImage(f.attachment)
            f.model.confirm()
            runCurrent()
            assertEquals(AdminRemovalPhase.CLEANUP_PENDING, f.model.state.value.phase)
            f.model.retry()
            runCurrent()
            assertEquals(listOf(f.attachment.blobId), f.cleanupCalls)
            assertEquals(
                "new-image",
                f.photos.attachment.value
                    ?.blobId,
            )
            assertEquals(AdminRemovalPhase.COMPLETE, f.model.state.value.phase)
        }

    @Test fun failedRecoveryRequiresAReadBeforeAnotherWrite() =
        scenario { f ->
            f.moderation.removeAccountHandler =
                { _, _, _ ->
                    f.readFailure = IOException("read offline")
                    throw IOException("write offline")
                }
            f.model.requestAccount(f.owner)
            f.model.confirm()
            runCurrent()
            assertEquals(AdminRemovalPhase.UNKNOWN, f.model.state.value.phase)
            f.readFailure = null
            f.model.retry()
            runCurrent()
            assertEquals(AdminRemovalPhase.FAILED, f.model.state.value.phase)
            assertEquals(1, f.moderation.requests.size)
            f.moderation.removeAccountHandler = { _, _, _ -> }
            f.model.retry()
            runCurrent()
            assertEquals(2, f.moderation.requests.size)
            assertEquals(AdminRemovalPhase.COMPLETE, f.model.state.value.phase)
        }

    @Test fun recreationReadsOriginalFactsWithoutReplayingRemoval() =
        scenario { f ->
            f.moderation.removeAccountHandler = { id, actor, _ ->
                f.accountRecords[id] = f.owner.copy(removal = Removal(Instant.EPOCH, actor))
                throw IOException("cleanup offline")
            }
            f.model.requestAccount(f.owner)
            f.model.confirm()
            runCurrent()
            val saved = f.saved.keys().associateWith { f.saved.get<Any?>(it) }
            assertTrue(saved.values.all { it is String || it is Boolean })
            f.recreate(saved = SavedStateHandle(saved))
            runCurrent()
            assertEquals(AdminRemovalPhase.CLEANUP_PENDING, f.model.state.value.phase)
            assertEquals(1, f.moderation.requests.size)
            f.moderation.removeAccountHandler = { _, _, _ -> }
            f.model.retry()
            runCurrent()
            assertEquals(List(2) { "account:${f.owner.id}" }, f.moderation.requests)
        }

    @Test fun nonAdminAndChangedSessionsCannotStartOrRetargetRemoval() =
        scenario { f ->
            f.model.updateSession(SessionState.Ready(f.admin.copy(roles = setOf(Role.OWNER)), Role.OWNER))
            f.model.requestAccount(f.owner)
            f.model.confirm()
            runCurrent()
            assertEquals(AdminRemovalPhase.IDLE, f.model.state.value.phase)
            assertTrue(f.moderation.requests.isEmpty())
            f.model.updateSession(SessionState.Ready(f.admin, Role.ADMIN))
            f.model.requestAccount(f.owner)
            f.model.updateSession(SessionState.Ready(f.admin.copy(id = "another-admin"), Role.ADMIN))
            f.model.confirm()
            runCurrent()
            assertTrue(f.moderation.requests.isEmpty())
        }

    @Test fun mismatchedOrRemovedRecordsCannotStartANewRemoval() =
        scenario { f ->
            f.model.requestAccount(f.owner)
            f.accountRecords[f.owner.id] = f.owner.copy(id = "foreign")
            f.model.confirm()
            runCurrent()
            assertEquals(AdminRemovalPhase.UNAVAILABLE, f.model.state.value.phase)
            assertTrue(f.moderation.requests.isEmpty())
            f.model.dismiss()
            f.accountRecords[f.owner.id] = f.owner.copy(removal = Removal(Instant.EPOCH, "other-admin"))
            f.model.requestAccount(f.owner)
            f.model.confirm()
            runCurrent()
            assertEquals(AdminRemovalPhase.UNAVAILABLE, f.model.state.value.phase)
            assertTrue(f.moderation.requests.isEmpty())
        }

    @Test fun clearingOwnerRejectsLateResultsAndActions() =
        scenario { f ->
            val gate = CompletableDeferred<Unit>()
            f.moderation.removeAccountHandler = { _, _, _ -> withContext(NonCancellable) { gate.await() } }
            f.model.requestAccount(f.owner)
            f.model.confirm()
            runCurrent()
            val before = f.model.state.value
            f.store.clear()
            f.model.requestCampsite(f.site)
            f.model.retry()
            gate.complete(Unit)
            runCurrent()
            assertEquals(before, f.model.state.value)
            assertEquals(1, f.moderation.requests.size)
        }

    @Test fun restoredImageCleanupNeverRepeatsDeletionOrTargetsTheReplacement() =
        scenario { f ->
            f.photos.deleteResult = ImageUpdateResult(null, f.attachment.blobId)
            f.model.requestImage(f.attachment)
            f.model.confirm()
            runCurrent()
            val snapshot = f.saved.keys().associateWith { f.saved.get<Any?>(it) }
            f.photos.attachment.value = f.attachment.copy(blobId = "new-live-image")
            f.recreate(saved = SavedStateHandle(snapshot))
            runCurrent()
            assertEquals(AdminRemovalPhase.CLEANUP_PENDING, f.model.state.value.phase)
            assertEquals(1, f.photos.deleteCalls)
            assertTrue(f.cleanupCalls.isEmpty())
            f.model.retry()
            runCurrent()
            assertEquals(listOf(f.attachment.blobId), f.cleanupCalls)
            assertEquals(1, f.photos.deleteCalls)
            assertEquals(
                "new-live-image",
                f.photos.attachment.value
                    ?.blobId,
            )
        }

    @Test fun actorChangedDuringPreflightCannotStartTheWrite() =
        scenario { f ->
            val gate = CompletableDeferred<Unit>()
            f.readGate = gate
            f.model.requestAccount(f.owner)
            f.model.confirm()
            runCurrent()
            f.model.updateSession(SessionState.Ready(f.admin.copy(id = "different-admin"), Role.ADMIN))
            gate.complete(Unit)
            runCurrent()
            assertTrue(f.moderation.requests.isEmpty())
            assertEquals(AdminRemovalProblem.DENIED, f.model.state.value.problem)
        }

    private fun scenario(block: suspend TestScope.(Fixture) -> Unit) =
        runTest(main.dispatcher) {
            val fixture = Fixture()
            try {
                block(fixture)
            } finally {
                fixture.store.clear()
                runCurrent()
            }
        }

    private class Fixture {
        val admin = sampleAccount(setOf(Role.ADMIN)).copy(id = "admin-1")
        val owner = sampleAccount(setOf(Role.OWNER)).copy(id = "owner/one", name = "Taylor Owner")
        val accountRecords = mutableMapOf(admin.id to admin, owner.id to owner)
        var readFailure: Exception? = null
        var readGate: CompletableDeferred<Unit>? = null
        val accounts =
            object : AccountRepository by FakeAccountRepository() {
                override suspend fun get(id: String): Account? {
                    readGate?.await()
                    readFailure?.let { throw it }
                    return accountRecords[id]
                }
            }
        val site = sampleCampsite("site/one", owner.id)
        val sites = FakeCampsiteRepository(listOf(site))
        val photos = FakeImageRepository()
        val attachment = ImageAttachment(ImageTarget(ImageTargetType.ACCOUNT, owner.id), "original-blob", Instant.EPOCH)
        val cleanupCalls = mutableListOf<String>()
        var cleanupFailure: Exception? = null
        val images =
            object : ImageRepository by photos {
                override suspend fun retryCleanup(blobId: String) {
                    cleanupCalls += blobId
                    cleanupFailure?.let { throw it }
                }
            }
        val moderation =
            FakeModerationRepository().apply {
                removeAccountHandler = { _, _, _ -> }
                removeCampsiteHandler = { _, _, _ -> }
            }
        val store = ViewModelStore()
        var saved = SavedStateHandle()
        var model = create(images)

        init {
            photos.attachment.value = attachment
        }

        fun recreate(
            saved: SavedStateHandle = SavedStateHandle(),
            images: ImageRepository = this.images,
        ) {
            store.clear()
            this.saved = saved
            model = create(images)
        }

        private fun create(images: ImageRepository): AdminRemovalViewModel =
            AdminRemovalViewModel(admin.id, accounts, sites, images, moderation, saved).also {
                store.put("admin-removal", it)
                it.updateSession(SessionState.Ready(admin, Role.ADMIN))
            }
    }
}
