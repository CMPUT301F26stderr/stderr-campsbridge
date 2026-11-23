/*
 * The shared offline composition performs real local cleanup behind the same admin boundary.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo

import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoStore
import io.github.CMPUT301F26stderr.campsbridge.demo.data.documents
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The shared offline composition performs real local cleanup behind the same admin boundary. */
class DemoMaintenanceTest {
    @get:Rule val files = TemporaryFolder()

    /** Recovery drains recorded site and photo jobs while preserving their first attribution. */
    @Test fun adminResumesDurableRemovalAndDetachedPhotos() =
        runTest {
            val data = DemoPreviewData(DemoScenario.ADMIN)
            val store = DemoStore(data.documents())
            val directory = files.newFolder()
            val repositories = DemoRepositories(store, data, directory)
            val removal = Removal(store.clock.instant(), "demo-host")
            val site = sampleCampsite("recovery-site", "demo-host").copy(removal = removal)
            val obsolete = File(directory, "obsolete-photo").apply { writeText("old image") }
            store.change {
                put("campsites/${site.id}", CampsiteDocument, site)
                raw["_removals/campsite_${site.id}"] = removal.stored()
                raw["_storageCleanup/${obsolete.name}"] =
                    mapOf(
                        "queuedAt" to store.clock.instant().stored(),
                        "attachmentId" to "campsite_${site.id}",
                    )
            }
            repositories.maintenance.resume("demo-admin")
            assertFalse(obsolete.exists())
            assertEquals(removal, repositories.campsites.get(site.id)?.removal)
            assertTrue(
                store.read {
                    raw.keys.none {
                        it == "_removals/campsite_${site.id}" ||
                            it == "_storageCleanup/${obsolete.name}"
                    }
                },
            )
            repositories.maintenance.resume("demo-admin")
            assertEquals(removal, repositories.campsites.get(site.id)?.removal)
        }

    /** Ordinary users cannot run a global cleanup pass, even in the offline demo. */
    @Test fun nonAdminCannotResumeGlobalWork() =
        runTest {
            val data = DemoPreviewData(DemoScenario.ENTRANT)
            val store = DemoStore(data.documents())
            val repositories = DemoRepositories(store, data, files.newFolder())
            try {
                repositories.maintenance.resume("demo-account")
                fail("An entrant must not authorize global maintenance")
            } catch (failure: PersistenceFailure) {
                assertEquals(PersistenceFailure.Reason.NOT_ALLOWED, failure.reason)
            }
        }
}
