package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImagePublicationException
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant

/** Published editor contracts, using real validation and independent image ownership. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteEditViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val site = sampleCampsite()
    private val sites = FakeCampsiteRepository(listOf(site))
    private val images = FakeImageRepository()
    private var sequence = 0
    private val photo =
        SelectedPhoto(
            "content://photo",
            ImageUpload("image/png") {
                ByteArrayInputStream(byteArrayOf(1))
            },
        )

    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, images.activeObservers)
        assertTrue(sites.createRequests.isEmpty())
        assertTrue(sites.publishRequests.isEmpty())
        assertTrue(sites.offerRequests.isEmpty())
        assertTrue(sites.observedCalendars.isEmpty())
    }

    @Test fun loadsAllSavedDetailsAndSavesOnlyUpdatesWithOriginalHistory() =
        runTest {
            val model = model()
            runCurrent()
            assertEquals(site.toDraft(), content(model).draft)
            sites.updateHandler = { id, owner, details ->
                assertEquals(site.id, id)
                assertEquals(site.ownerId, owner)
                sites.records.value = listOf(site.copy(details = details))
            }
            model.editName("  Updated camp  ")
            model.editPrice("72.05")
            model.save()
            assertTrue(content(model).isSaving)
            model.save()
            model.editName("blocked")
            runCurrent()
            assertEquals(1, sites.updateRequests.size)
            assertTrue(model.saveConfirmed.value)
            assertFalse(content(model).isDirty)
            assertEquals(
                site.copy(details = site.details.copy(name = "Updated camp", nightlyPriceCents = 7205)),
                sites.records.value.single(),
            )
            val reopened = model()
            runCurrent()
            assertEquals(
                sites.records.value
                    .single()
                    .toDraft(),
                content(reopened).draft,
            )
        }

    @Test fun invalidRawInputNeverReachesRepository() =
        runTest {
            val model = model()
            runCurrent()
            model.editName(" ")
            model.editCapacity("2.5")
            model.editPrice("12.345")
            model.editTimeZone("not/a/zone")
            model.save()
            runCurrent()
            assertEquals("12.345", content(model).draft.priceText)
            assertTrue(content(model).errors.nameRequired)
            assertTrue(content(model).errors.timeZoneInvalid)
            assertNotNull(content(model).errors.capacity)
            assertNotNull(content(model).errors.price)
            assertTrue(sites.updateRequests.isEmpty())
        }

    @Test fun failureRetainsExactInputForExplicitRetryAndRecreation() =
        runTest {
            val saved = SavedStateHandle()
            val model = model(saved)
            runCurrent()
            model.editName(" Raw name ")
            model.editDescription("line one\nline two")
            model.editCapacity("0004")
            model.editPrice("071.50")
            model.setAmenity("custom", true)
            model.setTag("custom-tag", true)
            sites.updateHandler = { _, _, _ -> throw IOException("offline") }
            model.save()
            runCurrent()
            assertTrue(content(model).saveFailed)
            assertFalse(model.saveConfirmed.value)
            val restored = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
            runCurrent()
            assertEquals(content(model).draft, content(restored).draft)
            sites.updateHandler = { _, _, details -> sites.records.value = listOf(site.copy(details = details)) }
            restored.save()
            runCurrent()
            assertTrue(restored.saveConfirmed.value)
            assertEquals(
                "Raw name",
                sites.records.value
                    .single()
                    .details.name,
            )
        }

    @Test fun cancelAndDismissLeaveSavedDetailsAlone() =
        runTest {
            val model = model()
            runCurrent()
            model.editRules("new rules")
            model.requestExit()
            assertTrue(model.discardPrompt.value)
            model.dismissDiscard()
            assertFalse(model.exitRequested.value)
            assertEquals("new rules", content(model).draft.rules)
            model.requestExit()
            model.confirmDiscard()
            assertTrue(model.exitRequested.value)
            assertEquals(listOf(site), sites.records.value)
            assertTrue(sites.updateRequests.isEmpty())
        }

    @Test fun absentRemovedDraftAndDifferentOwnerNeverBecomeNewRecords() =
        runTest {
            for (record in listOf(
                null,
                site.copy(ownerId = "other"),
                site.copy(publishedAt = null),
                site.copy(removal = Removal(Instant.EPOCH, "admin")),
            )) {
                sites.records.value = listOfNotNull(record)
                val model = model()
                runCurrent()
                assertEquals(CampsiteEditUiState.Unavailable, model.state.value)
                model.save()
            }
            assertTrue(images.observedTargets.isEmpty())
            assertTrue(sites.updateRequests.isEmpty())
        }

    @Test fun readFailureRetriesSameTargetAndRemovalBeforeSaveBlocksUpdate() =
        runTest {
            sites.readFailure = IOException("offline")
            val model = model()
            runCurrent()
            assertEquals(CampsiteEditUiState.Failure, model.state.value)
            sites.readFailure = null
            model.retry()
            runCurrent()
            model.editName("changed")
            sites.records.value = emptyList()
            model.save()
            runCurrent()
            assertEquals(CampsiteEditUiState.Unavailable, model.state.value)
            assertTrue(sites.readIds.all { it == site.id })
            assertTrue(sites.updateRequests.isEmpty())
            model.requestExit()
            assertTrue(model.discardPrompt.value)
        }

    @Test fun photoSelectionAndDetailsSaveAreIndependentAndFailedUploadRetainsCandidate() =
        runTest {
            val model = model()
            runCurrent()
            model.selectPhoto(photo)
            assertEquals(0, images.replaceCalls)
            model.editName("updated")
            sites.updateHandler = { _, _, _ -> }
            model.save()
            runCurrent()
            assertEquals(photo, model.photoState.value.candidate)
            assertEquals(0, images.replaceCalls)
            images.replaceFailure = IOException("upload failed")
            model.savePhoto()
            runCurrent()
            assertEquals(photo, model.photoState.value.candidate)
            assertNotNull(model.photoState.value.failure)
            images.replaceFailure = null
            model.retryPhoto()
            runCurrent()
            assertNull(model.photoState.value.candidate)
            assertNotNull(model.photoState.value.attachment)
            model.removePhoto()
            runCurrent()
            assertNull(model.photoState.value.attachment)
            assertEquals(1, sites.updateRequests.size)
        }

    @Test fun photoInFlightBlocksExitAndUnconfirmedPublicationNeedsRecovery() =
        runTest {
            val model = model()
            runCurrent()
            model.selectPhoto(photo)
            images.uploadGate = CompletableDeferred()
            model.savePhoto()
            model.requestExit()
            assertFalse(model.exitRequested.value)
            assertFalse(model.discardPrompt.value)
            images.replaceFailure =
                ImagePublicationException(
                    io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget(
                        io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType.CAMPSITE,
                        site.id,
                    ),
                    "blob-uncertain",
                    IOException("lost acknowledgement"),
                )
            images.uploadGate!!.complete(Unit)
            runCurrent()
            model.requestExit()
            assertFalse(model.exitRequested.value)
            assertFalse(model.discardPrompt.value)
            assertEquals("blob-uncertain", model.photoState.value.unconfirmedBlobId)
        }

    private fun model(saved: SavedStateHandle = SavedStateHandle()): CampsiteEditViewModel =
        CampsiteEditViewModel(site.ownerId, site.id, sites, images, saved).also {
            store.put("edit-${sequence++}", it)
        }

    private fun content(model: CampsiteEditViewModel) =
        model.state.value as CampsiteEditUiState.Content
}
