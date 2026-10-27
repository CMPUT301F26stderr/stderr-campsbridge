package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant

/** Specifies saved record/attachment outcomes independently of future write controllers. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val site = sampleCampsite()
    private val sites = FakeCampsiteRepository(listOf(site))
    private val images = FakeImageRepository()

    /** Cancels graph-owned readers after each contract. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
    }

    /** Record updates retain exactly one attachment target and never write anything. */
    @Test fun savedRecordUpdatesWithoutReplacingAttachmentOwner() =
        runTest {
            val model = model()
            assertEquals(CampsiteDetailUiState.Loading, model.state.value)
            assertTrue(images.observedTargets.isEmpty())
            runCurrent()
            assertEquals(CampsiteDetailUiState.Content(site), model.state.value)
            sites.records.value =
                listOf(site.copy(details = site.details.copy(name = "Updated camp")))
            runCurrent()
            assertEquals(
                "Updated camp",
                (model.state.value as CampsiteDetailUiState.Content).campsite.details.name,
            )
            assertEquals(listOf(site.id), sites.observedIds)
            assertEquals(
                listOf(ImageTarget(ImageTargetType.CAMPSITE, site.id)),
                images.observedTargets,
            )
            assertEquals(0, images.replaceCalls)
            assertEquals(0, images.deleteCalls)
        }

    /** Missing, removed and foreign-owner management records are unavailable, not fake content. */
    @Test fun incompatibleRecordsReleaseTheirPhotoReader() =
        runTest {
            val model = model()
            runCurrent()
            for (records in listOf(
                emptyList(),
                listOf(site.copy(removal = Removal(Instant.EPOCH, "admin"))),
                listOf(site.copy(ownerId = "someone-else")),
            )) {
                sites.records.value = records
                runCurrent()
                assertEquals(CampsiteDetailUiState.Unavailable, model.state.value)
                assertEquals(0, images.activeObservers)
            }
            sites.records.value = listOf(site)
            runCurrent()
            assertEquals(CampsiteDetailUiState.Content(site), model.state.value)
            assertEquals(1, images.activeObservers)
        }

    /** Source retry restores one listener without confusing transport failure with absence. */
    @Test fun recordFailureCanRetryWithoutLeakingReaders() =
        runTest {
            sites.streamFailure.value = IOException("offline")
            val model = model()
            runCurrent()
            assertEquals(CampsiteDetailUiState.Failure, model.state.value)
            assertTrue(images.observedTargets.isEmpty())
            sites.streamFailure.value = null
            model.retry()
            model.retry()
            runCurrent()
            assertEquals(CampsiteDetailUiState.Content(site), model.state.value)
            assertEquals(1, sites.activeObservers)
            assertEquals(1, images.activeObservers)
        }

    /** A failed attachment does not erase the saved listing and retries read-only. */
    @Test fun attachmentFailureIsSectionalAndRetryable() =
        runTest {
            images.observationFailure = IOException("photo offline")
            val model = model()
            runCurrent()
            assertEquals(CampsiteDetailUiState.Content(site), model.state.value)
            assertEquals(ImageLoadState.FAILED, model.photoState.value.loadState)
            images.observationFailure = null
            model.retryPhoto()
            runCurrent()
            assertEquals(ImageLoadState.READY, model.photoState.value.loadState)
            assertEquals(0, images.replaceCalls)
            assertEquals(0, images.deleteCalls)
        }

    /** Late URL work cannot update a cleared graph or keep its observers alive. */
    @Test fun clearCancelsPendingImageAndRecordReads() =
        runTest {
            images.attachment.value =
                ImageAttachment(
                    ImageTarget(ImageTargetType.CAMPSITE, site.id),
                    "photo",
                    Instant.EPOCH,
                )
            images.urlGates["photo"] = CompletableDeferred()
            val model = model()
            runCurrent()
            assertEquals(1, sites.activeObservers)
            assertEquals(1, images.activeObservers)
            val before = model.photoState.value
            store.clear()
            runCurrent()
            images.urlGates.getValue("photo").complete("late-url")
            runCurrent()
            assertEquals(before, model.photoState.value)
            assertEquals(0, sites.activeObservers)
            assertEquals(0, images.activeObservers)
        }

    /** Creates one tracked management reader. */
    private fun model(): CampsiteDetailViewModel =
        CampsiteDetailViewModel(
            "account-1",
            site.id,
            CampsiteDetailContext.MANAGE,
            sites,
            images,
            FakeAccountRepository(),
            FakeReviewRepository(),
            SavedStateHandle(),
            Clock.systemUTC(),
        ).also { store.put("detail", it) }
}
