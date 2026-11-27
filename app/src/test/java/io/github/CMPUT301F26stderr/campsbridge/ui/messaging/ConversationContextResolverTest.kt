/*
 * NE-01 actual booked-site participants, reused by direct and immutable-event entry.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** NE-01 actual booked-site participants, reused by direct and immutable-event entry. */
class ConversationContextResolverTest {
    private val reservation = CampingFixtures.reservation().copy(campsiteId = "site-B")
    private val site = CampingFixtures.campsite("site-B", "owner-B")

    /** NE-01: entrant E and actual owner B resolve; collaborator A has no participant authority. */
    @Test fun savedBookedSiteDeterminesTheTwoActualParticipants() =
        runTest {
            val resolver = ConversationContextResolver(FakeCampsiteRepository(listOf(site)))
            for (actor in listOf(reservation.entrantId, site.ownerId)) {
                val result = resolver.resolve(reservation, actor)
                assertEquals(reservation, result?.reservation)
                assertEquals(setOf(reservation.entrantId, site.ownerId), result?.participantIds)
                assertEquals(setOf(reservation.entrantId, site.ownerId).single { it != actor }, result?.otherId)
            }
            assertNull(resolver.resolve(reservation, "owner-A"))
        }

    /** NE-01: missing context cannot authorize entry and a failed read remains a failure. */
    @Test fun missingAndFailedSiteContextNeverAuthorizeAConversation() =
        runTest {
            val sites = FakeCampsiteRepository()
            val resolver = ConversationContextResolver(sites)
            assertNull(resolver.resolve(reservation, reservation.entrantId))
            sites.readFailure = IOException("site offline")
            val result = runCatching { resolver.resolve(reservation, reservation.entrantId) }
            assertTrue(result.exceptionOrNull() is IOException)
        }
}
