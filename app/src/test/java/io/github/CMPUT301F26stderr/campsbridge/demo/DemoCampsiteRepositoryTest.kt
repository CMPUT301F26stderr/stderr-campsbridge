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

    /** Unavailable writes throw an explicit expected limitation rather than fake success. */
    @Test fun unsupportedWritesDoNotChangeSuppliedSnapshots() =
        runTest {
            val site = sampleCampsite()
            val repository = DemoCampsiteRepository(listOf(site))
            val operations: List<suspend () -> Unit> =
                listOf(
                    {
                        repository.createDraft("new", site.ownerId, site.details)
                        Unit
                    },
                    { repository.update(site.id, site.ownerId, site.details) },
                    { repository.publish(site.id, site.ownerId) },
                    {
                        repository.setOffered(
                            site.id,
                            site.ownerId,
                            setOf(LocalDate.of(2027, 5, 14)),
                            true,
                        )
                    },
                )
            for (operation in operations) {
                val failure = runCatching { operation() }.exceptionOrNull()
                assertTrue(failure is PersistenceFailure)
                assertEquals(
                    PersistenceFailure.Reason.NOT_ALLOWED,
                    (failure as PersistenceFailure).reason,
                )
            }
            assertEquals(listOf(site), repository.observeAll().first())
        }
}
