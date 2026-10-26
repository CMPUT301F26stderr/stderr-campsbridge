package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Freezes owner scope, tab restoration and thumbnail lifetime before the list is wired. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteListViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val published = sampleCampsite()
    private val draft = sampleCampsite("draft", published = false)
    private val sites = FakeCampsiteRepository(listOf(published, draft))
    private val images = FakeImageRepository()

    /** Cancels owned observers after each contract. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
    }

    /** Removed records never become drafts and no administrative query is used. */
    @Test fun ownerSourceSeparatesActivePublishedAndDraftRecords() =
        runTest {
            sites.records.value +=
                listOf(
                    sampleCampsite("removed").copy(removal = Removal(Instant.EPOCH, "admin")),
                    sampleCampsite("other", "other-owner"),
                )
            val model = model()
            assertEquals(CampsiteListUiState.Loading, model.state.value)
            runCurrent()
            assertEquals(listOf(published), content(model).campsites)
            assertTrue(content(model).orderingPending)
            model.selectTab(CampsiteListTab.DRAFTS)
            runCurrent()
            assertEquals(listOf(draft), content(model).campsites)
            assertEquals(listOf("account-1"), sites.observedOwners)
            assertEquals(0, sites.allObservations)
            assertEquals(0, images.replaceCalls)
        }

    /** Lead orchestration consumes a fixed helper result without implementing F11. */
    @Test fun injectedOrderingAndSavedTabAreUsed() =
        runTest {
            val second = sampleCampsite("second", published = false)
            sites.records.value += second
            val handle = SavedStateHandle()
            var suppliedIds = emptyList<String>()
            val model =
                CampsiteListViewModel(
                    "account-1",
                    sites,
                    images,
                    handle,
                    sortCampsites = { rows ->
                        suppliedIds = rows.map { it.id }
                        listOf(second, draft)
                    },
                )
            store.put("list", model)
            model.selectTab(CampsiteListTab.DRAFTS)
            runCurrent()
            assertEquals(listOf(draft.id, second.id), suppliedIds)
            assertEquals(listOf(second, draft), content(model).campsites)
            assertFalse(content(model).orderingPending)
            val restored =
                model(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
            runCurrent()
            assertEquals(CampsiteListTab.DRAFTS, restored.tab.value)
            assertEquals(listOf(draft, second), content(restored).campsites)
        }

    /** A failed source is retryable and never reported as a successful empty category. */
    @Test fun failedSourceRetryKeepsTabAndOneObserver() =
        runTest {
            val model = model()
            model.selectTab(CampsiteListTab.DRAFTS)
            runCurrent()
            sites.streamFailure.value = IOException("offline")
            runCurrent()
            assertEquals(CampsiteListUiState.Failure, model.state.value)
            assertEquals(0, sites.activeObservers)
            sites.streamFailure.value = null
            model.retry()
            model.retry()
            runCurrent()
            assertEquals(listOf(draft), content(model).campsites)
            assertEquals(1, sites.activeObservers)
            sites.records.value = emptyList()
            runCurrent()
            assertTrue(content(model).campsites.isEmpty())
        }

    /** Only currently visible records in the selected category own image readers. */
    @Test fun visibilityTabAndRemovalCancelThumbnailReaders() =
        runTest {
            val model = model()
            runCurrent()
            assertTrue(images.observedTargets.isEmpty())
            model.setVisibleCampsites(setOf(published.id, draft.id, "unknown"))
            runCurrent()
            assertEquals(listOf(published.id), images.observedTargets.map { it.id })
            assertEquals(1, images.activeObservers)
            model.selectTab(CampsiteListTab.DRAFTS)
            runCurrent()
            assertEquals(0, images.activeObservers)
            model.setVisibleCampsites(setOf(draft.id))
            runCurrent()
            assertEquals(1, images.activeObservers)
            sites.records.value = emptyList()
            runCurrent()
            assertEquals(0, images.activeObservers)
            assertTrue(content(model).photos.isEmpty())
        }

    /** Clearing the feature owner releases both listing and visible-image subscriptions. */
    @Test fun clearReleasesEveryOwnedRead() =
        runTest {
            val model = model()
            runCurrent()
            model.setVisibleCampsites(setOf(published.id))
            runCurrent()
            assertEquals(1, sites.activeObservers)
            assertEquals(1, images.activeObservers)
            store.clear()
            runCurrent()
            assertEquals(0, sites.activeObservers)
            assertEquals(0, images.activeObservers)
        }

    /** Creates a tracked list owner with no substitute ordering algorithm.
     * @param handle Small restorable tab state.
     */
    private fun model(handle: SavedStateHandle = SavedStateHandle()): CampsiteListViewModel =
        CampsiteListViewModel("account-1", sites, images, handle).also {
            store.put("list-" + store.keys().size, it)
        }

    /** Requires a successful source before inspecting rows.
     * @param model List owner under test.
     */
    private fun content(model: CampsiteListViewModel): CampsiteListUiState.Content {
        assertTrue(
            "Expected confirmed list content",
            model.state.value is CampsiteListUiState.Content,
        )
        return model.state.value as CampsiteListUiState.Content
    }
}
