package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Fixed collaborating-owner contracts; persistence suites own transaction/race proofs. */
@OptIn(ExperimentalCoroutinesApi::class)
class CollaboratingOwnersTest {
    @get:Rule val main = MainDispatcherRule()
    private val anchor = Instant.parse("2027-05-10T18:00:00Z")
    private val clock = MutableClock(anchor)
    private val organizer = sampleAccount(setOf(Role.OWNER)).copy(id = "owner-a", name = "Alex")
    private val contributor = sampleAccount(setOf(Role.OWNER)).copy(id = "owner-b", name = "Sam")
    private val addition = sampleAccount(setOf(Role.OWNER)).copy(id = "owner-c", name = "Sam")
    private val other = sampleAccount(setOf(Role.OWNER)).copy(id = "owner-d", name = "Taylor")
    private val entrant = sampleAccount().copy(id = "entrant-e", name = "Sam")
    private val removed = addition.copy(id = "removed", removal = Removal(anchor, "admin"))
    private val profiles = MutableStateFlow(listOf(organizer, contributor, addition, other, entrant, removed))
    private val accounts =
        object : AccountRepository by FakeAccountRepository() {
            /** Supplies this picker's controlled profile table without implementing persistence. */
            override fun observeAll(): Flow<List<Account>> =
                profiles
        }
    private val siteB = sampleCampsite("site-b", contributor.id)
    private val siteD = sampleCampsite("site-d", other.id)
    private val campsites = FakeCampsiteRepository(listOf(siteB, siteD))
    private val offerB = LotteryOffer(siteB.id, listOf(LocalDate.of(2027, 5, 18)))
    private val offerD = LotteryOffer(siteD.id, listOf(LocalDate.of(2027, 5, 22)))
    private val initialMembers = setOf(organizer.id, contributor.id)
    private val lottery =
        Lottery(
            "lottery-a",
            organizer.id,
            "Shared May stays",
            "Quiet stays.",
            LotteryConfiguration(
                anchor.plusSeconds(3600),
                anchor.plusSeconds(86400),
                anchor.plusSeconds(86400),
                anchor.plusSeconds(172800),
                5,
                null,
            ),
            0,
            anchor,
            anchor,
            participantOwnerIds = initialMembers,
        )
    private val lotteries = FakeLotteryRepository(listOf(lottery))
    private val requests = mutableListOf<Triple<String, String, Set<String>>>()
    private val store = ViewModelStore()
    private var modelNumber = 0

    /** Supplies one contributor allocation and an explicit fake membership write outcome. */
    @Before fun configure() {
        lotteries.serverOffers[lottery.id] = listOf(offerB)
        lotteries.liveOffers.value = mapOf(lottery.id to listOf(offerB))
        lotteries.updateParticipantsHandler = { id, actor, desired ->
            requests += Triple(id, actor, desired)
            lotteries.records.value =
                lotteries.records.value.map { if (it.id == id) it.copy(participantOwnerIds = desired) else it }
        }
    }

    /** Releases the real controller's required saved-context observers. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, lotteries.activeRecordObservers)
        assertEquals(0, lotteries.activeOfferObservers)
    }

    /** CO-01: search filters new owners, retains member context and explains identity/selection locks. */
    @Test fun filtersNewOwnersWithoutLosingExistingMembersOrTheirLocks() =
        runTest {
            val picker = model()
            runCurrent()
            picker.setQuery("sam")
            val state = picker.state.value
            assertEquals(LotteryTargetLoad.READY, state.targetLoad)
            assertEquals(setOf(organizer.id, contributor.id, addition.id), state.owners.map { it.id }.toSet())
            val organizerRow = state.owners.single { it.id == organizer.id }
            val contributedRow = state.owners.single { it.id == contributor.id }
            assertTrue(organizerRow.isOrganizer)
            assertFalse(organizerRow.canChange)
            assertTrue(contributedRow.hasAllocations)
            assertFalse(contributedRow.canChange)
            assertTrue(state.owners.filter { it.id in setOf(contributor.id, addition.id) }.all { it.showId })
            picker.setOwnerSelected(organizer.id, false)
            picker.setOwnerSelected(contributor.id, false)
            assertEquals(initialMembers, picker.state.value.selectedOwnerIds)
            profiles.value = profiles.value.filterNot { it.id == contributor.id }
            runCurrent()
            assertEquals(
                null,
                picker.state.value.owners
                    .single { it.id == contributor.id }
                    .name,
            )
            assertTrue(contributor.id in picker.state.value.selectedOwnerIds)
            picker.setQuery("no matching owner")
            assertEquals(
                initialMembers,
                picker.state.value.owners
                    .map { it.id }
                    .toSet(),
            )
            assertTrue(requests.isEmpty())
        }

    /** CO-01: participating co-owners cannot submit or alter organizer membership. */
    @Test fun contributorCannotChangeMembership() =
        runTest {
            val picker = model(contributor.id)
            runCurrent()
            assertFalse(picker.state.value.canEdit)
            picker.setOwnerSelected(addition.id, true)
            picker.save()
            runCurrent()
            assertEquals(initialMembers, picker.state.value.selectedOwnerIds)
            assertTrue(requests.isEmpty())
        }

    /** CO-02: checkbox changes stay local until Save sends the complete set and actual organizer. */
    @Test fun savesTheCompleteDesiredSetOnlyFromItsExplicitAction() =
        runTest {
            val picker = model()
            runCurrent()
            picker.setOwnerSelected(addition.id, true)
            val desired = initialMembers + addition.id
            assertEquals(desired, picker.state.value.selectedOwnerIds)
            assertTrue(requests.isEmpty())
            picker.save()
            runCurrent()
            assertEquals(listOf(Triple(lottery.id, organizer.id, desired)), requests)
            assertEquals(lottery.id, picker.completion.value)
        }

    /** CO-02: a failed write keeps query and desired membership available for explicit recovery. */
    @Test fun failedSaveRetainsSearchAndSelections() =
        runTest {
            lotteries.updateParticipantsHandler = { id, actor, desired ->
                requests += Triple(id, actor, desired)
                throw IOException("offline")
            }
            val picker = model()
            runCurrent()
            picker.setQuery("sam")
            picker.setOwnerSelected(addition.id, true)
            picker.save()
            runCurrent()
            assertTrue(picker.state.value.saveFailed)
            assertEquals("sam", picker.state.value.query)
            assertEquals(initialMembers + addition.id, picker.state.value.selectedOwnerIds)
            assertEquals(1, requests.size)
        }

    /** CO-02: conflict reads expose changed membership/allocations while preserving desired local input. */
    @Test fun conflictRefreshRequiresReviewAndAnExplicitResave() =
        runTest {
            val freshMembers = initialMembers + other.id
            lotteries.updateParticipantsHandler = { id, actor, desired ->
                requests += Triple(id, actor, desired)
                lotteries.getHandler = { lottery.copy(participantOwnerIds = freshMembers) }
                lotteries.getOffersHandler = { listOf(offerB, offerD) }
                throw PersistenceFailure(PersistenceFailure.Reason.CONFLICT, "Membership changed")
            }
            val picker = model()
            runCurrent()
            picker.setOwnerSelected(addition.id, true)
            picker.save()
            runCurrent()
            val refreshed = picker.state.value
            assertTrue(refreshed.contextChanged)
            assertEquals(freshMembers, refreshed.savedOwnerIds)
            assertEquals(initialMembers + addition.id, refreshed.selectedOwnerIds)
            assertTrue(refreshed.owners.single { it.id == other.id }.hasAllocations)
            assertFalse(refreshed.canSave)
            assertEquals(1, requests.size)
            picker.setOwnerSelected(other.id, true)
            lotteries.updateParticipantsHandler = { id, actor, desired -> requests += Triple(id, actor, desired) }
            picker.save()
            runCurrent()
            assertEquals(2, requests.size)
            assertEquals(Triple(lottery.id, organizer.id, initialMembers + addition.id + other.id), requests.last())
            assertEquals(lottery.id, picker.completion.value)
        }

    /** CO-03: opening registration, cancellation and unavailable context block Save without losing input. */
    @Test fun changedEligibilityKeepsLocalInputButBlocksSubmission() =
        runTest {
            for (change in listOf("opening", "cancellation", "unavailable")) {
                store.clear()
                runCurrent()
                clock.advance(Duration.between(clock.instant(), anchor))
                lotteries.records.value = listOf(lottery)
                val picker = model()
                runCurrent()
                picker.setOwnerSelected(addition.id, true)
                when (change) {
                    "opening" -> {
                        clock.advance(Duration.ofHours(1))
                    }

                    "cancellation" -> {
                        lotteries.records.value =
                            listOf(lottery.copy(cancellation = Cancellation(anchor, organizer.id)))
                    }

                    "unavailable" -> {
                        lotteries.records.value = emptyList()
                    }
                }
                picker.refreshTime()
                runCurrent()
                picker.save()
                runCurrent()
                assertFalse(picker.state.value.canSave)
                assertEquals(initialMembers + addition.id, picker.state.value.selectedOwnerIds)
                assertTrue(requests.isEmpty())
            }
        }

    /** CO-03: an unpublished saved draft remains editable at its configured opening. */
    @Test fun unpublishedDraftKeepsItsExistingEditingRule() =
        runTest {
            lotteries.records.value = listOf(lottery.copy(publishedAt = null))
            val picker = model()
            runCurrent()
            picker.setOwnerSelected(addition.id, true)
            clock.advance(Duration.ofHours(1))
            picker.refreshTime()
            assertTrue(picker.state.value.canSave)
            picker.save()
            runCurrent()
            assertEquals(listOf(Triple(lottery.id, organizer.id, initialMembers + addition.id)), requests)
        }

    /** Creates the actual picker controller with one controlled actor and shared related records.
     * @param actorId Organizer or participating co-owner identity.
     */
    private fun model(actorId: String = organizer.id): CollaboratingOwnersViewModel =
        CollaboratingOwnersViewModel(
            lottery.id,
            actorId,
            accounts,
            campsites,
            lotteries,
            clock,
            SavedStateHandle(),
        ).also {
            store.put("owners-${modelNumber++}", it)
        }
}
