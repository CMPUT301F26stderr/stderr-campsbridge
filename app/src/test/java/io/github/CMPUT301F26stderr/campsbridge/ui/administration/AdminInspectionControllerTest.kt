package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Eighteen fixed selected-reader contracts without native navigation or persistence mutations. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminInspectionControllerTest {
    @get:Rule val main = MainDispatcherRule()

    /** Unselected and blank destinations cannot start global or selected reads */
    @Test fun idleAndBlankSelectionAreReadFree() =
        scenario { f ->
            f.model.openProfile("")
            f.model.openImage(ImageTarget(ImageTargetType.ACCOUNT, "  "))
            runCurrent()
            assertEquals(AdminInspectionUiState.None, f.model.state.value)
            assertEquals(
                0,
                f.profileSource.requests + f.siteSource.requests + f.ownedSource.requests,
            )
            assertTrue(f.photoBase.observedTargets.isEmpty())
        }

    /** A held profile source starts dependents only after its exact saved record arrives. */
    @Test fun profileLoadsExactRecordThenIndependentSitesAndPhoto() =
        scenario { f ->
            val held = CompletableDeferred<Unit>()
            f.profileSource.gate = held
            f.photoBase.attachment.value = f.attachment
            f.model.openProfile(f.profile.id)
            runCurrent()
            assertEquals(AdminRead.Loading, f.profile().account)
            assertEquals(0, f.ownedSource.requests)
            held.complete(Unit)
            runCurrent()
            assertEquals(AdminRead.Content(f.profile), f.profile().account)
            assertEquals(AdminRead.Content(listOf(f.site)), f.profile().campsites)
            assertEquals(f.attachment, f.profile().photo.attachment)
            assertEquals("file:///missing-fixture/blob-1", f.profile().photo.displayUrl)
            assertEquals(listOf(f.accountTarget), f.photoBase.observedTargets)
        }

    /** Confirmed absence and an inconsistent repository identity never start dependent reads. */
    @Test fun absentOrMismatchedProfileIsUnavailable() =
        scenario { f ->
            f.profileSource.values.value = Result.success(null)
            f.model.openProfile(f.profile.id)
            runCurrent()
            assertEquals(AdminRead.Unavailable, f.profile().account)
            f.profileSource.values.value = Result.success(f.profile.copy(id = "foreign"))
            runCurrent()
            assertEquals(AdminRead.Unavailable, f.profile().account)
            assertEquals(0, f.ownedSource.requests)
            assertTrue(f.photoBase.observedTargets.isEmpty())
        }

    /** Retained removal metadata is inspectable rather than treated as missing. */
    @Test fun removedProfileAndCampsiteRemainActualFacts() =
        scenario { f ->
            val removal = Removal(Instant.parse("2027-05-02T12:00:00Z"), "admin")
            val removedProfile = f.profile.copy(name = "", email = null, removal = removal)
            val removedSite = f.site.copy(removal = removal)
            f.profileSource.values.value = Result.success(removedProfile)
            f.ownedSource.values.value = Result.success(listOf(removedSite))
            f.model.openProfile(f.profile.id)
            runCurrent()
            assertEquals(AdminRead.Content(removedProfile), f.profile().account)
            assertEquals(AdminRead.Content(listOf(removedSite)), f.profile().campsites)
        }

    /** Failed sites/photo sections preserve the profile and retry without replacing its source. */
    @Test fun dependentFailuresAndRetriesAreIndependent() =
        scenario { f ->
            f.ownedSource.values.value = Result.failure(IOException("sites offline"))
            f.photoBase.streamFailure.value = IOException("photo offline")
            f.model.openProfile(f.profile.id)
            runCurrent()
            assertEquals(AdminRead.Content(f.profile), f.profile().account)
            assertEquals(AdminRead.Failure, f.profile().campsites)
            assertEquals(ImageLoadState.FAILED, f.profile().photo.loadState)
            f.ownedSource.values.value = Result.success(listOf(f.site))
            f.model.retryCampsites()
            f.model.retryCampsites()
            runCurrent()
            assertEquals(AdminRead.Content(listOf(f.site)), f.profile().campsites)
            assertEquals(ImageLoadState.FAILED, f.profile().photo.loadState)
            f.photoBase.streamFailure.value = null
            f.model.retryPhoto()
            runCurrent()
            assertEquals(ImageLoadState.READY, f.profile().photo.loadState)
            assertEquals(1, f.profileSource.requests)
            assertEquals(2, f.ownedSource.requests)
        }

    /** Explicit source retry preserves target and synchronously ignores duplicate taps. */
    @Test fun profileRetryIsGuardedAndKeepsItsIdentity() =
        scenario { f ->
            f.profileSource.values.value = Result.failure(IOException("profile offline"))
            f.model.openProfile(f.profile.id)
            runCurrent()
            assertEquals(AdminRead.Failure, f.profile().account)
            f.profileSource.values.value = Result.success(f.profile)
            f.model.retryProfile()
            f.model.retryProfile()
            assertEquals(AdminRead.Loading, f.profile().account)
            runCurrent()
            assertEquals(f.profile.id, f.profile().id)
            assertEquals(AdminRead.Content(f.profile), f.profile().account)
            assertEquals(2, f.profileSource.requests)
        }

    /** Missing parent state clears dependent facts, then confirmed reappearance starts new reads. */
    @Test fun disappearedProfileRetiresAndReappearingRestartsDependents() =
        scenario { f ->
            f.photoBase.attachment.value = f.attachment
            f.model.openProfile(f.profile.id)
            runCurrent()
            assertEquals(1, f.ownedSource.active)
            f.profileSource.values.value = Result.success(null)
            runCurrent()
            assertEquals(AdminRead.Unavailable, f.profile().account)
            assertEquals(AdminRead.Loading, f.profile().campsites)
            assertNull(f.profile().photo.attachment)
            assertEquals(0, f.ownedSource.active + f.photoBase.activeObservers)
            f.profileSource.values.value = Result.success(f.profile)
            runCurrent()
            assertEquals(AdminRead.Content(listOf(f.site)), f.profile().campsites)
            assertEquals(2, f.ownedSource.requests)
            assertEquals(1, f.ownedSource.active)
        }

    /** Scoped rows preserve original order and reject foreign-owner results as failed data. */
    @Test fun ownerRowsPreserveDraftsAndRemovalWithoutAcceptingForeignOwnership() =
        scenario { f ->
            val draft = f.site.copy(id = "draft", publishedAt = null)
            val retained = f.site.copy(id = "removed", removal = Removal(Instant.MAX, "admin"))
            val rows = listOf(draft, retained, f.site)
            f.ownedSource.values.value = Result.success(rows)
            f.model.openProfile(f.profile.id)
            runCurrent()
            assertEquals(AdminRead.Content(rows), f.profile().campsites)
            f.ownedSource.values.value = Result.success(listOf(f.site.copy(ownerId = "foreign")))
            runCurrent()
            assertEquals(AdminRead.Failure, f.profile().campsites)
            assertEquals(AdminRead.Content(f.profile), f.profile().account)
        }

    /** Same selection is stable and noncooperative previous reads cannot publish under a new ID. */
    @Test fun unchangedSelectionAndLateReplacedProfileDoNotRestartOrOverwrite() =
        scenario { f ->
            val late = CompletableDeferred<Account?>()
            f.profileSource.late = late
            f.model.openProfile(f.profile.id)
            runCurrent()
            f.model.openProfile(f.profile.id)
            runCurrent()
            assertEquals(1, f.profileSource.requests)
            f.model.openProfile(f.other.id)
            runCurrent()
            late.complete(f.profile)
            runCurrent()
            assertEquals(f.other.id, f.profile().id)
            assertEquals(AdminRead.Content(f.other), f.profile().account)
            assertEquals(0, f.profileSource.active)
        }

    /** An account attachment resolves only the corresponding account and no owned-site rows. */
    @Test fun accountImageUsesItsExactProfileParent() =
        scenario { f ->
            f.photoBase.attachment.value = f.attachment
            f.model.openImage(f.accountTarget)
            runCurrent()
            assertEquals(f.accountTarget, f.image().target)
            assertEquals(AdminRead.Content(AdminImageParent.Profile(f.profile)), f.image().parent)
            assertEquals(f.attachment, f.image().photo.attachment)
            assertEquals(1, f.profileSource.requests)
            assertEquals(0, f.siteSource.requests + f.ownedSource.requests)
        }

    /** A campsite attachment resolves its actual saved listing without account-source reads. */
    @Test fun campsiteImageUsesItsExactSiteParent() =
        scenario { f ->
            f.photoBase.attachment.value = f.attachment.copy(target = f.siteTarget)
            f.model.openImage(f.siteTarget)
            runCurrent()
            assertEquals(AdminRead.Content(AdminImageParent.Site(f.site)), f.image().parent)
            assertEquals(1, f.siteSource.requests)
            assertEquals(0, f.profileSource.requests + f.ownedSource.requests)
            assertEquals(listOf(f.siteTarget), f.photoBase.observedTargets)
        }

