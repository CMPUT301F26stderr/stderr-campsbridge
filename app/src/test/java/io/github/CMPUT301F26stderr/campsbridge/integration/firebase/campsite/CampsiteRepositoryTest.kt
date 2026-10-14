package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.campsite

import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Owns listing lifecycle/query behavior and the documented independent calendar writes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class CampsiteRepositoryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Publishing and descriptive edits preserve listing identity and its first publication. */
    @Test(timeout = 60000)
    fun draftPublicationAndEditsPreserveLifecycleMetadata() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val campsites = owner.container.campsites
            val details = CampingFixtures.campsite().details
            val id = campsites.create(owner.id, details)
            val draft = campsites.get(id)!!
            assertFalse(draft.isPublished)
            assertNull(draft.publishedAt)
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            campsites.publish(id, owner.id)
            val published = campsites.get(id)!!
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            campsites.publish(id, owner.id)
            val changed = details.copy(name = "Updated campsite", nightlyPriceCents = 7000)
            campsites.update(id, owner.id, changed)
            assertEquals(published.copy(details = changed), campsites.get(id))
            assertEquals(draft.createdAt, published.createdAt)
            assertEquals(owner.id, published.ownerId)
        }

    /** Discovery queries work for an entrant while an owner's listing query includes its drafts. */
    @Test(timeout = 60000)
    fun discoveryExcludesDraftsAndRemovedListings() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val entrant = firebase.registeredActor(Role.ENTRANT)
            val published = CampingFixtures.campsite(ownerId = owner.id)
            firebase.seed("campsites/site", CampsiteDocument.encode(published))
            firebase.seed(
                "campsites/draft",
                CampsiteDocument.encode(published.copy(id = "draft", publishedAt = null)),
            )
            firebase.seed(
                "campsites/removed",
                CampsiteDocument.encode(
                    published.copy(
                        id = "removed",
                        removal = Removal(firebase.clock.instant(), owner.id),
                    ),
                ),
            )
            assertEquals(
                listOf("site"),
                entrant.container.campsites
                    .observePublished()
                    .first()
                    .map { it.id },
            )
            assertEquals(
                setOf(
                    "site",
                    "draft",
                    "removed",
                ),
                owner.container.campsites
                    .observeForOwner(owner.id)
                    .first()
                    .map {
                        it.id
                    }.toSet(),
            )
        }

    /** Calendar queries select the exact half-open local-date range and retain missing-night gaps. */
    @Test(timeout = 60000)
    fun calendarQueryExcludesCheckoutAndUnstoredDates() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val site = CampingFixtures.campsite(ownerId = owner.id)
            firebase.seed("campsites/site", CampsiteDocument.encode(site))
            val start = CampingFixtures.arrival
            owner.container.campsites.setOffered(
                "site",
                owner.id,
                setOf(start.minusDays(1), start, start.plusDays(2), start.plusDays(3)),
                true,
            )
            val calendar =
                owner.container.campsites
                    .observeCalendar(
                        "site",
                        start,
                        start.plusDays(3),
                    ).first()
            assertEquals(listOf(start, start.plusDays(2)), calendar.map { it.date })
            assertTrue(calendar.all { it.isFree })
        }

    /** A blocked bulk withdrawal retains earlier progress, then a retry converges after release. */
    @Test(timeout = 60000)
    fun calendarFailurePreservesProgressWithoutWithdrawingClaimedNight() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            firebase.seed(
                "campsites/site",
                CampsiteDocument.encode(CampingFixtures.campsite(ownerId = owner.id)),
            )
            val dates = (0L..2L).map(CampingFixtures.arrival::plusDays)
            owner.container.campsites.setOffered("site", owner.id, dates.toSet(), true)
            firebase.seed(
                "campsites/site/calendar/${dates[1]}",
                mapOf(
                    "isOffered" to true,
                    "reservationId" to "existing",
                ),
            )
            assertPersistenceFailure(Reason.CONFLICT) {
                owner.container.campsites.setOffered("site", owner.id, dates.toSet(), false)
            }
            val stored =
                dates.map { date ->
                    CalendarNightDocument.decode(
                        owner.clients.firestore
                            .document(
                                "campsites/site/calendar/$date",
                            ).get(Source.SERVER)
                            .await(),
                    )
                }
            assertFalse(stored[0].isOffered)
            assertTrue(stored[1].isOffered)
            assertEquals("existing", stored[1].reservationId)
            assertTrue(stored[2].isOffered)
            firebase.seed(
                "campsites/site/calendar/${dates[1]}",
                mapOf(
                    "isOffered" to true,
                    "reservationId" to null,
                ),
            )
            owner.container.campsites.setOffered("site", owner.id, dates.toSet(), false)
            val afterRetry =
                dates.map { date ->
                    CalendarNightDocument.decode(
                        owner.clients.firestore
                            .document(
                                "campsites/site/calendar/$date",
                            ).get(Source.SERVER)
                            .await(),
                    )
                }
            assertTrue(afterRetry.all { !it.isOffered && it.reservationId == null })
        }
}
