/*
 * Tests selected inspection reads, independent section recovery and reader cleanup.
 */

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

    /** Missing/failed image parents do not erase the independently current attachment. */
    @Test fun parentAbsenceAndFailurePreserveImageAndRetryOnlyParent() =
        scenario { f ->
            f.photoBase.attachment.value = f.attachment
            f.profileSource.values.value = Result.success(null)
            f.model.openImage(f.accountTarget)
            runCurrent()
            assertEquals(AdminRead.Unavailable, f.image().parent)
            assertEquals(f.attachment, f.image().photo.attachment)
            f.profileSource.values.value = Result.failure(IOException("parent offline"))
            runCurrent()
            assertEquals(AdminRead.Failure, f.image().parent)
            f.profileSource.values.value = Result.success(f.profile)
            f.model.retryParent()
            f.model.retryParent()
            runCurrent()
            assertEquals(AdminRead.Content(AdminImageParent.Profile(f.profile)), f.image().parent)
            assertEquals(2, f.profileSource.requests)
            assertEquals(1, f.photoBase.observedTargets.size)
        }

    /** Confirmed empty slots can receive a live attachment without reloading their parent. */
    @Test fun missingAttachmentCanAppearLive() =
        scenario { f ->
            f.model.openImage(f.accountTarget)
            runCurrent()
            assertEquals(ImageLoadState.READY, f.image().photo.loadState)
            assertNull(f.image().photo.attachment)
            f.photoBase.attachment.value = f.attachment
            runCurrent()
            assertEquals(f.attachment, f.image().photo.attachment)
            assertEquals(1, f.profileSource.requests)
            assertEquals(listOf("blob-1"), f.photoBase.downloads)
        }

    /** Replacement/removal invalidate obsolete references, including noncooperative URL work. */
    @Test fun replacementAndRemovalCannotExposeThePreviousBlobUrl() =
        scenario { f ->
            val held = CompletableDeferred<String>()
            f.urlGates.add(held)
            f.urlHandler = { blob ->
                if (blob ==
                    "blob-1"
                ) {
                    withContext(NonCancellable) { held.await() }
                } else {
                    "file:///new"
                }
            }
            f.photoBase.attachment.value = f.attachment
            f.model.openImage(f.accountTarget)
            runCurrent()
            assertTrue(f.image().photo.isResolvingUrl)
            val replacement = f.attachment.copy(blobId = "blob-2")
            f.photoBase.attachment.value = replacement
            runCurrent()
            assertEquals(replacement, f.image().photo.attachment)
            assertEquals("file:///new", f.image().photo.displayUrl)
            held.complete("file:///old")
            runCurrent()
            assertEquals("file:///new", f.image().photo.displayUrl)
            f.photoBase.attachment.value = null
            runCurrent()
            assertNull(f.image().photo.attachment)
            assertNull(f.image().photo.displayUrl)
        }

    /** Retrying attachment/URL failures never touches writes or the independent parent. */
    @Test fun photoRetryIsReadOnlyAndSectional() =
        scenario { f ->
            f.photoBase.streamFailure.value = IOException("attachment offline")
            f.photoBase.attachment.value = f.attachment
            f.model.openImage(f.accountTarget)
            runCurrent()
            assertEquals(ImageLoadState.FAILED, f.image().photo.loadState)
            f.photoBase.streamFailure.value = null
            f.photoBase.urlFailures["blob-1"] = IOException("bytes offline")
            f.model.retryPhoto()
            runCurrent()
            assertTrue(f.image().photo.displayFailed)
            assertEquals(f.attachment, f.image().photo.attachment)
            f.photoBase.urlFailures.clear()
            f.model.retryPhoto()
            runCurrent()
            assertFalse(f.image().photo.displayFailed)
            assertEquals(1, f.profileSource.requests)
            assertEquals(0, f.photoBase.replaceCalls + f.photoBase.deleteCalls)
        }

    /** An outgoing target cannot close its replacement; matching close releases every reader. */
    @Test fun closeSelectionIsTargetQualifiedAndReleasesReaders() =
        scenario { f ->
            f.model.openProfile(f.profile.id)
            runCurrent()
            f.model.openImage(f.siteTarget)
            runCurrent()
            f.model.closeSelection(AdminInspectionTarget.Profile(f.profile.id))
            assertEquals(f.siteTarget, f.image().target)
            f.model.closeSelection(AdminInspectionTarget.Image(f.siteTarget))
            runCurrent()
            assertEquals(AdminInspectionUiState.None, f.model.state.value)
            assertEquals(0, f.siteSource.active + f.profileSource.active + f.ownedSource.active)
            assertEquals(0, f.photoBase.activeObservers)
        }

    /** An account profile and its image are separate scopes despite sharing the same raw ID. */
    @Test fun profileAndAccountImageHaveDistinctSelectionIdentity() =
        scenario { f ->
            f.model.openProfile(f.profile.id)
            runCurrent()
            assertEquals(1, f.ownedSource.active)
            f.model.openImage(f.accountTarget)
            runCurrent()
            assertEquals(f.accountTarget, f.image().target)
            assertEquals(0, f.ownedSource.active)
            assertEquals(2, f.profileSource.requests)
            f.model.openImage(f.accountTarget)
            runCurrent()
            assertEquals(2, f.profileSource.requests)
        }

    /** Retired owners reject late reads and later actions without leaking a source or mutation. */
    @Test fun retirementRejectsLateCallbacksAndActions() =
        scenario { f ->
            val late = CompletableDeferred<Account?>()
            f.profileSource.late = late
            f.model.openProfile(f.profile.id)
            runCurrent()
            f.model.close()
            late.complete(f.profile)
            runCurrent()
            f.model.openProfile(f.other.id)
            f.model.openImage(f.siteTarget)
            f.model.retryProfile()
            f.model.retryParent()
            f.model.retryPhoto()
            f.model.retryCampsites()
            runCurrent()
            assertEquals(AdminInspectionUiState.None, f.model.state.value)
            assertEquals(1, f.profileSource.requests)
            assertEquals(0, f.otherSource.requests + f.siteSource.requests + f.ownedSource.requests)
            assertTrue(f.photoBase.observedTargets.isEmpty())
        }

    /** Runs a fixed contract with deterministic dispatcher and guaranteed scope cleanup.
     * @param block One predeclared scenario; helper predicates are not implemented here.
     */
    private fun scenario(block: suspend TestScope.(Fixture) -> Unit) =
        runTest(main.dispatcher) {
            val f = Fixture(CoroutineScope(SupervisorJob() + main.dispatcher))
            try {
                block(f)
            } finally {
                f.model.close()
                f.urlGates.forEach { it.complete("file:///released") }
                f.scope.cancel()
                f.profileSource.late?.complete(f.profile)
                f.otherSource.late?.complete(f.other)
                f.profileSource.gate?.complete(Unit)
                runCurrent()
                assertEquals(0, f.accountBase.writes + f.siteBase.writeCalls)
                assertEquals(0, f.photoBase.replaceCalls + f.photoBase.deleteCalls)
            }
        }

    /** Exact saved sources and delegated mutation-rejecting repositories.
     * @param scope Lifetime explicitly cancelled by the scenario.
     */
    private class Fixture(
        val scope: CoroutineScope,
    ) {
        val profile = sampleAccount().copy(id = "camper/α ?")
        val other = profile.copy(id = "other", name = "Taylor")
        val site = sampleCampsite("site/one", profile.id)
        val accountTarget = ImageTarget(ImageTargetType.ACCOUNT, profile.id)
        val siteTarget = ImageTarget(ImageTargetType.CAMPSITE, site.id)
        val attachment = ImageAttachment(accountTarget, "blob-1", Instant.EPOCH)
        val profileSource = Stream<Account?>(profile)
        val otherSource = Stream<Account?>(other)
        val siteSource = Stream<Campsite?>(site)
        val ownedSource = Stream(listOf(site))
        val accountBase = FakeAccountRepository()
        val siteBase = FakeCampsiteRepository()
        val photoBase = FakeImageRepository()
        val urlGates = mutableListOf<CompletableDeferred<String>>()
        var urlHandler: (suspend (String) -> String)? = null
        val accounts =
            object : AccountRepository by accountBase {
                /** Supplies only the exact requested saved profile.
                 * @param id Actual selected account.
                 */
                override fun observe(id: String): Flow<Account?> =
                    if (id == profile.id) profileSource else otherSource
            }
        val sites =
            object : CampsiteRepository by siteBase {
                /** Supplies one actual saved listing.
                 * @param id Actual selected campsite.
                 */
                override fun observe(id: String): Flow<Campsite?> =
                    siteSource

                /** Supplies controlled owner-scoped rows without inventing a hidden filter.
                 * @param ownerId Requested account.
                 */
                override fun observeForOwner(ownerId: String): Flow<List<Campsite>> =
                    ownedSource
            }
        val photos =
            object : ImageRepository by photoBase {
                /** Supports explicit noncooperative URL completion while recording real read IDs.
                 * @param blobId Current attachment's immutable blob.
                 */
                override suspend fun downloadUrl(blobId: String): String {
                    val handler = urlHandler ?: return photoBase.downloadUrl(blobId)
                    photoBase.downloads.add(blobId)
                    return handler(blobId)
                }
            }
        val model = AdminInspectionController(accounts, sites, photos, scope)

        /** Requires actual Profile presentation before accessing its independent sections. */
        fun profile(): AdminInspectionUiState.Profile {
            assertTrue(model.state.value is AdminInspectionUiState.Profile)
            return model.state.value as AdminInspectionUiState.Profile
        }

        /** Requires actual Image presentation before accessing its independent sections. */
        fun image(): AdminInspectionUiState.Image {
            assertTrue(model.state.value is AdminInspectionUiState.Image)
            return model.state.value as AdminInspectionUiState.Image
        }
    }

    /** Tracks controllable source lifetime, including deliberately noncooperative completions.
     * @param initial Initial actual repository fact.
     */
    @OptIn(InternalCoroutinesApi::class)
    private class Stream<T>(
        initial: T,
    ) : Flow<T> {
        val values = MutableStateFlow(Result.success(initial))
        var gate: CompletableDeferred<Unit>? = null
        var late: CompletableDeferred<T>? = null
        var requests = 0
        var active = 0

        /** Emits controlled facts and reliably records cancellation cleanup.
         * @param collector Actual consumer callback under test.
         */
        override suspend fun collect(collector: FlowCollector<T>) {
            requests++
            active++
            try {
                val delayed = late
                if (delayed != null) {
                    withContext(NonCancellable) { collector.emit(delayed.await()) }
                } else {
                    gate?.await()
                    values.collect { collector.emit(it.getOrThrow()) }
                }
            } finally {
                active--
            }
        }
    }
}
