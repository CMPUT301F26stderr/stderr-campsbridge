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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Explicit publication contracts, including ordered prerequisites and authoritative recovery. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsitePublicationTest {
    @get:Rule val main = MainDispatcherRule()
    private val site = sampleCampsite(published = false)
    private val sites = FakeCampsiteRepository(listOf(site))
    private val images = FakeImageRepository()
    private val clock = Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC)
    private val store = ViewModelStore()
    private var models = 0

    /** Releases each real graph-owned reader and pending operation after a case. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, sites.activeCalendarObservers)
        assertEquals(0, images.activeObservers)
    }

    /** A valid listing may publish with no image or offered nights; no extra requirements apply. */
    @Test fun validEmptyInventoryPublishesAndWaitsForAcknowledgement() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            sites.publishHandler = { id, owner ->
                assertEquals(site.id, id)
                assertEquals(site.ownerId, owner)
                gate.await()
            }
            val model = model()
            runCurrent()
            review(model)
            model.publish()
            runCurrent()
            assertEquals(CampsiteSaveAction.PUBLISH, content(model).saveAction)
            assertEquals(CampsiteSavePhase.PUBLISHING, content(model).savePhase)
            assertTrue(content(model).publicationPending)
            model.publish()
            model.saveDraft()
            model.requestExit()
            model.back()
            assertEquals(1, sites.publishRequests.size)
            assertNull(model.completion.value)
            assertEquals(CampsiteStep.REVIEW, content(model).draft.step)
            assertFalse(model.exitRequested.value)
            gate.complete(Unit)
            runCurrent()
            assertEquals(site.id, model.completion.value)
            assertFalse(content(model).publicationPending)
        }

    /** Publication uses the retained new ID and follows details and every acknowledged requested night. */
    @Test fun newListingPublishesOnlyAfterAllRequestedStages() =
        runTest {
            val order = mutableListOf<String>()
            val date = LocalDate.of(2027, 5, 14)
            sites.createHandler = { id, owner, details ->
                order.add("create")
                site
                    .copy(
                        id = id,
                        ownerId = owner,
                        details = details,
                    ).also { sites.records.value += it }
            }
            sites.offerHandler = { id, _, _, _ ->
                assertEquals("new-site", id)
                order.add("night")
            }
            sites.publishHandler = { id, _ ->
                assertEquals("new-site", id)
                order.add("publish")
            }
            val model = model(id = null, difference = { requested, _ -> requested })
            runCurrent()
            model.editName("River Camp")
            model.selectLocation(site.details.location)
            model.editTimeZone(site.details.timeZone)
            model.editCapacity("8")
            model.editPrice("65.00")
            review(model)
            model.editStep(CampsiteStep.AVAILABILITY)
            model.toggleDate(date)
            model.next()
            model.publish()
            runCurrent()
            assertEquals(listOf("create", "night", "publish"), order)
            assertEquals("new-site", model.completion.value)
        }

    /** Invalid input and unimplemented date comparison cannot perform any publication or draft write. */
    @Test fun invalidOrPendingPrerequisitesNeverPublish() =
        runTest {
            val model = model()
            runCurrent()
            model.publish()
            review(model)
            model.editName("  ")
            model.publish()
            runCurrent()
            assertTrue(content(model).errors.nameRequired)
            model.editName(site.details.name)
            model.editStep(CampsiteStep.AVAILABILITY)
            model.toggleDate(LocalDate.of(2027, 5, 14))
            model.next()
            model.publish()
            runCurrent()
            assertEquals(CampsiteSaveFailure.AVAILABILITY_PENDING, content(model).saveFailure)
            assertEquals(0, sites.writeCalls)
            assertNull(model.completion.value)
        }

    /** A failed requested night prevents publication while retaining that night's explicit retry. */
    @Test fun failedInventoryStopsBeforePublication() =
        runTest {
            sites.offerHandler = { _, _, _, _ -> throw IOException("night write failed") }
            val model = model(difference = { requested, _ -> requested })
            runCurrent()
            review(model)
            model.editStep(CampsiteStep.AVAILABILITY)
            model.toggleDate(LocalDate.of(2027, 5, 14))
            model.next()
            model.publish()
            runCurrent()
            assertEquals(CampsiteSaveFailure.AVAILABILITY, content(model).saveFailure)
            assertTrue(sites.publishRequests.isEmpty())
            assertEquals(CampsiteSaveAction.PUBLISH, content(model).saveAction)
            sites.offerHandler = { _, _, _, _ -> }
            sites.publishHandler = { _, _ -> }
            model.retrySave()
            runCurrent()
            assertEquals(1, sites.publishRequests.size)
            assertEquals(site.id, model.completion.value)
        }

    /** A lost acknowledgement is confirmed by server get without another publication attempt. */
    @Test fun committedPublicationWithLostAcknowledgementCompletesFromServer() =
        runTest {
            sites.publishHandler = { _, _ ->
                sites.records.value = listOf(site.copy(publishedAt = clock.instant()))
                throw IOException("acknowledgement lost")
            }
            val model = model()
            runCurrent()
            review(model)
            model.publish()
            runCurrent()
            assertEquals(1, sites.publishRequests.size)
            assertTrue(sites.readIds.size >= 3)
            assertEquals(site.id, model.completion.value)
        }

    /** Confirmed unpublished failure leaves editable input and retries the same requested outcome. */
    @Test fun confirmedDraftAfterPublicationFailureOffersExplicitRetry() =
        runTest {
            sites.publishHandler = { _, _ -> throw IOException("not committed") }
            val model = model()
            runCurrent()
            review(model)
            model.publish()
            runCurrent()
            assertEquals(CampsiteSaveFailure.PUBLICATION, content(model).saveFailure)
            assertFalse(content(model).publicationPending)
            assertNull(model.completion.value)
            sites.publishHandler = { _, _ -> }
            model.retrySave()
            runCurrent()
            assertEquals(2, sites.publishRequests.size)
            assertEquals(site.id, model.completion.value)
        }

    /** A failed reconciliation read cannot authorize editing, leaving or a replacement draft action. */
    @Test fun unknownPublicationBlocksChangesUntilAnExplicitRecovery() =
        runTest {
            sites.publishHandler = { _, _ ->
                sites.readFailure = IOException("server read failed")
                throw IOException("unknown publication")
            }
            val model = model()
            runCurrent()
            review(model)
            model.publish()
            runCurrent()
            assertTrue(content(model).publicationPending)
            assertEquals(CampsiteSaveFailure.RECOVERY, content(model).saveFailure)
            model.editName("Must not apply")
            model.editStep(CampsiteStep.BASICS)
            model.requestExit()
            model.saveDraft()
            assertEquals(site.details.name, content(model).draft.name)
            assertEquals(CampsiteStep.REVIEW, content(model).draft.step)
            assertFalse(content(model).discardPrompt)
            sites.readFailure = null
            sites.records.value = listOf(site.copy(publishedAt = clock.instant()))
            model.retrySave()
            runCurrent()
            assertEquals(1, sites.publishRequests.size)
            assertEquals(site.id, model.completion.value)
        }

    /** Unknown publication markers survive reconstruction without automatically replaying a write. */
    @Test fun restoredUnknownPublicationRetainsRequestedActionAndGuard() =
        runTest {
            val saved = SavedStateHandle()
            sites.publishHandler = { _, _ ->
                sites.readFailure = IOException("read failed")
                throw IOException("publish unknown")
            }
            val first = model(saved = saved)
            runCurrent()
            review(first)
            first.publish()
            runCurrent()
            val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            store.clear()
            sites.readFailure = null
            val restored = model(saved = snapshot)
            runCurrent()
            assertEquals(1, sites.publishRequests.size)
            assertEquals(CampsiteSaveAction.PUBLISH, content(restored).saveAction)
            assertTrue(content(restored).publicationPending)
            restored.requestExit()
            assertFalse(content(restored).discardPrompt)
            sites.publishHandler = { _, _ -> }
            restored.retrySave()
            runCurrent()
            assertEquals(2, sites.publishRequests.size)
            assertEquals(site.id, restored.completion.value)
        }

    /** Failed restored reads cannot expose ordinary discard navigation during publication uncertainty. */
    @Test fun failedRestoredPublicationReadCannotAbandonTheTarget() =
        runTest {
            val saved = SavedStateHandle()
            sites.publishHandler = { _, _ ->
                sites.readFailure = IOException("read failed")
                throw IOException("publish unknown")
            }
            val first = model(saved = saved)
            runCurrent()
            review(first)
            first.publish()
            runCurrent()
            val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            store.clear()
            val restored = model(saved = snapshot)
            runCurrent()
            restored.requestExit()
            restored.confirmDiscard()
            assertFalse(restored.exitRequested.value)
            assertFalse(restored.state.value is CampsiteSetupUiState.ConfirmDiscard)
            assertEquals(1, sites.publishRequests.size)
            sites.readFailure = null
            sites.records.value = listOf(site.copy(publishedAt = clock.instant()))
            restored.retry()
            runCurrent()
            assertEquals(site.id, restored.completion.value)
        }

    /** A server-confirmed missing target is unavailable rather than falsely complete or forever unknown. */
    @Test fun confirmedMissingPublicationTargetCanLeaveWithoutCreatingAnother() =
        runTest {
            sites.publishHandler = { _, _ ->
                sites.records.value = emptyList()
                throw IOException("target disappeared")
            }
            val model = model()
            runCurrent()
            review(model)
            model.publish()
            runCurrent()
            assertEquals(CampsiteSetupUiState.Unavailable, model.state.value)
            assertNull(model.completion.value)
            assertTrue(sites.createRequests.isEmpty())
            model.requestExit()
            assertTrue(model.exitRequested.value)
        }

    /** A restored unknown attempt releases its guard after a server-confirmed target removal. */
    @Test fun restoredMissingPublicationTargetIsUnavailableAndCanLeave() =
        runTest {
            sites.records.value = emptyList()
            val saved =
                SavedStateHandle(
                    mapOf(
                        "campsiteSetup.publishing" to true,
                        "campsiteSetup.action" to CampsiteSaveAction.PUBLISH.name,
                        "campsiteSetup.attempt" to true,
                    ),
                )
            val model = model(saved = saved)
            runCurrent()
            assertEquals(CampsiteSetupUiState.Unavailable, model.state.value)
            model.requestExit()
            assertTrue(model.exitRequested.value)
            assertEquals(0, sites.writeCalls)
        }

    /** A blocked Publish must retain that outcome when the user fixes a restored photo choice. */
    @Test fun blockedPublicationRetainsActionAfterDiscardingRestoredPhoto() =
        runTest {
            val saved = SavedStateHandle(mapOf("campsiteSetup.photo" to "replace"))
            sites.publishHandler = { _, _ -> }
            val model = model(saved = saved)
            runCurrent()
            review(model)
            model.publish()
            assertEquals(CampsiteSaveFailure.PHOTO_RESELECTION, content(model).saveFailure)
            assertEquals(CampsiteSaveAction.PUBLISH, content(model).saveAction)
            model.editStep(CampsiteStep.PHOTO)
            model.discardPhoto()
            model.next()
            model.retrySave()
            runCurrent()
            assertEquals(1, sites.publishRequests.size)
            assertEquals(site.id, model.completion.value)
        }

    /** Explicit retry must not recreate a target that the server confirms disappeared. */
    @Test fun retryOfUnknownPublicationAcceptsConfirmedUnavailability() =
        runTest {
            sites.publishHandler = { _, _ ->
                sites.readFailure = IOException("read failed")
                throw IOException("unknown publish")
            }
            val model = model()
            runCurrent()
            review(model)
            model.publish()
            runCurrent()
            sites.readFailure = null
            sites.records.value = emptyList()
            model.retrySave()
            runCurrent()
            assertEquals(CampsiteSetupUiState.Unavailable, model.state.value)
            assertNull(model.completion.value)
            assertTrue(sites.createRequests.isEmpty())
            assertEquals(1, sites.publishRequests.size)
            model.requestExit()
            assertTrue(model.exitRequested.value)
        }

    /** Builds one saved-state owner with a deterministic optional new ID and comparison fixture.
     * @param id Actual existing target, or null for a local new form.
     * @param saved Primitive graph restoration state.
     * @param difference Expected date-comparison result for orchestration cases.
     */
    private fun model(
        id: String? = site.id,
        saved: SavedStateHandle = SavedStateHandle(),
        difference: AvailabilityDifference? = null,
    ): CampsiteSetupViewModel =
        CampsiteSetupViewModel(
            site.ownerId,
            id,
            sites,
            images,
            clock,
            saved,
            newId = { "new-site" },
            remainingAvailabilityEdits = difference,
        ).also { store.put("publication-${models++}", it) }

    /** Walks the real eight-step advancement with the supplied valid existing/new fields.
     * @param model Actual setup controller.
     */
    private fun review(model: CampsiteSetupViewModel) {
        repeat(7) { model.next() }
        assertEquals(CampsiteStep.REVIEW, content(model).draft.step)
    }

    /** Requires current draft content without treating failure or completion as editable input.
     * @param model Actual controller under test.
     */
    private fun content(model: CampsiteSetupViewModel): CampsiteSetupUiState.Content =
        model.state.value as CampsiteSetupUiState.Content
}
