/*
 * Keeps offline campsite inspection truthful about snapshot reads and unsupported changes.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo

import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Keeps offline campsite inspection truthful about snapshot reads and unsupported changes. */
class DemoCampsiteRepositoryTest {
    /** Owner and discovery reads expose the supplied records without creating Firebase state. */
    @Test fun snapshotsRespectOwnerAndPublicationFacts() =
        runTest {
            val published = sampleCampsite()
            val draft = sampleCampsite("draft", published = false)
            val removed = sampleCampsite("removed").copy(removal = Removal(Instant.EPOCH, "admin"))
            val other = sampleCampsite("other", "other-owner")
            val rows = listOf(published, draft, removed, other)
            val repository = DemoCampsiteRepository(rows)
            assertEquals(rows, repository.observeAll().first())
            assertEquals(
                listOf(published, draft, removed),
                repository.observeForOwner("account-1").first(),
            )
            assertEquals(listOf(published, other), repository.observePublished().first())
            assertEquals(draft, repository.observe("draft").first())
            assertEquals(published, repository.get(published.id))
            assertNull(repository.get("missing"))
            assertTrue(
                repository
                    .observeCalendar(
                        published.id,
                        LocalDate.MIN,
                        LocalDate.MAX,
                    ).first()
                    .isEmpty(),
            )
        }

    /** A standalone read fixture still rejects writes from an unknown actor. */
    @Test fun unknownActorsCannotWriteSuppliedSnapshots() =
        runTest {
            val site = sampleCampsite()
            val repository = DemoCampsiteRepository(listOf(site))
            assertTrue(
                runCatching {
                    repository.update(
                        site.id,
                        "missing",
                        site.details,
                    )
                }.exceptionOrNull() is PersistenceFailure,
            )
            assertEquals(listOf(site), repository.observeAll().first())
        }
}
