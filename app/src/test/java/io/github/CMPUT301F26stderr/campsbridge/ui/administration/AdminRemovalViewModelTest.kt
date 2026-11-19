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

