package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PriceInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Specifies the local editor independently of its later save pipeline and teammate helpers. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteSetupViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val site = sampleCampsite(published = false)
    private val sites = FakeCampsiteRepository(listOf(site))
    private val images = FakeImageRepository()
    private val clock = Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC)
    private var sequence = 0
    private val photo =
        SelectedPhoto(
            "content://test/photo",
            ImageUpload("image/png") { ByteArrayInputStream(byteArrayOf(1)) },
        )

    /** All editor actions remain read-only and release graph-owned work. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, sites.writeCalls)
        assertEquals(0, images.replaceCalls)
        assertEquals(0, images.deleteCalls)
        assertEquals(0, images.activeObservers)
        assertEquals(0, sites.activeCalendarObservers)
    }

    /** A new form has no record identity, saved-parent reader or persistence side effect. */
    @Test fun newFormStartsLocalWithNoAllocatedRecord() =
        runTest {
            val model = model()
            runCurrent()
            assertEquals(CampsiteDraft(), content(model).draft)
            assertEquals(ImageLoadState.READY, content(model).savedPhoto.loadState)
            assertEquals(YearMonth.of(2027, 5), content(model).month)
            assertTrue(sites.readIds.isEmpty())
            assertTrue(images.observedTargets.isEmpty())
        }

    /** Validation on Next is limited to the current step, with raw input retained. */
    @Test fun nameErrorDoesNotInventOtherFieldRequirements() =
        runTest {
            val model = model()
            runCurrent()
            model.editName("  ")
            model.next()
            assertEquals(CampsiteStep.BASICS, content(model).draft.step)
            assertEquals(CampsiteDetailsInput.Errors(nameRequired = true), content(model).errors)
            model.editName(" River Camp ")
            model.next()
            assertEquals(CampsiteStep.PHOTO, content(model).draft.step)
            assertEquals(" River Camp ", content(model).draft.name)
        }

    /** Coordinates and a registered IANA zone are required; labels are optional. */
    @Test fun locationFeedbackBlocksOnlyInvalidAdvance() =
        runTest {
            val model = newAtLocation()
            model.next()
            assertEquals(
                CampsiteDetailsInput.Errors(locationRequired = true, timeZoneInvalid = true),
                content(model).errors,
            )
            model.selectLocation(Coordinates(53.0, -118.0))
            model.editTimeZone("+05:30")
            model.next()
            assertEquals(CampsiteStep.LOCATION, content(model).draft.step)
            assertEquals(CampsiteDetailsInput.Errors(timeZoneInvalid = true), content(model).errors)
            model.editTimeZone(" America/Edmonton ")
            model.next()
            assertEquals(CampsiteStep.CAPACITY_PRICE, content(model).draft.step)
        }

    /** The shared parsers provide feedback without modifying invalid field text. */
    @Test fun capacityAndPriceErrorsPreserveRawText() =
        runTest {
            val model = newAtLocation()
            model.selectLocation(Coordinates(53.0, -118.0))
            model.editTimeZone("America/Edmonton")
            model.next()
            model.editCapacity("0")
            model.editPrice("12.345")
            model.next()
            assertEquals(CampsiteStep.CAPACITY_PRICE, content(model).draft.step)
            assertEquals(
                CampsiteDetailsInput.Errors(
                    capacity = PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                    price = PriceInput.Error.INVALID_FORMAT,
                ),
                content(model).errors,
            )
            assertEquals("12.345", content(model).draft.priceText)
            model.editCapacity("8")
            model.editPrice("0")
            model.next()
            assertEquals(CampsiteStep.OPTIONS, content(model).draft.step)
        }

    /** Eight positions and review edits retain one local draft with optional fields empty. */
    @Test fun eightStepsAndReviewEditHaveOneDraftOwner() =
        runTest {
            val model = model(site.id)
            runCurrent()
            val visited = mutableListOf(content(model).draft.step)
            repeat(7) {
                model.next()
                visited.add(content(model).draft.step)
            }
            assertEquals(CampsiteStep.entries, visited)
            model.editStep(CampsiteStep.BASICS)
            model.editName("")
            model.next()
            assertEquals(CampsiteStep.BASICS, content(model).draft.step)
            model.editName("Revised")
            model.next()
            assertEquals(CampsiteStep.REVIEW, content(model).draft.step)
            assertFalse(content(model).draft.returnToReview)
            model.back()
            assertEquals(CampsiteStep.AVAILABILITY, content(model).draft.step)
        }

    /** Primitive saved input restores without replacing it from the server or writing. */
    @Test fun recreationRestoresSmallRawDraftWithoutReplayingWrites() =
        runTest {
            sites.records.value =
                listOf(site.copy(details = site.details.copy(tags = setOf("unknown"))))
            val saved = SavedStateHandle()
            val model = model(site.id, saved)
            runCurrent()
            repeat(7) { model.next() }
            model.editStep(CampsiteStep.CAPACITY_PRICE)
            model.editCapacity("not a number")
            model.setAmenity("water", true)
            model.setTag("lakeside", true)
            model.changeMonth(YearMonth.of(2027, 6))
            runCurrent()
            model.toggleDate(LocalDate.of(2027, 6, 14))
            val expected = content(model)
            val restored = model(site.id, copy(saved))
            runCurrent()
            assertEquals(expected.draft, content(restored).draft)
            assertEquals(expected.month, content(restored).month)
            assertTrue("unknown" in content(restored).draft.tags)
        }

    /** A failed target lookup is retryable and repeated taps start one replacement read. */
    @Test fun failedExistingReadCanRetry() =
        runTest {
            sites.readFailure = IOException("offline")
            val model = model(site.id)
            runCurrent()
            assertEquals(CampsiteSetupUiState.Failure, model.state.value)
            sites.readFailure = null
            model.retry()
            model.retry()
            runCurrent()
            assertEquals(site.id, content(model).draft.id)
            assertEquals(listOf(site.id, site.id), sites.readIds)
        }

    /** Clearing an in-flight graph cannot publish a late read into its disposed draft. */
    @Test fun clearingHeldReadDoesNotCreateLateReaders() =
        runTest {
            sites.readGate = CompletableDeferred()
            val model = model(site.id)
            runCurrent()
            assertEquals(CampsiteSetupUiState.Loading, model.state.value)
            store.clear()
            sites.readGate!!.complete(Unit)
            runCurrent()
            assertEquals(CampsiteSetupUiState.Loading, model.state.value)
            assertTrue(images.observedTargets.isEmpty())
        }

    /** A published target redirects by its actual identity instead of becoming an editable copy. */
    @Test fun publishedTargetEmitsOneSavedId() =
        runTest {
            sites.records.value = listOf(site.copy(publishedAt = Instant.EPOCH))
            val model = model(site.id)
            runCurrent()
            assertEquals(site.id, model.completion.value)
            assertTrue(images.observedTargets.isEmpty())
            model.acknowledgeCompletion()
            assertEquals(null, model.completion.value)
        }

    /** Missing, removed and foreign-owner targets stay unavailable without photo subscriptions. */
    @Test fun incompatibleTargetsStayUnavailable() =
        runTest {
            for (records in listOf(
                emptyList(),
                listOf(site.copy(ownerId = "another-owner")),
                listOf(site.copy(removal = Removal(Instant.EPOCH, "admin"))),
            )) {
                sites.records.value = records
                val model = model(site.id)
                runCurrent()
                assertEquals(CampsiteSetupUiState.Unavailable, model.state.value)
            }
            assertTrue(images.observedTargets.isEmpty())
        }

    /** Picker cancellation preserves the transient candidate; process restoration needs
     * reselection.
     */
    @Test fun photoIntentIsLocalAndReplacementNeedsReselectionAfterRecreation() =
        runTest {
            val saved = SavedStateHandle()
            val model = model(saved = saved)
            runCurrent()
            model.editName("Camp")
            model.next()
            model.selectPhoto(photo)
            model.selectPhoto(null)
            assertEquals(CampsitePhotoIntent.Replace(photo), content(model).photoIntent)
            model.photoSelectionFailed()
            assertEquals(CampsitePhotoIntent.Replace(photo), content(model).photoIntent)
            assertTrue(content(model).photoSelectionFailed)
            val restored = model(saved = copy(saved))
            runCurrent()
            assertEquals(CampsitePhotoIntent.Keep, content(restored).photoIntent)
            assertTrue(content(restored).photoReselectionRequired)
            restored.discardPhoto()
            assertFalse(content(restored).photoReselectionRequired)
            model.removePhoto()
            assertEquals(CampsitePhotoIntent.Remove, content(model).photoIntent)
            model.discardPhoto()
            assertEquals(CampsitePhotoIntent.Keep, content(model).photoIntent)
        }

    /** Late native selection cannot replace the Keep intent after the user skipped that step. */
    @Test fun latePickerResultAfterPhotoStepDoesNotChangeDraft() =
        runTest {
            val model = newAtLocation()
            model.selectPhoto(photo)
            model.photoSelectionFailed()
            assertEquals(CampsitePhotoIntent.Keep, content(model).photoIntent)
            assertFalse(content(model).photoSelectionFailed)
        }

    /** Explicit abandonment asks once, retains input on dismiss and emits an acknowledged exit. */
    @Test fun discardConfirmationRetainsOrExitsTheSameDraft() =
        runTest {
            val model = model()
            runCurrent()
            model.editName("New camp")
            model.next()
            model.back()
            assertEquals("New camp", content(model).draft.name)
            model.requestExit()
            assertTrue(content(model).discardPrompt)
            assertFalse(model.exitRequested.value)
            model.dismissDiscard()
            assertFalse(content(model).discardPrompt)
            model.requestExit()
            model.confirmDiscard()
            assertTrue(model.exitRequested.value)
            model.acknowledgeExit()
            assertFalse(model.exitRequested.value)
        }

    /** Local date intentions and display month survive navigation without an inventory write. */
    @Test fun availabilityEditsRetainDatesAcrossMonths() =
        runTest {
            val model = model()
            runCurrent()
            val may = LocalDate.of(2027, 5, 14)
            val june = LocalDate.of(2027, 6, 15)
            model.toggleDate(may)
            model.changeMonth(YearMonth.of(2027, 6))
            model.toggleDate(june)
            model.toggleDate(may)
            assertEquals(
                mapOf(may to false, june to true),
                content(model).draft.requestedAvailability,
            )
            assertEquals(YearMonth.of(2027, 6), content(model).month)
        }

    /** Saved inventory is month-scoped, replaced on navigation and remains read-only. */
    @Test fun savedCalendarTracksOneVisibleRange() =
        runTest {
            val may = LocalDate.of(2027, 5, 14)
            sites.calendarNights.value = listOf(CalendarNight(may, true, "reservation"))
            val model = model(site.id)
            runCurrent()
            model.toggleDate(may)
            assertTrue(content(model).draft.requestedAvailability.isEmpty())
            assertEquals(1, sites.activeCalendarObservers)
            model.changeMonth(YearMonth.of(2027, 6))
            runCurrent()
            assertEquals(
                listOf(
                    Triple(site.id, LocalDate.of(2027, 5, 1), LocalDate.of(2027, 6, 1)),
                    Triple(site.id, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 7, 1)),
                ),
                sites.observedCalendars,
            )
            assertEquals(CampsiteInventoryUiState.Content(emptyList()), content(model).inventory)
            assertEquals(1, sites.activeCalendarObservers)
        }

    /** Failed ranges block edits; retry restores only inventory and does not reread the parent. */
    @Test fun unknownCalendarCannotBecomeAnEmptyBaseline() =
        runTest {
            sites.calendarFailure.value = IOException("calendar offline")
            val model = model(site.id)
            runCurrent()
            assertEquals(CampsiteInventoryUiState.Failure, content(model).inventory)
            model.toggleDate(LocalDate.of(2027, 5, 14))
            assertTrue(content(model).draft.requestedAvailability.isEmpty())
            sites.calendarFailure.value = null
            model.retryCalendar()
            model.retryCalendar()
            runCurrent()
            assertEquals(CampsiteInventoryUiState.Content(emptyList()), content(model).inventory)
            assertEquals(listOf(site.id), sites.readIds)
            assertEquals(1, sites.activeCalendarObservers)
        }

    /** A new local form has no persisted nights and does not query an invented parent. */
    @Test fun newCalendarIsKnownEmptyWithoutARepositoryRead() =
        runTest {
            val model = model()
            runCurrent()
            assertEquals(CampsiteInventoryUiState.Content(emptyList()), content(model).inventory)
            model.changeMonth(YearMonth.of(2027, 6))
            runCurrent()
            assertTrue(sites.observedCalendars.isEmpty())
        }

    /** Restored dirty input cannot be abandoned without confirmation during a failed parent
     * read.
     */
    @Test fun failedRestorationRetainsDirtyInputUntilConfirmedDiscard() =
        runTest {
            val saved = SavedStateHandle()
            val first = model(site.id, saved)
            runCurrent()
            first.editName("Unsaved name")
            sites.readFailure = IOException("offline")
            val restored = model(site.id, copy(saved))
            runCurrent()
            restored.requestExit()
            assertFalse(restored.exitRequested.value)
            restored.dismissDiscard()
            assertEquals(CampsiteSetupUiState.Failure, restored.state.value)
            sites.readFailure = null
            restored.retry()
            runCurrent()
            assertEquals("Unsaved name", content(restored).draft.name)
        }

    /** Restored input is also protected while an authoritative read is still held. */
    @Test fun heldRestorationCannotSilentlyDiscardInput() =
        runTest {
            val saved = SavedStateHandle()
            val first = model(site.id, saved)
            runCurrent()
            first.editName("Unsaved name")
            sites.readGate = CompletableDeferred()
            val restored = model(site.id, copy(saved))
            runCurrent()
            restored.requestExit()
            assertFalse(restored.exitRequested.value)
            restored.confirmDiscard()
            assertTrue(restored.exitRequested.value)
        }

    /** An actual saved parent owns one photo reader across steps and closes it with the graph. */
    @Test fun savedPhotoReaderUsesActualIdentityAcrossSteps() =
        runTest {
            val model = model(site.id)
            runCurrent()
            assertEquals(
                listOf(ImageTarget(ImageTargetType.CAMPSITE, site.id)),
                images.observedTargets,
            )
            assertEquals(1, images.activeObservers)
            repeat(7) { model.next() }
            assertEquals(1, images.activeObservers)
            assertEquals(1, images.observedTargets.size)
            store.clear()
            runCurrent()
            assertEquals(0, images.activeObservers)
        }

    /** Creates one graph-tracked editor with deterministic time.
     * @param id Existing target or null for a local new form.
     * @param saved Primitive restoration input.
     */
    private fun model(
        id: String? = null,
        saved: SavedStateHandle = SavedStateHandle(),
    ): CampsiteSetupViewModel =
        CampsiteSetupViewModel("account-1", id, sites, images, clock, saved)
            .also { store.put("setup-${sequence++}", it) }

    /** Starts the new form's location step without introducing test-only navigation. */
    private fun newAtLocation(): CampsiteSetupViewModel =
        model().also {
            main.dispatcher.scheduler.runCurrent()
            it.editName("Camp")
            it.next()
            it.next()
        }

    /** Requires real editable state.
     * @param model Editor under test.
     */
    private fun content(model: CampsiteSetupViewModel): CampsiteSetupUiState.Content {
        assertTrue(
            "Expected editable content, got ${model.state.value}",
            model.state.value is CampsiteSetupUiState.Content,
        )
        return model.state.value as CampsiteSetupUiState.Content
    }

    /** Copies only the saved values, simulating a newly constructed process-owned model.
     * @param saved Previous small state.
     */
    private fun copy(saved: SavedStateHandle): SavedStateHandle =
        SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
}
