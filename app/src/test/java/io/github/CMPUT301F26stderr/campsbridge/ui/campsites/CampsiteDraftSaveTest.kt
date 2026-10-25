package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImagePublicationException
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Explicit details/photo save contracts; inventory writes and publishing are a later slice. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteDraftSaveTest {
    @get:Rule val main = MainDispatcherRule()
    private val site = sampleCampsite(published = false)
    private val sites = FakeCampsiteRepository(listOf(site))
    private val images = FakeImageRepository()
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC)
    private var allocations = 0
    private var models = 0
    private val photo =
        SelectedPhoto(
            "content://test/save-photo",
            ImageUpload("image/png") { ByteArrayInputStream(byteArrayOf(1)) },
        )

    /** Cancels retained work and verifies that each actual reader releases its subscription. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, images.activeObservers)
        assertEquals(0, sites.activeCalendarObservers)
    }

    /** Neither a non-review action nor an invalid review allocates an identity or writes data. */
    @Test fun saveRequiresReviewAndValidDetails() =
        runTest {
            val model = model()
            runCurrent()
            model.saveDraft()
            review(model)
            model.editName("  ")
            model.saveDraft()
            runCurrent()
            assertTrue(content(model).errors.nameRequired)
            assertEquals(0, allocations)
            assertEquals(0, sites.writeCalls)
        }

    /** The ID and operation guard are established before suspension and exact valid facts save. */
    @Test fun heldCreateFreezesInputAndUsesOneIdentity() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            sites.createHandler = { id, owner, details ->
                gate.await()
                site.copy(id = id, ownerId = owner, details = details)
            }
            val model = model()
            runCurrent()
            review(model)
            model.saveDraft()
            assertEquals(CampsiteSavePhase.SAVING_DETAILS, content(model).savePhase)
            model.saveDraft()
            model.editName("Should be ignored")
            model.back()
            model.requestExit()
            runCurrent()
            assertEquals(1, allocations)
            assertEquals(1, sites.createRequests.size)
            assertEquals("draft-new", sites.createRequests.single().first)
            assertEquals(
                "River Camp",
                sites.createRequests
                    .single()
                    .third.name,
            )
            assertEquals(
                6500L,
                sites.createRequests
                    .single()
                    .third.nightlyPriceCents,
            )
            assertEquals(" River Camp ", content(model).draft.name)
            assertEquals(CampsiteStep.REVIEW, content(model).draft.step)
            assertFalse(model.exitRequested.value)
            assertFalse(content(model).discardPrompt)
            gate.complete(Unit)
            runCurrent()
            assertEquals("draft-new", model.completion.value)
            assertEquals(0, images.replaceCalls)
        }

    /** A creation retry cannot overwrite another confirmed set of fields implicitly. */
    @Test fun returnedExistingDraftRequiresExplicitDetailsUpdate() =
        runTest {
            sites.createHandler = { id, _, _ -> site.copy(id = id) }
            sites.updateHandler = { _, _, _ -> }
            val model = model()
            runCurrent()
            review(model)
            model.editDescription("Explicit description")
            model.saveDraft()
            runCurrent()
            assertEquals(
                "Explicit description",
                sites.updateRequests
                    .single()
                    .third.description,
            )
            assertEquals("draft-new", model.completion.value)
        }

    /** A saved draft is updated by its actual ID without allocating or creating another record. */
    @Test fun existingDraftUsesExplicitUpdate() =
        runTest {
            sites.updateHandler = { _, _, _ -> }
            val model = model(site.id)
            runCurrent()
            review(model)
            model.editName("Updated site")
            model.saveDraft()
            runCurrent()
            assertEquals(
                Triple(site.id, site.ownerId, site.details.copy(name = "Updated site")),
                sites.updateRequests.single(),
            )
            assertTrue(sites.createRequests.isEmpty())
            assertEquals(0, allocations)
            assertEquals(site.id, model.completion.value)
        }

    /** A lost create acknowledgement is reconciled by server get before any further mutation. */
    @Test fun committedCreateWithLostAcknowledgementDoesNotCreateAgain() =
        runTest {
            sites.createHandler = { id, owner, details ->
                sites.records.value += site.copy(id = id, ownerId = owner, details = details)
                throw IOException("ack lost")
            }
            val model = model()
            runCurrent()
            review(model)
            model.saveDraft()
            runCurrent()
            assertEquals(CampsiteSaveFailure.DETAILS, content(model).saveFailure)
            assertNull(model.completion.value)
            model.retrySave()
            runCurrent()
            assertEquals(1, sites.createRequests.size)
            assertTrue(sites.updateRequests.isEmpty())
            assertEquals(1, allocations)
            assertEquals("draft-new", model.completion.value)
        }

    /** A failed recovery read cannot be treated as confirmed absence and repeat a create. */
    @Test fun recoveryFailureDoesNotAuthorizeAnotherCreate() =
        runTest {
            sites.createHandler = { _, _, _ -> throw IOException("not saved") }
            val model = model()
            runCurrent()
            review(model)
            model.saveDraft()
            runCurrent()
            sites.readFailure = IOException("unknown")
            model.retrySave()
            runCurrent()
            assertEquals(1, sites.createRequests.size)
            assertEquals(CampsiteSaveFailure.RECOVERY, content(model).saveFailure)
            sites.readFailure = null
            sites.createHandler =
                { id, owner, details -> site.copy(id = id, ownerId = owner, details = details) }
            model.retrySave()
            runCurrent()
            assertEquals(listOf("draft-new", "draft-new"), sites.createRequests.map { it.first })
            assertEquals(1, allocations)
            assertEquals("draft-new", model.completion.value)
        }

    /** Photo bytes cannot publish before the valid parent create has actually completed. */
    @Test fun photoWaitsForParentAndUsesActualTarget() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            sites.createHandler = { id, owner, details ->
                gate.await()
                site.copy(id = id, ownerId = owner, details = details)
            }
            images.uploadGate = CompletableDeferred()
            val model = model()
            runCurrent()
            review(model, photo)
            model.saveDraft()
            runCurrent()
            assertEquals(0, images.replaceCalls)
            gate.complete(Unit)
            runCurrent()
            assertEquals(CampsiteSavePhase.SAVING_PHOTO, content(model).savePhase)
            assertEquals(
                listOf(ImageTarget(ImageTargetType.CAMPSITE, "draft-new")),
                images.replacementTargets,
            )
            model.saveDraft()
            assertEquals(1, images.replaceCalls)
            images.uploadGate!!.complete(Unit)
            runCurrent()
            assertEquals("draft-new", model.completion.value)
        }

    /** A later photo failure keeps details confirmed and retry uploads only the staged image. */
    @Test fun photoFailureRetainsConfirmedDetailsAndSelection() =
        runTest {
            sites.createHandler = { id, owner, details ->
                site
                    .copy(
                        id = id,
                        ownerId = owner,
                        details = details,
                    ).also { sites.records.value += it }
            }
            images.replaceFailure = IOException("upload failed")
            val model = model()
            runCurrent()
            review(model, photo)
            model.saveDraft()
            runCurrent()
            assertEquals(CampsiteSaveFailure.PHOTO, content(model).saveFailure)
            assertTrue(content(model).photoIntent is CampsitePhotoIntent.Replace)
            images.replaceFailure = null
            model.retrySave()
            runCurrent()
            assertEquals(1, sites.createRequests.size)
            assertTrue(sites.updateRequests.isEmpty())
            assertEquals(2, images.replaceCalls)
            assertEquals("draft-new", model.completion.value)
        }

    /** Ambiguous publication reconciles the uploaded blob instead of uploading it again. */
    @Test fun uncertainPhotoReconcilesWithoutReupload() =
        runTest {
            val model = model(site.id)
            runCurrent()
            review(model, photo)
            images.replaceFailure =
                ImagePublicationException(
                    ImageTarget(ImageTargetType.CAMPSITE, site.id),
                    "uploaded-blob",
                    IOException("ack lost"),
                )
            model.saveDraft()
            runCurrent()
            assertEquals(CampsiteSaveFailure.PHOTO, content(model).saveFailure)
            images.attachment.value =
                ImageAttachment(
                    ImageTarget(ImageTargetType.CAMPSITE, site.id),
                    "uploaded-blob",
                    clock.instant(),
                )
            model.retrySave()
            runCurrent()
            assertEquals(1, images.replaceCalls)
            assertTrue(images.getCalls > 0)
            assertEquals(site.id, model.completion.value)
        }

    /** Reading the saved preview cannot accidentally repeat a failed staged replacement. */
    @Test fun previewRetryDoesNotPublishTheStagedPhoto() =
        runTest {
            val model = model(site.id)
            runCurrent()
            review(model, photo)
            images.replaceFailure = IOException("upload failed")
            model.saveDraft()
            runCurrent()
            assertEquals(1, images.replaceCalls)
            images.replaceFailure = null
            model.retryPhoto()
            runCurrent()
            assertEquals(1, images.replaceCalls)
            assertNull(model.completion.value)
            model.retrySave()
            runCurrent()
            assertEquals(site.id, model.completion.value)
        }

    /** An unresolved blob publication cannot be abandoned through ordinary discard navigation. */
    @Test fun uncertainPhotoPublicationBlocksAbandonmentUntilRecovered() =
        runTest {
            val model = model(site.id)
            runCurrent()
            review(model, photo)
            images.replaceFailure =
                ImagePublicationException(
                    ImageTarget(ImageTargetType.CAMPSITE, site.id),
                    "pending-blob",
                    IOException("unknown"),
                )
            model.saveDraft()
            runCurrent()
            model.requestExit()
            model.confirmDiscard()
            assertFalse(model.exitRequested.value)
            assertFalse(content(model).discardPrompt)
            assertEquals(1, images.activeObservers)
        }

    /** Retry on review reopens a failed attachment read without requiring another wizard walk. */
    @Test fun failedAttachmentReadCanRetryAtReview() =
        runTest {
            images.observationFailure = IOException("attachment read failed")
            val model = model(site.id)
            runCurrent()
            review(model, photo)
            model.saveDraft()
            runCurrent()
            assertEquals(CampsiteSaveFailure.PHOTO, content(model).saveFailure)
            images.observationFailure = null
            model.retrySave()
            runCurrent()
            assertEquals(1, images.replaceCalls)
            assertEquals(site.id, model.completion.value)
        }

    /** Explicit remove intent goes through the shared controller and waits for acknowledgement. */
    @Test fun savedPhotoRemovalIsPartOfTheExplicitReviewSave() =
        runTest {
            images.attachment.value =
                ImageAttachment(
                    ImageTarget(ImageTargetType.CAMPSITE, site.id),
                    "old-photo",
                    clock.instant(),
                )
            images.deleteGate = CompletableDeferred()
            val model = model(site.id)
            runCurrent()
            review(model)
            model.removePhoto()
            assertEquals(0, images.deleteCalls)
            model.saveDraft()
            runCurrent()
            assertEquals(1, images.deleteCalls)
            assertNull(model.completion.value)
            images.deleteGate!!.complete(Unit)
            runCurrent()
            assertNull(images.attachment.value)
            assertEquals(site.id, model.completion.value)
        }

    /** Keep cannot imply undoing a deletion whose result has not yet been confirmed. */
    @Test fun failedRemovalCannotBeDiscardedIntoSuccessfulKeep() =
        runTest {
            images.attachment.value =
                ImageAttachment(
                    ImageTarget(ImageTargetType.CAMPSITE, site.id),
                    "old-photo",
                    clock.instant(),
                )
            images.deleteFailure = IOException("acknowledgement unknown")
            val model = model(site.id)
            runCurrent()
            review(model)
            model.removePhoto()
            model.saveDraft()
            runCurrent()
            images.attachment.value = null
            runCurrent()
            model.discardPhoto()
            model.requestExit()
            assertEquals(CampsitePhotoIntent.Remove, content(model).photoIntent)
            assertFalse(content(model).discardPrompt)
            images.deleteFailure = null
            model.retrySave()
            runCurrent()
            assertEquals(2, images.deleteCalls)
            assertEquals(site.id, model.completion.value)
        }

    /** Interrupted removal stays pending across recreation until an explicit retry confirms it. */
    @Test fun failedRemovalRetainsItsRecoveryGuardAfterRecreation() =
        runTest {
            val saved = SavedStateHandle()
            images.deleteFailure = IOException("result unknown")
            val first = model(site.id, saved)
            runCurrent()
            review(first)
            first.removePhoto()
            first.saveDraft()
            runCurrent()
            val snapshot = copy(saved)
            store.clear()
            val restored = model(site.id, snapshot)
            runCurrent()
            restored.discardPhoto()
            restored.requestExit()
            assertEquals(CampsitePhotoIntent.Remove, content(restored).photoIntent)
            assertFalse(content(restored).discardPrompt)
            images.deleteFailure = null
            restored.retrySave()
            runCurrent()
            assertEquals(2, images.deleteCalls)
            assertEquals(site.id, restored.completion.value)
        }

    /** Reconstructed transient bytes need reselection or explicit discard, never an automatic upload. */
    @Test fun interruptedPhotoSaveRestoresInputAndRequiresReselection() =
        runTest {
            val saved = SavedStateHandle()
            images.uploadGate = CompletableDeferred()
            val first = model(site.id, saved)
            runCurrent()
            review(first, photo)
            first.saveDraft()
            runCurrent()
            assertEquals(1, images.replaceCalls)
            val snapshot = copy(saved)
            store.clear()
            runCurrent()
            val restored = model(site.id, snapshot)
            runCurrent()
            assertTrue(content(restored).photoReselectionRequired)
            restored.retrySave()
            runCurrent()
            assertEquals(CampsiteSaveFailure.PHOTO_RESELECTION, content(restored).saveFailure)
            assertEquals(1, images.replaceCalls)
            restored.discardPhoto()
            restored.retrySave()
            runCurrent()
            assertEquals(site.id, restored.completion.value)
            assertEquals(1, images.replaceCalls)
        }

    /** Recreated uncommitted new ID remains local and retries only after an explicit action. */
    @Test fun recreationOfUncommittedAttemptDoesNotReplayWrite() =
        runTest {
            val saved = SavedStateHandle()
            sites.createHandler = { _, _, _ -> throw IOException("not saved") }
            val first = model(saved = saved)
            runCurrent()
            review(first)
            first.saveDraft()
            runCurrent()
            store.clear()
            val restored = model(saved = copy(saved))
            runCurrent()
            assertEquals("draft-new", content(restored).draft.id)
            assertEquals(CampsiteStep.REVIEW, content(restored).draft.step)
            assertEquals(1, sites.createRequests.size)
            sites.createHandler =
                { id, owner, details -> site.copy(id = id, ownerId = owner, details = details) }
            restored.retrySave()
            runCurrent()
            assertEquals(2, sites.createRequests.size)
            assertEquals(1, allocations)
            assertEquals("draft-new", restored.completion.value)
        }

    /** Completion survives failed reads and reconstruction without submitting again. */
    @Test fun confirmedCompletionRestoresIndependentlyOfSourceRead() =
        runTest {
            val saved = SavedStateHandle()
            val first = model(site.id, saved)
            runCurrent()
            review(first)
            first.saveDraft()
            runCurrent()
            val reads = sites.readIds.size
            store.clear()
            sites.readFailure = IOException("offline after save")
            val restored = model(site.id, copy(saved))
            runCurrent()
            assertEquals(site.id, restored.completion.value)
            assertEquals(reads, sites.readIds.size)
            restored.acknowledgeCompletion()
            assertNull(restored.completion.value)
        }

    /** Date intentions are never silently lost while their persistence integration is pending. */
    @Test fun pendingAvailabilityBlocksDraftCompletionInsteadOfDroppingDates() =
        runTest {
            val model = model()
            runCurrent()
            review(model)
            model.editStep(CampsiteStep.AVAILABILITY)
            model.toggleDate(java.time.LocalDate.of(2027, 5, 14))
            model.next()
            model.saveDraft()
            runCurrent()
            assertEquals(CampsiteSaveFailure.AVAILABILITY_PENDING, content(model).saveFailure)
            assertEquals(0, allocations)
            assertEquals(0, sites.writeCalls)
            assertNull(model.completion.value)
        }

    /** Details and selected photo must finish before any explicit offered-night write. */
    @Test fun inventoryWaitsForConfirmedDetailsAndPhoto() =
        runTest {
            val order = mutableListOf<String>()
            sites.createHandler = { id, owner, details ->
                order.add("details")
                site.copy(id = id, ownerId = owner, details = details)
            }
            images.uploadGate = CompletableDeferred()
            sites.offerHandler = { id, owner, dates, offered ->
                order.add("night")
                assertEquals("draft-new", id)
                assertEquals(site.ownerId, owner)
                assertEquals(setOf(java.time.LocalDate.of(2027, 5, 14)), dates)
                assertTrue(offered)
                assertEquals(1, images.replaceCalls)
            }
            val model = model(difference = { requested, _ -> requested })
            runCurrent()
            review(model, photo)
            model.editStep(CampsiteStep.AVAILABILITY)
            model.toggleDate(java.time.LocalDate.of(2027, 5, 14))
            model.next()
            model.saveDraft()
            runCurrent()
            assertEquals(listOf("details"), order)
            assertEquals(CampsiteSavePhase.SAVING_PHOTO, content(model).savePhase)
            images.uploadGate!!.complete(Unit)
            runCurrent()
            assertEquals(listOf("details", "night"), order)
            assertEquals("draft-new", model.completion.value)
            assertTrue(content(model).draft.requestedAvailability.isEmpty())
        }

    /** Failed later nights retain only unresolved requests and do not repeat an acknowledged photo. */
    @Test fun inventoryRetryRetainsPhotoAndConfirmedDates() =
        runTest {
            val first = java.time.LocalDate.of(2027, 5, 14)
            val second = first.plusDays(1)
            sites.offerHandler = { _, _, dates, _ ->
                if (dates.single() == second) throw IOException("second night failed")
            }
            val model = model(site.id, difference = { requested, _ -> requested })
            runCurrent()
            review(model, photo)
            model.editStep(CampsiteStep.AVAILABILITY)
            model.toggleDate(second)
            model.toggleDate(first)
            model.next()
            model.saveDraft()
            runCurrent()
            assertEquals(CampsiteSaveFailure.AVAILABILITY, content(model).saveFailure)
            assertEquals(mapOf(second to true), content(model).draft.requestedAvailability)
            assertEquals(1, images.replaceCalls)
            assertNull(model.completion.value)
            sites.offerHandler = { _, _, _, _ -> }
            model.retrySave()
            runCurrent()
            assertEquals(
                listOf(first, second, second),
                sites.offerRequests.map { it.dates.single() },
            )
            assertEquals(1, images.replaceCalls)
            assertEquals(site.id, model.completion.value)
        }

    /** An inventory failure recreates with the same pending withdrawal, never an implicit replay. */
    @Test fun restoredInventoryFailureRequiresExplicitRetry() =
        runTest {
            val date = java.time.LocalDate.of(2027, 5, 14)
            val saved = SavedStateHandle()
            sites.calendarNights.value = listOf(CalendarNight(date, true))
            sites.offerHandler = { _, _, _, _ -> throw IOException("acknowledgement lost") }
            val first = model(site.id, saved, difference = { requested, _ -> requested })
            runCurrent()
            review(first)
            first.editStep(CampsiteStep.AVAILABILITY)
            first.toggleDate(date)
            first.next()
            first.saveDraft()
            runCurrent()
            val snapshot = copy(saved)
            store.clear()
            sites.calendarFailure.value = IOException("unknown month")
            val restored = model(site.id, snapshot, difference = { requested, _ -> requested })
            runCurrent()
            assertEquals(mapOf(date to false), content(restored).draft.requestedAvailability)
            assertEquals(1, sites.offerRequests.size)
            assertNull(restored.completion.value)
            sites.offerHandler = { _, _, _, offered -> assertFalse(offered) }
            restored.retrySave()
            runCurrent()
            assertEquals(2, sites.offerRequests.size)
            assertEquals(site.id, restored.completion.value)
        }

    /** Constructs one graph-owned editor with a counted deterministic identity source.
     * @param id Saved target or null for a new form.
     * @param saved Small state from this or a previous process.
     * @param difference Fixed comparison outcome for these lead-owned orchestration tests.
     */
    private fun model(
        id: String? = null,
        saved: SavedStateHandle = SavedStateHandle(),
        difference: AvailabilityDifference? = null,
    ): CampsiteSetupViewModel =
        CampsiteSetupViewModel(site.ownerId, id, sites, images, clock, saved, newId = {
            allocations++
            "draft-new"
        }, remainingAvailabilityEdits = difference).also { store.put("save-${models++}", it) }

    /** Walks real advancement with valid details and an optional local photo selection.
     * @param model Actual controller under test.
     * @param selected Optional fixture held only by the active model.
     */
    private fun review(
        model: CampsiteSetupViewModel,
        selected: SelectedPhoto? = null,
    ) {
        model.editName(" River Camp ")
        model.selectLocation(Coordinates(53.0, -118.0))
        model.editTimeZone("America/Edmonton")
        model.editCapacity("8")
        model.editPrice("65.00")
        model.next()
        if (selected != null) model.selectPhoto(selected)
        repeat(6) { model.next() }
        assertEquals(CampsiteStep.REVIEW, content(model).draft.step)
    }

    /** Requires the explicit content state without discarding action feedback.
     * @param model Actual editor.
     */
    private fun content(model: CampsiteSetupViewModel): CampsiteSetupUiState.Content =
        model.state.value as CampsiteSetupUiState.Content

    /** Simulates process reconstruction without retaining the original state handle.
     * @param saved Previous primitive fields and independent completion.
     */
    private fun copy(saved: SavedStateHandle): SavedStateHandle =
        SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
}
