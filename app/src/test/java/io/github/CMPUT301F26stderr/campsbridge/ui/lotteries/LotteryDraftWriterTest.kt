package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Save-stage contracts use controlled outcomes; real transaction invariants stay in Firebase. */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryDraftWriterTest {
    private val draft = CampingFixtures.lottery("draft", "owner").copy(publishedAt = null)
    private val offer = LotteryOffer("chosen", listOf(CampingFixtures.arrival))
    private val repository = FakeLotteryRepository()
    private val writer = LotteryDraftWriter(repository)

    /** Supplies explicit acknowledged mutations; individual cases replace only their failure. */
    @Before fun configureOutcomes() {
        repository.createHandler = { input ->
            draft
                .copy(
                    id = input.id,
                    ownerId = input.ownerId,
                    title = input.title,
                    description = input.description,
                    configuration = input.configuration,
                ).also { repository.records.value = listOf(it) }
        }
        repository.updateHandler = { input ->
            repository.records.value =
                repository.records.value.map {
                    if (it.id == input.id) {
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
        repository.setOfferHandler = { id, _, next ->
            repository.serverOffers[id] =
                repository.serverOffers[id].orEmpty().filterNot {
                    it.campsiteId == next.campsiteId
                } +
                next
        }
        repository.removeOfferHandler = { id, _, site ->
            repository.serverOffers[id] =
                repository.serverOffers[id].orEmpty().filterNot { it.campsiteId == site }
        }
        repository.publishHandler = { id, _ ->
            repository.records.value =
                repository.records.value.map {
                    if (it.id == id) it.copy(publishedAt = CampingFixtures.now) else it
                }
        }
    }

    /** The parent is confirmed before an allocation; saving a draft never invokes publication. */
    @Test fun draftSaveCreatesBeforeOfferAndRetainsExactInput() =
        runTest {
            val phases = mutableListOf<LotterySavePhase>()
            val request = request().copy(title = "May camping 🌲", description = "  Quiet stays  ")
            assertEquals(LotterySaveResult.Saved(draft.id), writer.save(request) { phases.add(it) })
            assertEquals(
                listOf("get:draft", "create:draft", "offers:draft", "set:chosen"),
                repository.events,
            )
            assertEquals(
                listOf(
                    LotterySavePhase.RECOVERING,
                    LotterySavePhase.SAVING_DETAILS,
                    LotterySavePhase.SAVING_OFFER,
                ),
                phases,
            )
            val actual = repository.detailsRequests.single()
            assertEquals(request.title, actual.title)
            assertEquals(request.description, actual.description)
            assertEquals(request.configuration, actual.configuration)
            assertEquals(null, actual.configuration.waitlistCapacity)
            assertEquals(listOf(offer), repository.offerRequests)
        }

    /** Every old site is removed before the chosen allocation and final publication. */
    @Test fun oneSitePublicationReconcilesEveryServerOffer() =
        runTest {
            repository.records.value = listOf(draft)
            repository.serverOffers[draft.id] =
                listOf(
                    LotteryOffer("z-old", offer.nights),
                    offer.copy(nights = listOf(CampingFixtures.arrival.plusDays(1))),
                    LotteryOffer("a-old", offer.nights),
                )
            val phases = mutableListOf<LotterySavePhase>()
            val result = writer.save(request(LotterySaveAction.PUBLISH)) { phases.add(it) }
            assertEquals(LotterySaveResult.Saved(draft.id), result)
            assertEquals(
                listOf(
                    "get:draft",
                    "offers:draft",
                    "remove:a-old",
                    "remove:z-old",
                    "set:chosen",
                    "publish:draft",
                ),
                repository.events,
            )
            assertEquals(listOf(offer), repository.serverOffers[draft.id])
            assertEquals(LotterySavePhase.PUBLISHING, phases.last())
        }

    /** Acknowledged details and allocation are not replayed on an identical explicit retry. */
    @Test fun retrySkipsConfirmedStages() =
        runTest {
            assertEquals(LotterySaveResult.Saved(draft.id), writer.save(request()))
            repository.events.clear()
            assertEquals(LotterySaveResult.Saved(draft.id), writer.save(request()))
            assertEquals(listOf("get:draft", "offers:draft"), repository.events)
            assertEquals(1, repository.detailsRequests.size)
            assertEquals(1, repository.offerRequests.size)
        }

    /** New edits update only dirty details while preserving a confirmed matching allocation. */
    @Test fun changedDetailsDoNotRepeatTheMatchingOffer() =
        runTest {
            repository.records.value = listOf(draft)
            repository.serverOffers[draft.id] = listOf(offer)
            val request = request().copy(title = "Renamed draft")
            assertEquals(LotterySaveResult.Saved(draft.id), writer.save(request))
            assertEquals(listOf("get:draft", "update:draft", "offers:draft"), repository.events)
            assertEquals(
                request.title,
                repository.records.value
                    .single()
                    .title,
            )
        }

    /** An operation cannot complete while its allocation acknowledgement is held. */
    @Test fun heldAllocationRetainsItsStageUntilAcknowledged() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.setOfferHandler = { _, _, _ -> gate.await() }
            val phases = mutableListOf<LotterySavePhase>()
            val operation = async { writer.save(request()) { phases.add(it) } }
            runCurrent()
            assertFalse(operation.isCompleted)
            assertEquals(LotterySavePhase.SAVING_OFFER, phases.last())
            assertEquals(listOf(offer), repository.offerRequests)
            gate.complete(Unit)
            assertEquals(LotterySaveResult.Saved(draft.id), operation.await())
        }

    /** Lost create acknowledgement reconciles the same record rather than creating another ID. */
    @Test fun uncertainCreationReadsTheSameIdentityOnRetry() =
        runTest {
            val acknowledged = checkNotNull(repository.createHandler)
            repository.createHandler = { input ->
                acknowledged(input)
                throw IOException("create acknowledgement lost")
            }
            assertEquals(
                LotterySaveResult.Failed(LotterySavePhase.SAVING_DETAILS),
                writer.save(request()),
            )
            assertEquals(listOf("get:draft", "create:draft"), repository.events)
            assertEquals(LotterySaveResult.Saved(draft.id), writer.save(request()))
            assertEquals(1, repository.events.count { it.startsWith("create:") })
            assertEquals(1, repository.detailsRequests.size)
        }

    /** Stale empty live offers cannot bypass a failed server reconciliation after removal. */
    @Test fun uncertainRemovalAndStaleEmptyEmissionCannotPermitPublication() =
        runTest {
            repository.records.value = listOf(draft)
            repository.serverOffers[draft.id] = listOf(LotteryOffer("old", offer.nights))
            repository.liveOffers.value = mapOf(draft.id to emptyList())
            val remove = checkNotNull(repository.removeOfferHandler)
            repository.removeOfferHandler = { id, owner, site ->
                remove(id, owner, site)
                throw IOException("remove acknowledgement lost")
            }
            val request = request(LotterySaveAction.PUBLISH)
            assertEquals(
                LotterySaveResult.Failed(LotterySavePhase.SAVING_OFFER),
                writer.save(request),
            )
            repository.getOffersHandler = { throw IOException("server unavailable") }
            assertEquals(
                LotterySaveResult.Failed(LotterySavePhase.SAVING_OFFER),
                writer.save(request),
            )
            assertFalse(
                repository.events.any { it.startsWith("publish:") || it.startsWith("set:") },
            )
            repository.getOffersHandler = null
            assertEquals(LotterySaveResult.Saved(draft.id), writer.save(request))
            assertEquals(1, repository.events.count { it == "remove:old" })
            assertEquals(1, repository.events.count { it == "set:chosen" })
            assertEquals(1, repository.events.count { it == "publish:draft" })
            assertEquals(3, repository.events.count { it == "offers:draft" })
        }

    /** A server-confirmed allocation survives lost acknowledgement without a second set. */
    @Test fun uncertainOfferSaveDoesNotRepeatTheConfirmedAllocation() =
        runTest {
            val save = checkNotNull(repository.setOfferHandler)
            repository.setOfferHandler = { id, owner, next ->
                save(id, owner, next)
                throw IOException("offer acknowledgement lost")
            }
            val request = request(LotterySaveAction.PUBLISH)
            assertEquals(
                LotterySaveResult.Failed(LotterySavePhase.SAVING_OFFER),
                writer.save(request),
            )
            assertEquals(LotterySaveResult.Saved(draft.id), writer.save(request))
            assertEquals(1, repository.offerRequests.size)
            assertEquals(1, repository.events.count { it == "publish:draft" })
        }

    /** Successful publication with a lost acknowledgement completes only after its server read. */
    @Test fun publicationAcknowledgementFailureReconcilesConfirmedSuccess() =
        runTest {
            val publish = checkNotNull(repository.publishHandler)
            repository.publishHandler = { id, owner ->
                publish(id, owner)
                throw IOException("publish acknowledgement lost")
            }
            val phases = mutableListOf<LotterySavePhase>()
            assertEquals(
                LotterySaveResult.Saved(draft.id),
                writer.save(request(LotterySaveAction.PUBLISH)) { phases.add(it) },
            )
            assertEquals(LotterySavePhase.RECOVERING, phases.last())
            assertEquals("get:draft", repository.events.last())
            assertEquals(1, repository.events.count { it == "publish:draft" })
        }

    /** A known draft permits publication retry without replaying its acknowledged saves. */
    @Test fun knownDraftPublicationFailureCanRetry() =
        runTest {
            repository.publishHandler = { _, _ -> throw IOException("publish rejected") }
            val request = request(LotterySaveAction.PUBLISH)
            assertEquals(
                LotterySaveResult.Failed(LotterySavePhase.PUBLISHING),
                writer.save(request),
            )
            assertEquals(1, repository.events.count { it.startsWith("publish:") })
            repository.publishHandler = { id, _ ->
                repository.records.value =
                    repository.records.value.map {
                        if (it.id == id) it.copy(publishedAt = CampingFixtures.now) else it
                    }
            }
            assertEquals(LotterySaveResult.Saved(draft.id), writer.save(request))
            assertEquals(1, repository.detailsRequests.size)
            assertEquals(1, repository.offerRequests.size)
            assertEquals(2, repository.events.count { it.startsWith("publish:") })
        }

    /** An unknown publication outcome remains explicit until a later server read resolves it. */
    @Test fun failedPublicationReadRetainsUncertaintyWithoutAnotherMutation() =
        runTest {
            var attempted = false
            repository.getHandler = {
                if (attempted) throw IOException("read unavailable")
                repository.records.value.firstOrNull()
            }
            repository.publishHandler = { _, _ ->
                attempted = true
                throw IOException("acknowledgement lost")
            }
            val request = request(LotterySaveAction.PUBLISH)
            assertEquals(
                LotterySaveResult.Failed(LotterySavePhase.RECOVERING, publicationUncertain = true),
                writer.save(request),
            )
            repository.getHandler = null
            repository.records.value = listOf(draft.copy(publishedAt = CampingFixtures.now))
            val before = repository.events.toList()
            assertEquals(LotterySaveResult.Published(draft.id), writer.save(request))
            assertEquals(before + "get:draft", repository.events)
        }

    /** Existing target loss, cancellation or ownership mismatch never creates a replacement. */
    @Test fun unavailableTargetsCannotBeRecreatedOrMutated() =
        runTest {
            val request = request().copy(allowCreate = false)
            val candidates =
                listOf(
                    emptyList(),
                    listOf(draft.copy(ownerId = "another-owner")),
                    listOf(
                        draft.copy(cancellation = Cancellation(CampingFixtures.now, "owner", null)),
                    ),
                    listOf(draft.copy(id = "wrong-target")),
                )
            for (rows in candidates) {
                repository.getHandler = { rows.firstOrNull() }
                assertEquals(LotterySaveResult.Unavailable, writer.save(request))
                assertEquals(
                    if (rows.isEmpty()) LotteryDraftRead.Missing else LotteryDraftRead.Unavailable,
                    writer.read(draft.id, draft.ownerId),
                )
            }
            assertTrue(repository.events.all { it == "get:draft" })
        }

    /** Read-only restoration distinguishes complete, empty and failed allocation sources. */
    @Test fun restorationUsesEveryServerOfferAndNeverWrites() =
        runTest {
            repository.records.value = listOf(draft)
            val offers = listOf(offer, LotteryOffer("old", offer.nights))
            repository.serverOffers[draft.id] = offers
            assertEquals(
                LotteryDraftRead.Content(draft, offers),
                writer.read(draft.id, draft.ownerId),
            )
            repository.serverOffers[draft.id] = emptyList()
            assertEquals(
                LotteryDraftRead.Content(draft, emptyList()),
                writer.read(draft.id, draft.ownerId),
            )
            repository.getOffersHandler = { throw IOException("offers unavailable") }
            assertEquals(LotteryDraftRead.Failed, writer.read(draft.id, draft.ownerId))
            assertTrue(repository.events.all { it.startsWith("get:") || it.startsWith("offers:") })
        }

    /** A published record opens the saved reader without waiting for editable-offer hydration. */
    @Test fun restorationOfPublishedTargetDoesNotOpenAnEditor() =
        runTest {
            repository.records.value = listOf(draft.copy(publishedAt = CampingFixtures.now))
            repository.getOffersHandler =
                { error("Published target must not hydrate an editable draft") }
            assertEquals(LotteryDraftRead.Published(draft.id), writer.read(draft.id, draft.ownerId))
            assertEquals(listOf("get:draft"), repository.events)
        }

    /** Cancellation escapes reads, writes and publication reconciliation without compensation. */
    @Test fun cancellationIsNeverConvertedToSaveFeedback() =
        runTest {
            val cancelled = CancellationException("graph closed")
            repository.getHandler = { throw cancelled }
            assertSame(
                cancelled,
                runCatching { writer.read(draft.id, draft.ownerId) }.exceptionOrNull(),
            )
            assertSame(cancelled, runCatching { writer.save(request()) }.exceptionOrNull())
            repository.getHandler = null
            repository.publishHandler = { _, _ -> throw cancelled }
            assertSame(
                cancelled,
                runCatching { writer.save(request(LotterySaveAction.PUBLISH)) }.exceptionOrNull(),
            )
            assertEquals(3, repository.events.count { it == "get:draft" })
        }

    /** An initial failed server lookup is unknown, so it cannot authorize draft creation. */
    @Test fun failedParentReadCannotBecomeAnAbsentDraft() =
        runTest {
            repository.getHandler = { throw IOException("record unavailable") }
            assertEquals(LotteryDraftRead.Failed, writer.read(draft.id, draft.ownerId))
            assertEquals(
                LotterySaveResult.Failed(LotterySavePhase.RECOVERING),
                writer.save(request()),
            )
            assertEquals(listOf("get:draft", "get:draft"), repository.events)
        }

    /** A late noncooperative read cannot start writes after its owning operation is cancelled. */
    @Test fun cancelledReadCannotStartCreationAfterItsLateReturn() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.getHandler = {
                withContext(NonCancellable) { gate.await() }
                null
            }
            val operation = async { writer.save(request()) }
            runCurrent()
            operation.cancel()
            gate.complete(Unit)
            operation.join()
            assertTrue(operation.isCancelled)
            assertEquals(listOf("get:draft"), repository.events)
        }

    /** Confirmed target loss releases publication uncertainty without creating a replacement. */
    @Test fun publicationRecoveryCanConfirmAnUnavailableTarget() =
        runTest {
            repository.publishHandler = { _, _ ->
                repository.records.value = emptyList()
                throw IOException("acknowledgement lost")
            }
            assertEquals(
                LotterySaveResult.Unavailable,
                writer.save(request(LotterySaveAction.PUBLISH)),
            )
            assertEquals(1, repository.events.count { it == "create:draft" })
            assertEquals(1, repository.events.count { it == "publish:draft" })
            assertEquals("get:draft", repository.events.last())
        }

    /** Previously unknown publication stays unknown when its next record lookup fails. */
    @Test fun priorPublicationUncertaintySurvivesAnotherFailedRead() =
        runTest {
            val request = request(LotterySaveAction.PUBLISH).copy(publicationUncertain = true)
            repository.getHandler = { throw IOException("still unavailable") }
            assertEquals(
                LotterySaveResult.Failed(LotterySavePhase.RECOVERING, publicationUncertain = true),
                writer.save(request),
            )
            assertEquals(listOf("get:draft"), repository.events)
            repository.getHandler = null
            repository.records.value = listOf(draft)
            repository.getOffersHandler = { throw IOException("offers unavailable") }
            assertEquals(
                LotterySaveResult.Failed(
                    LotterySavePhase.SAVING_OFFER,
                    publicationUncertain = false,
                ),
                writer.save(request),
            )
            assertEquals(listOf("get:draft", "get:draft", "offers:draft"), repository.events)
        }

    /** An uncertain publication cannot recreate a subsequently missing target. */
    @Test fun priorPublicationUncertaintyCannotRecreateAMissingTarget() =
        runTest {
            assertEquals(
                LotterySaveResult.Unavailable,
                writer.save(request(LotterySaveAction.PUBLISH).copy(publicationUncertain = true)),
            )
            assertEquals(listOf("get:draft"), repository.events)
        }

    /** A previously published target cannot report a new request's unapplied edits as saved. */
    @Test fun publishedTargetDoesNotReportUnappliedInputAsSaved() =
        runTest {
            repository.records.value = listOf(draft.copy(publishedAt = CampingFixtures.now))
            for (action in LotterySaveAction.entries) {
                val request = request(action).copy(title = "Unsaved replacement")
                assertEquals(LotterySaveResult.Published(draft.id), writer.save(request))
            }
            assertEquals(listOf("get:draft", "get:draft"), repository.events)
        }

    /** An idempotent create returning a newly published record must stop before edits or offers. */
    @Test fun publicationDuringCreationCannotClaimDraftEditsWereSaved() =
        runTest {
            repository.createHandler = { draft.copy(publishedAt = CampingFixtures.now) }
            assertEquals(
                LotterySaveResult.Published(draft.id),
                writer.save(request().copy(title = "Unapplied replacement")),
            )
            assertEquals(listOf("get:draft", "create:draft"), repository.events)
        }

    /** Confirmed absence has no invented allocation content or read-failure interpretation. */
    @Test fun missingParentReadDoesNotInventAnEmptyOfferSet() =
        runTest {
            assertEquals(LotteryDraftRead.Missing, writer.read(draft.id, draft.ownerId))
            assertEquals(listOf("get:draft"), repository.events)
        }

    /** Interrupted publication can be resolved by identity without recreating form input. */
    @Test fun publicationRecoveryNeedsOnlyTheRetainedTargetAndOwner() =
        runTest {
            repository.getOffersHandler =
                { error("Publication recovery does not hydrate allocations") }
            repository.records.value = listOf(draft)
            assertEquals(
                LotterySaveResult.Failed(LotterySavePhase.PUBLISHING),
                writer.recoverPublication(draft.id, draft.ownerId),
            )
            repository.records.value = listOf(draft.copy(publishedAt = CampingFixtures.now))
            assertEquals(
                LotterySaveResult.Saved(draft.id),
                writer.recoverPublication(draft.id, draft.ownerId),
            )
            repository.records.value = emptyList()
            assertEquals(
                LotterySaveResult.Unavailable,
                writer.recoverPublication(draft.id, draft.ownerId),
            )
            repository.getHandler = { throw IOException("record unavailable") }
            assertEquals(
                LotterySaveResult.Failed(LotterySavePhase.RECOVERING, publicationUncertain = true),
                writer.recoverPublication(draft.id, draft.ownerId),
            )
            assertEquals(List(4) { "get:draft" }, repository.events)
        }

    /** Supplies valid fixed reviewed values without implementing schedule-feedback rules.
     * @param action Requested confirmed outcome.
     */
    private fun request(action: LotterySaveAction = LotterySaveAction.DRAFT): LotterySaveRequest =
        LotterySaveRequest(
            draft.id,
            draft.ownerId,
            draft.title,
            draft.description,
            draft.configuration,
            offer,
            allowCreate = true,
            action = action,
        )
}
