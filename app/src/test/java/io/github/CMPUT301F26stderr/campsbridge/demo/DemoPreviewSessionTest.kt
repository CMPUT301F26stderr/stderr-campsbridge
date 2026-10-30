package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.time.LocalDate

/** Verifies the populated offline session through its actual repository interfaces. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoPreviewSessionTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val files = TemporaryFolder()

    /** Entrant discovery, lottery history and booking readers resolve the same coherent records. */
    @Test fun entrantReadersExposeScopedSavedRecords() =
        runTest {
            val session = DemoSession(DemoScenario.ENTRANT, files.root)
            try {
                runCurrent()
                val repositories = session.services.repositories
                assertEquals(
                    0,
                    repositories.campsites
                        .observeForOwner("demo-account")
                        .first()
                        .size,
                )
                assertTrue(
                    repositories.lotteries
                        .observeForEntrant("missing")
                        .first()
                        .isEmpty(),
                )
                assertTrue(
                    repositories.lotteries
                        .observeForOwner("missing")
                        .first()
                        .isEmpty(),
                )
                assertNull(repositories.lotteries.get("missing"))
                val related =
                    repositories.lotteries
                        .observePublishedForCampsite(
                            "demo-river",
                        ).first()
                assertTrue(related.isNotEmpty())
                assertTrue(
                    related.all { draw ->
                        repositories.lotteries.getOffers(draw.id).any {
                            it.campsiteId ==
                                "demo-river"
                        }
                    },
                )
                val stay = repositories.bookings.get("demo-stay")!!
                assertEquals(stay, repositories.bookings.observe("demo-stay").first())
                assertEquals(
                    stay,
                    repositories.bookings.getForOpportunity(stay.lotteryId, stay.entrantId),
                )
                assertNull(repositories.bookings.getForOpportunity("demo-won", "demo-account"))
                assertTrue(
                    repositories.bookings
                        .observeForOwner("missing")
                        .first()
                        .isEmpty(),
                )
                assertTrue(
                    repositories.bookings
                        .observeForEntrant("missing")
                        .first()
                        .isEmpty(),
                )
            } finally {
                session.viewModelStore.clear()
            }
        }

    /** Calendar samples keep booked nights meaningful and respect the requested half-open range. */
    @Test fun calendarReadsAreScopedAndRetainOccupiedFacts() =
        runTest {
            val session = DemoSession(DemoScenario.ENTRANT, files.root)
            try {
                val repository = session.services.repositories.campsites
                val start = LocalDate.of(2027, 5, 14)
                val end = LocalDate.of(2027, 5, 18)
                val nights = repository.observeCalendar("demo-river", start, end).first()
                assertEquals((14..17).map { start.withDayOfMonth(it) }, nights.map { it.date })
                assertEquals(
                    listOf(15, 16),
                    nights
                        .filter {
                            it.reservationId == "demo-stay"
                        }.map { it.date.dayOfMonth },
                )
                assertTrue(repository.observeCalendar("missing", start, end).first().isEmpty())
            } finally {
                session.viewModelStore.clear()
            }
        }

    /** Local acknowledgement updates audience facts once and reset reinstalls unread samples. */
    @Test fun receiptsHaveLocalFirstReadAndResetIsolation() =
        runTest {
            val host = DemoHostViewModel(files.root)
            val store = ViewModelStore().apply { put("host", host) }
            try {
                runCurrent()
                val first = host.session.value
                val repository = first.services.repositories.notifications
                val original = repository.getInbox("demo-account")
                assertTrue(
                    original.any {
                        it.notificationId == "demo-notice-won" &&
                            it.readAt == null
                    },
                )
                repository.markRead("demo-account", "demo-notice-won")
                val read =
                    repository.getInbox("demo-account").single {
                        it.notificationId ==
                            "demo-notice-won"
                    }
                assertNotNull(read.readAt)
                repository.markRead("demo-account", "demo-notice-won")
                assertEquals(
                    read,
                    repository.getInbox("demo-account").single {
                        it.notificationId ==
                            read.notificationId
                    },
                )
                val audience =
                    repository.getAudience(
                        read.notificationId,
                    ) as NotificationAudience.Recorded
                assertEquals(
                    read.readAt,
                    audience.members.single { it.accountId == "demo-account" }.readAt,
                )
                assertTrue(repository.getInbox("missing").isEmpty())
                host.reset()
                runCurrent()
                assertEquals(
                    original,
                    host.session.value.services.repositories.notifications
                        .getInbox("demo-account"),
                )
            } finally {
                store.clear()
            }
        }

    /** A packaged campsite photo resolves locally and replacement/removal affect only that slot. */
    @Test fun samplePhotosRemainIndependentOfEditablePrivatePhotos() =
        runTest {
            val session = DemoSession(DemoScenario.ENTRANT, files.root)
            try {
                val repository = session.services.repositories.images
                val target = ImageTarget(ImageTargetType.CAMPSITE, "demo-river")
                val peer = ImageTarget(ImageTargetType.CAMPSITE, "demo-alpine")
                assertNotNull(repository.get(target))
                val original = repository.get(target)!!
                val originalPeer = repository.get(peer)
                assertEquals(
                    "file:///android_asset/photos/jasper.jpg",
                    repository.downloadUrl(original.blobId),
                )
                val replacement =
                    repository.replace(
                        target,
                        ImageUpload("image/png") {
                            ByteArrayInputStream(byteArrayOf(1))
                        },
                    )
                assertNull(replacement.obsoleteBlobId)
                assertTrue(
                    repository.downloadUrl(replacement.attachment!!.blobId).startsWith("file:"),
                )
                repository.delete(target)
                assertNull(repository.get(target))
                assertEquals(originalPeer, repository.get(peer))
            } finally {
                session.viewModelStore.clear()
            }
        }

    /** Existing actors resolve their intended workspace under one fixed fixture clock. */
    @Test fun ownerAndAdminScenariosOpenPopulatedWorkspaces() =
        runTest {
            for ((scenario, role) in listOf(
                DemoScenario.OWNER to Role.OWNER,
                DemoScenario.ADMIN to Role.ADMIN,
            )) {
                val session = DemoSession(scenario, files.root)
                try {
                    runCurrent()
                    val ready = session.model.state.value as SessionState.Ready
                    assertEquals(role, ready.workspace)
                    assertEquals(
                        java.time.Instant.parse("2027-05-10T18:00:00Z"),
                        session.services.clock.instant(),
                    )
                    val repositories = session.services.repositories
                    if (scenario == DemoScenario.ADMIN) {
                        assertTrue(Role.ADMIN in ready.account.roles)
                        assertTrue(
                            repositories.notifications
                                .observeAll()
                                .first()
                                .isNotEmpty(),
                        )
                    }
                } finally {
                    session.viewModelStore.clear()
                }
            }
        }

    /** Unsupported distributed operations never report saved local transactions. */
    @Test fun unsupportedWritesPreserveTheSeededRecords() =
        runTest {
            val session = DemoSession(DemoScenario.ENTRANT, files.root)
            try {
                val repositories = session.services.repositories
                val draws = repositories.lotteries.observeAll().first()
                val entries = repositories.lotteries.observeEntries("demo-open").first()
                assertTrue(draws.isNotEmpty())
                assertTrue(
                    runCatching {
                        repositories.lotteries.join(
                            "demo-open",
                            "demo-account",
                        )
                    }.isFailure,
                )
                assertEquals(draws, repositories.lotteries.observeAll().first())
                assertEquals(entries, repositories.lotteries.observeEntries("demo-open").first())
                assertTrue(runCatching { repositories.lotteries.draw("demo-open") }.isFailure)
            } finally {
                session.viewModelStore.clear()
            }
        }
}
