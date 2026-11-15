package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
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

/** Fixed saved-listing contracts for retained input and explicit update operations. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteEditViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val site = sampleCampsite()
    private val sites = FakeCampsiteRepository(listOf(site))
    private val images = FakeImageRepository()
    private val store = ViewModelStore()
    private var modelNumber = 0

    /** Releases the editor's record and image subscriptions between cases. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, sites.activeObservers)
        assertEquals(0, images.activeObservers)
    }

    /** CE-01: raw fields survive fresh observations and a new controller without saving. */
    @Test fun restoresRawDetailsWithoutOverwritingDirtyInput() =
        runTest {
            val saved = SavedStateHandle()
            val editor = model(saved)
            runCurrent()
            assertEquals(site.details.timeZone, content(editor).draft.timeZone)
            editor.editName("River Camp family pitches")
            editor.editMaxGuests("8 people")
            sites.records.value =
                listOf(site.copy(details = site.details.copy(description = "Updated saved description")))
            runCurrent()
            assertEquals("River Camp family pitches", content(editor).draft.name)
            assertEquals("8 people", content(editor).draft.maxGuestsText)
            val restored = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
            runCurrent()
            assertEquals(content(editor).draft, content(restored).draft)
            assertEquals(0, sites.writeCalls)
            assertNull(restored.completion.value)
        }

    /** CE-01: failed observation and confirmed absence have different recovery states. */
    @Test fun distinguishesFailedSourceFromMissingTarget() =
        runTest {
            sites.streamFailure.value = IOException("offline")
            val editor = model()
            runCurrent()
            assertEquals(CampsiteEditUiState.Failure, editor.state.value)
            sites.streamFailure.value = null
            sites.records.value = emptyList()
            editor.retry()
            runCurrent()
            assertEquals(CampsiteEditUiState.Unavailable, editor.state.value)
            assertEquals(0, sites.writeCalls)
        }

    /** CE-02: a valid explicit save updates the same saved identity with complete details. */
    @Test fun savesCompleteDetailsWithoutCreationOrPublication() =
        runTest {
            sites.updateHandler = { _, _, _ -> }
            val editor = model()
            runCurrent()
            editor.editName("River Camp family pitches")
            editor.editDescription("Quiet riverside pitches for a family group.")
            editor.editTimeZone("America/Vancouver")
            editor.save()
            runCurrent()
            assertEquals(
                listOf(
                    Triple(
                        site.id,
                        site.ownerId,
                        site.details.copy(
                            name = "River Camp family pitches",
                            description = "Quiet riverside pitches for a family group.",
                            timeZone = "America/Vancouver",
                        ),
                    ),
                ),
                sites.updateRequests,
            )
            assertTrue(sites.createRequests.isEmpty())
            assertTrue(sites.publishRequests.isEmpty())
            assertEquals(site.id, editor.completion.value)
        }

    /** CE-02: the fixed invalid wiring vectors show parser feedback and make no update. */
    @Test fun blocksBlankNameZeroCapacityAndInvalidZone() =
        runTest {
            for (field in listOf("name", "capacity", "zone")) {
                val editor = model()
                runCurrent()
                when (field) {
                    "name" -> editor.editName(" ")
                    "capacity" -> editor.editMaxGuests("0")
                    "zone" -> editor.editTimeZone("Not/AZone")
                }
                editor.save()
                runCurrent()
                val errors = content(editor).errors
                when (field) {
                    "name" -> assertTrue(errors.nameRequired)
                    "capacity" -> assertTrue(errors.capacity != null)
                    "zone" -> assertTrue(errors.timeZoneInvalid)
                }
            }
            assertEquals(0, sites.writeCalls)
        }

    /** CE-03: failures keep input; explicit retry waits for acknowledgment and blocks repeats. */
    @Test fun retriesSameTargetAndWaitsForConfirmedCompletion() =
        runTest {
            sites.updateHandler = { _, _, _ -> throw IOException("save failed") }
            val editor = model()
            runCurrent()
            editor.editName("River Camp family pitches")
            editor.save()
            runCurrent()
            assertTrue(content(editor).saveFailed)
            assertEquals("River Camp family pitches", content(editor).draft.name)
            assertNull(editor.completion.value)
            val gate = CompletableDeferred<Unit>()
            sites.updateHandler = { _, _, _ -> gate.await() }
            editor.save()
            runCurrent()
            assertTrue(content(editor).isSaving)
            editor.save()
            assertNull(editor.completion.value)
            assertEquals(2, sites.updateRequests.size)
            assertTrue(sites.updateRequests.all { it.first == site.id && it.second == site.ownerId })
            assertEquals(sites.updateRequests.first(), sites.updateRequests.last())
            gate.complete(Unit)
            runCurrent()
            assertFalse(content(editor).isSaving)
            assertEquals(site.id, editor.completion.value)
            editor.acknowledgeCompletion()
            assertNull(editor.completion.value)
        }

    /** Creates a real editor with controlled persistence and a separately releasable lifetime.
     * @param saved Primitive input restored for this saved target.
     */
    private fun model(saved: SavedStateHandle = SavedStateHandle()): CampsiteEditViewModel =
        CampsiteEditViewModel(site.id, site.ownerId, sites, images, saved).also {
            store.put("editor-${modelNumber++}", it)
        }

    /** Requires editable saved input rather than treating loading or failure as empty fields.
     * @param editor Controller under the current case.
     */
    private fun content(editor: CampsiteEditViewModel): CampsiteEditUiState.Content {
        assertTrue(
            "Expected editable saved content, got ${editor.state.value}",
            editor.state.value is CampsiteEditUiState.Content,
        )
        return editor.state.value as CampsiteEditUiState.Content
    }
}
