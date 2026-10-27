package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowValidator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Actual form callers of the stateless writer: guards, retained intent and read-only
 * restoration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LotterySetupPersistenceTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val site = sampleCampsite("A")
    private val sites = FakeCampsiteRepository(listOf(site))
    private val lotteries = FakeLotteryRepository()
    private val clock = MutableClock(Instant.parse("2027-05-01T12:00:00Z"))
    private val night = LocalDate.of(2027, 5, 14)
    private var allocatedIds = 0

    /** Cancels all controller jobs after each independent contract. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
    }

    /**
     * Invalid and reserved-validator paths allocate no identity and perform no lottery
     * operations.
     */
    @Test fun onlyValidReviewedInputCanAllocateAnIdentity() =
        runTest {
            val model = model(validator = null)
            model.saveDraft()
            model.publishLottery()
            review(model)
            model.saveDraft()
            model.publishLottery()
            model.retrySave()
            runCurrent()
            assertEquals(0, allocatedIds)
            assertTrue(lotteries.events.isEmpty())
            assertNull(model.state.value.draft.id)
            assertTrue(model.state.value.errors.windowsPending)
        }

    /**
     * The action locks synchronously, freezes its input and rejects duplicate or conflicting
     * actions.
     */
    @Test fun saveFreezesBeforeTheFirstSuspensionAndCompletesWithTheActualId() =
        runTest {
            configureWrites()
            val gate = CompletableDeferred<Unit>()
            lotteries.getHandler = {
                gate.await()
                null
            }
            val model = model()
            review(model)
            model.saveDraft()
            assertEquals("new-1", model.state.value.draft.id)
            assertEquals(LotterySavePhase.RECOVERING, model.state.value.savePhase)
            model.saveDraft()
            model.publishLottery()
            model.retrySave()
            model.editTitle("racing title")
            model.back()
            model.requestExit()
            model.confirmDiscard()
            runCurrent()
            assertEquals(1, allocatedIds)
            assertEquals(listOf("get:new-1"), lotteries.events)
            assertEquals("May weekends", model.state.value.draft.title)
            assertEquals(LotteryStep.REVIEW, model.state.value.draft.step)
            assertFalse(model.exitRequested.value)
            gate.complete(Unit)
            runCurrent()
            assertEquals("new-1", model.completion.value)
            assertEquals("May weekends", lotteries.detailsRequests.single().title)
            assertEquals(listOf(LotteryOffer(site.id, listOf(night))), lotteries.offerRequests)
            assertEquals(0, sites.activeObservers)
            assertEquals(0, sites.activeCalendarObservers)
        }

    /**
     * An explicit retry retains the requested Publish operation and the initial target identity.
     */
    @Test fun failedPrerequisiteRetainsPublishIntentAndRetriesTheSameId() =
        runTest {
            configureWrites()
            val successfulCreate = lotteries.createHandler!!
            lotteries.createHandler = { throw IOException("create acknowledgement") }
            val model = model()
            review(model)
            model.publishLottery()
            runCurrent()
            assertNull(model.completion.value)
            assertEquals(
                LotterySavePhase.SAVING_DETAILS,
                model.state.value.saveFailure
                    ?.stage,
            )
            assertEquals(LotterySaveAction.PUBLISH, model.state.value.saveAction)
            assertEquals("May weekends", model.state.value.draft.title)
            lotteries.createHandler = successfulCreate
            model.retrySave()
            model.retrySave()
            runCurrent()
            assertEquals(1, allocatedIds)
            assertEquals("new-1", model.completion.value)
            assertEquals(2, lotteries.events.count { it == "create:new-1" })
            assertEquals(1, lotteries.events.count { it == "publish:new-1" })
        }

    /** Opening equality rejects Publish without preventing a deliberate valid draft save. */
    @Test fun publicationChecksTheCurrentClockAtTheExactOpeningBoundary() =
        runTest {
            configureWrites()
            val model = model()
            review(model)
            val opening =
                model.state.value.configuration!!
                    .registrationOpensAt
            clock.advance(Duration.between(clock.instant(), opening))
            model.publishLottery()
            runCurrent()
            assertTrue(model.state.value.publicationTooLate)
            assertEquals(0, allocatedIds)
            assertTrue(lotteries.events.isEmpty())
            model.saveDraft()
            runCurrent()
            assertEquals("new-1", model.completion.value)
            assertFalse(lotteries.events.any { it.startsWith("publish:") })
        }

    /**
     * Unknown publication freezes conflicting actions and survives another failed recovery read.
     */
    @Test fun unknownPublicationCanOnlyBeReconciledUntilTheServerAnswers() =
        runTest {
            configureWrites()
            lotteries.publishHandler = { _, _ ->
                lotteries.getHandler = { throw IOException("unknown") }
                throw IOException("publish acknowledgement")
            }
            val model = model()
            review(model)
            model.publishLottery()
            runCurrent()
            assertTrue(model.state.value.publicationPending)
            val before = model.state.value.draft
            model.editDescription("racing edit")
            model.editStep(LotteryStep.BASICS)
            model.back()
            model.requestExit()
            model.confirmDiscard()
            assertEquals(before, model.state.value.draft)
            assertFalse(model.exitRequested.value)
            val writeCount = lotteries.events.count { it.startsWith("publish:") }
            model.retrySave()
            runCurrent()
            assertTrue(model.state.value.publicationPending)
            assertEquals(writeCount, lotteries.events.count { it.startsWith("publish:") })
            val saved = lotteries.records.value.single()
            lotteries.getHandler = { saved.copy(publishedAt = clock.instant()) }
            model.retrySave()
            runCurrent()
            assertEquals(saved.id, model.completion.value)
            assertEquals(writeCount, lotteries.events.count { it.startsWith("publish:") })
        }

    /**
     * Recreated publication recovery needs only identity, even when F13 and campsite reads are
     * absent.
     */
    @Test fun uncertainPublicationRestoresAndRecoversWithoutAValidatorOrChoices() =
        runTest {
            configureWrites()
            lotteries.publishHandler = { _, _ ->
                lotteries.getHandler = { throw IOException("offline") }
                throw IOException("lost acknowledgement")
            }
            val retained = SavedStateHandle()
            val first = model(retained)
            review(first)
            first.publishLottery()
            runCurrent()
            assertTrue(first.state.value.publicationPending)
            store.clear()
            runCurrent()
            sites.streamFailure.value = IOException("choices offline")
            val restored = model(retained, validator = null)
            runCurrent()
            assertTrue(restored.state.value.publicationPending)
            val events = lotteries.events.size
            lotteries.getHandler =
                {
                    lotteries.records.value
                        .single()
                        .copy(publishedAt = clock.instant())
                }
            restored.retrySave()
            runCurrent()
            assertEquals("new-1", restored.completion.value)
            assertEquals(listOf("get:new-1"), lotteries.events.drop(events))
            assertEquals(1, allocatedIds)
        }

    /**
     * A confirmed unpublished recovery releases uncertainty without bypassing a pending
     * validator.
     */
    @Test fun knownDraftAfterRecreationAllowsExitButCannotWriteWithPendingValidation() =
        runTest {
            configureWrites()
            lotteries.publishHandler = { _, _ ->
                lotteries.getHandler = { throw IOException("offline") }
                throw IOException("lost acknowledgement")
            }
            val retained = SavedStateHandle()
            val first = model(retained)
            review(first)
            first.publishLottery()
            runCurrent()
            store.clear()
            runCurrent()
            val restored = model(retained, validator = null)
            runCurrent()
            lotteries.getHandler = null
            val before = lotteries.events.size
            restored.retrySave()
            runCurrent()
            assertFalse(restored.state.value.publicationPending)
            restored.retrySave()
            runCurrent()
            assertEquals(listOf("get:new-1"), lotteries.events.drop(before))
            restored.requestExit()
            restored.confirmDiscard()
            assertTrue(restored.exitRequested.value)
        }

    /** An interrupted first create restores raw input and never writes until an explicit retry. */
    @Test fun interruptedInitialCreationRestoresItsSameStillMissingIdentity() =
        runTest {
            configureWrites()
            val gate = CompletableDeferred<Unit>()
            lotteries.getHandler = {
                gate.await()
                null
            }
            val retained = SavedStateHandle()
            val first = model(retained)
            review(first)
            first.saveDraft()
            runCurrent()
            store.clear()
            runCurrent()
            lotteries.getHandler = null
            val restored = model(retained)
            runCurrent()
            assertEquals("new-1", restored.state.value.draft.id)
            assertEquals("May weekends", restored.state.value.draft.title)
            assertEquals(LotteryTargetLoad.READY, restored.state.value.targetLoad)
            assertFalse(lotteries.events.any { it.startsWith("create:") })
            restored.retrySave()
            runCurrent()
            assertEquals("new-1", restored.completion.value)
            assertEquals(1, allocatedIds)
        }

    /**
     * Fresh saved input uses the allocated site's actual zone and confirmed offers without
     * writes.
     */
    @Test fun reopeningHydratesSavedInputInTheCampsiteZone() =
        runTest {
            val saved = savedLottery()
            lotteries.records.value = listOf(saved)
            lotteries.serverOffers[saved.id] = listOf(LotteryOffer(site.id, listOf(night)))
            val model = model(id = saved.id)
            runCurrent()
            val draft = model.state.value.draft
            assertEquals(LotteryTargetLoad.READY, model.state.value.targetLoad)
            assertEquals(saved.id, draft.id)
            assertEquals(saved.title, draft.title)
            assertEquals(setOf(night), draft.nights)
            assertEquals(LotteryStep.REVIEW, draft.step)
            val local =
                saved.configuration.registrationOpensAt.atZone(
                    ZoneId.of(site.details.timeZone),
                )
            assertEquals(
                LotteryLocalDateTime(local.toLocalDate(), local.toLocalTime()),
                draft.schedule[LotterySchedulePoint.REGISTRATION_OPEN],
            )
            assertEquals(site.details.timeZone, draft.scheduleZone)
            assertEquals(0, allocatedIds)
            assertEquals(listOf("get:saved", "offers:saved"), lotteries.events)
        }

    /** No-offer drafts retain their stored configuration until a real site can supply its zone. */
    @Test fun reopeningWithoutAnOfferConvertsTheScheduleAfterExplicitSiteChoice() =
        runTest {
            val saved = savedLottery()
            lotteries.records.value = listOf(saved)
            val model = model(id = saved.id)
            runCurrent()
            assertEquals(LotteryStep.SITE, model.state.value.draft.step)
            assertTrue(
                model.state.value.draft.schedule
                    .isEmpty(),
            )
            model.chooseCampsite(site.id)
            assertEquals(site.details.timeZone, model.state.value.draft.scheduleZone)
            val local = saved.configuration.bookingClosesAt.atZone(ZoneId.of(site.details.timeZone))
            assertEquals(
                LotteryLocalDateTime(local.toLocalDate(), local.toLocalTime()),
                model.state.value.draft.schedule[LotterySchedulePoint.BOOKING_CLOSE],
            )
            assertEquals(listOf("get:saved", "offers:saved"), lotteries.events)
        }

    /**
     * Recreated raw edits take precedence over server defaults, while the target is revalidated.
     */
    @Test fun restorationPreservesUnsavedEditsRatherThanOverwritingThemFromTheServer() =
        runTest {
            lotteries.records.value = listOf(savedLottery())
            lotteries.serverOffers["saved"] = listOf(LotteryOffer(site.id, listOf(night)))
            val retained = SavedStateHandle()
            val first = model(retained, id = "saved")
            runCurrent()
            first.editStep(LotteryStep.BASICS)
            first.editTitle("  My unsaved title  ")
            store.clear()
            runCurrent()
            val restored = model(retained, id = "saved")
            runCurrent()
            assertEquals("  My unsaved title  ", restored.state.value.draft.title)
            assertEquals(LotteryStep.BASICS, restored.state.value.draft.step)
            assertTrue(restored.state.value.draft.returnToReview)
            assertEquals(
                listOf("get:saved", "offers:saved", "get:saved", "offers:saved"),
                lotteries.events,
            )
        }

    /**
     * Missing existing targets never allocate or create; failed reads remain retryable and
     * escapable.
     */
    @Test fun missingAndFailedSavedTargetsNeverAuthorizeCreation() =
        runTest {
            val missing = model(id = "missing")
            runCurrent()
            assertEquals(LotteryTargetLoad.UNAVAILABLE, missing.state.value.targetLoad)
            missing.saveDraft()
            missing.retrySave()
            missing.back()
            assertTrue(missing.exitRequested.value)
            lotteries.getHandler = { throw IOException("offline") }
            val failed = model(id = "saved")
            runCurrent()
            assertEquals(LotteryTargetLoad.FAILURE, failed.state.value.targetLoad)
            lotteries.getHandler = { savedLottery() }
            failed.retryLoad()
            runCurrent()
            assertEquals(LotteryTargetLoad.READY, failed.state.value.targetLoad)
            assertEquals(0, allocatedIds)
            assertFalse(lotteries.events.any { it.startsWith("create:") })
        }

    /** A saved target from another owner is unavailable and cannot be edited or recreated. */
    @Test fun foreignSavedTargetIsUnavailable() =
        runTest {
            lotteries.records.value = listOf(savedLottery().copy(ownerId = "another-owner"))
            val model = model(id = "saved")
            runCurrent()
            assertEquals(LotteryTargetLoad.UNAVAILABLE, model.state.value.targetLoad)
            model.editTitle("bad edit")
            model.saveDraft()
            assertEquals(listOf("get:saved"), lotteries.events)
            model.back()
            assertTrue(model.exitRequested.value)
        }

    /**
     * A saved completion survives recreation and late source failures without repeating any
     * operation.
     */
    @Test fun confirmedCompletionSurvivesRecreationAndRetiresSourceListeners() =
        runTest {
            configureWrites()
            val retained = SavedStateHandle()
            val first = model(retained)
            review(first)
            first.saveDraft()
            runCurrent()
            assertEquals("new-1", first.completion.value)
            val calls = lotteries.events.toList()
            sites.streamFailure.value = IOException("later failure")
            runCurrent()
            store.clear()
            val restored = model(retained)
            runCurrent()
            assertEquals("new-1", restored.completion.value)
            restored.retrySave()
            restored.retryLoad()
            restored.saveDraft()
            runCurrent()
            assertEquals(calls, lotteries.events)
            assertEquals(0, sites.activeObservers)
        }

    /** Clearing the graph rejects late noncooperative reads before any mutation or completion. */
    @Test fun scopeCancellationCannotResumeTheSaveAfterALateRead() =
        runTest {
            configureWrites()
            val gate = CompletableDeferred<Unit>()
            lotteries.getHandler = {
                withContext(NonCancellable) { gate.await() }
                null
            }
            val model = model()
            review(model)
            model.saveDraft()
            runCurrent()
            store.clear()
            gate.complete(Unit)
            runCurrent()
            assertNull(model.completion.value)
            assertEquals(listOf("get:new-1"), lotteries.events)
        }

    /**
     * Already-published incoming targets complete directly without hydrating editable
     * allocations.
     */
    @Test fun publishedIncomingTargetOpensItsActualSavedIdentity() =
        runTest {
            lotteries.records.value = listOf(savedLottery().copy(publishedAt = clock.instant()))
            val model = model(id = "saved")
            runCurrent()
            assertEquals("saved", model.completion.value)
            assertEquals(listOf("get:saved"), lotteries.events)
            assertEquals(0, allocatedIds)
            assertEquals(0, sites.activeObservers)
        }

    /** A consumed handoff remains terminal if its removed graph's state is later reconstructed. */
    @Test fun acknowledgedCompletionDoesNotReemitOrRestartReads() =
        runTest {
            configureWrites()
            val retained = SavedStateHandle()
            val first = model(retained)
            review(first)
            first.saveDraft()
            runCurrent()
            first.acknowledgeCompletion()
            assertNull(first.completion.value)
            val calls = lotteries.events.toList()
            store.clear()
            runCurrent()
            val restored = model(retained)
            runCurrent()
            assertNull(restored.completion.value)
            restored.saveDraft()
            restored.retryLoad()
            restored.retrySave()
            runCurrent()
            assertEquals(calls, lotteries.events)
            assertEquals(0, sites.activeObservers)
        }

    /**
     * A multiple-site draft needs an explicit site/allocation choice before one-site
     * reconciliation.
     */
    @Test fun multipleSavedOffersDoNotChooseAndDeleteAnAllocationImplicitly() =
        runTest {
            lotteries.records.value = listOf(savedLottery())
            lotteries.serverOffers["saved"] =
                listOf(
                    LotteryOffer(site.id, listOf(night)),
                    LotteryOffer("another-site", listOf(night.plusDays(1))),
                )
            val model = model(id = "saved")
            runCurrent()
            assertEquals(LotteryStep.SITE, model.state.value.draft.step)
            assertNull(model.state.value.draft.campsiteId)
            assertTrue(
                model.state.value.draft.nights
                    .isEmpty(),
            )
            model.saveDraft()
            model.publishLottery()
            assertEquals(listOf("get:saved", "offers:saved"), lotteries.events)
        }

    /**
     * Supplies explicit mutation acknowledgements; production behavior remains in the real
     * writer.
     */
    private fun configureWrites() {
        lotteries.createHandler = { input ->
            Lottery(
                input.id,
                input.ownerId,
                input.title,
                input.description,
                input.configuration,
                0,
                clock.instant(),
            ).also { lotteries.records.value = listOf(it) }
        }
        lotteries.updateHandler = { input ->
            lotteries.records.value =
                lotteries.records.value.map {
                    if (it.id ==
                        input.id
                    ) {
                        it.copy(
                            title = input.title,
                            description = input.description,
                            configuration = input.configuration,
                        )
                    } else {
                        it
                    }
                }
        }
        lotteries.setOfferHandler = { id, _, offer -> lotteries.serverOffers[id] = listOf(offer) }
        lotteries.removeOfferHandler = { id, _, campsiteId ->
            lotteries.serverOffers[id] =
                lotteries.serverOffers[id].orEmpty().filterNot { it.campsiteId == campsiteId }
        }
        lotteries.publishHandler = { id, _ ->
            lotteries.records.value =
                lotteries.records.value.map {
                    if (it.id ==
                        id
                    ) {
                        it.copy(publishedAt = clock.instant())
                    } else {
                        it
                    }
                }
        }
    }

    /** Uses real local actions rather than installing a prevalidated configuration.
     * @param model Actual form controller under test.
     */
    private fun review(model: LotterySetupViewModel) {
        main.dispatcher.scheduler.runCurrent()
        model.editTitle("May weekends")
        model.next()
        model.chooseCampsite(site.id)
        sites.calendarNights.value = listOf(CalendarNight(night, true))
        assertTrue(model.beginAllocation())
        main.dispatcher.scheduler.runCurrent()
        model.toggleAllocationDate(night)
        assertTrue(model.applyAllocation())
        model.next()
        LotterySchedulePoint.entries.forEachIndexed { index, point ->
            model.editScheduleDate(point, LocalDate.of(2027, 5, 5 + index))
            model.editScheduleTime(point, LocalTime.of(9, 0))
        }
        model.next()
        model.next()
        model.editWinnerCount("2")
        model.next()
        assertEquals(LotteryStep.REVIEW, model.state.value.draft.step)
    }

    /** Builds a minute-aligned existing record for native local schedule inputs. */
    private fun savedLottery(): Lottery =
        CampingFixtures.lottery("saved", "account-1").let {
            it.copy(
                publishedAt = null,
                configuration =
                    it.configuration.copy(
                        registrationOpensAt = Instant.parse("2027-05-05T15:00:00Z"),
                        registrationClosesAt = Instant.parse("2027-05-06T15:00:00Z"),
                        bookingOpensAt = Instant.parse("2027-05-07T15:00:00Z"),
                        bookingClosesAt = Instant.parse("2027-05-08T15:00:00Z"),
                    ),
            )
        }

    /** Creates one owned graph with injected deterministic identity and optional F13.
     * @param saved Small primitive form/operation state.
     * @param validator Fixed contract result, or null to keep the human exercise pending.
     * @param id Actual incoming saved target, absent for local creation.
     */
    private fun model(
        saved: SavedStateHandle = SavedStateHandle(),
        validator: LotteryWindowValidator? = { _, _, _, _ -> LotteryWindowInput.Valid },
        id: String? = null,
    ): LotterySetupViewModel =
        LotterySetupViewModel(
            "account-1",
            sites,
            lotteries,
            clock,
            saved,
            validator,
            id,
            {
                allocatedIds++
                "new-$allocatedIds"
            },
        ).also { store.put("lottery", it) }
}
