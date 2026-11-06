package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceDelivery
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceMember
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceUnavailable
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Fixed read-only audience facts, retries and selected-child lifetime contracts. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationLogAudienceTest {
    @get:Rule val main = MainDispatcherRule()

    /** Only a visible audience with an actual selected event performs this query. */
    @Test fun audienceStartsOnlyForTheVisibleSelectedIdentity() =
        logTest { f ->
            f.notifications.getAudienceHandler = { NotificationAudience.Recorded(emptyList()) }
            val model = f.model()
            runCurrent()
            model.setAudienceVisible(true)
            runCurrent()
            assertTrue(f.notifications.audienceRequests.isEmpty())
            model.selectEvent("event/A ?1")
            model.setAudienceVisible(true)
            runCurrent()
            assertEquals(listOf("event/A ?1"), f.notifications.audienceRequests)
            assertTrue(f.notifications.readRequests.isEmpty())
        }

    /** Empty membership, missing information and every unavailable reason retain distinct outcomes. */
    @Test fun repositoryOutcomesAreNotCollapsedIntoEmptyRecipients() =
        logTest { f ->
            val model = f.model()
            model.selectEvent("event")
            val outcomes =
                listOf(NotificationAudience.Recorded(emptyList()), NotificationAudience.NoRecordedInformation) +
                    NotificationAudienceUnavailable.entries.map { NotificationAudience.Unavailable(it) }
            for (outcome in outcomes) {
                model.setAudienceVisible(false)
                f.notifications.getAudienceHandler = { outcome }
                model.setAudienceVisible(true)
                runCurrent()
                assertEquals(NotificationLogAudienceState.Content("event", outcome), model.audience.value)
            }
        }

    /** Pending, delivered-unread and delivered-read preserve actual captured account IDs and times. */
    @Test fun membersKeepRecordedDeliveryAndReadFacts() =
        logTest { f ->
            val members =
                listOf(
                    NotificationAudienceMember("pending", NotificationAudienceDelivery.PENDING),
                    NotificationAudienceMember("unread", NotificationAudienceDelivery.DELIVERED),
                    NotificationAudienceMember("read", NotificationAudienceDelivery.DELIVERED, CampingFixtures.now),
                )
            val result = NotificationAudience.Recorded(members)
            f.notifications.getAudienceHandler = { result }
            val model = f.model()
            model.selectEvent("event")
            model.setAudienceVisible(true)
            runCurrent()
            assertEquals(NotificationLogAudienceState.Content("event", result), model.audience.value)
            assertTrue(f.notifications.readRequests.isEmpty())
            assertTrue(f.bookings.reserveRequests.isEmpty())
        }

    /** A held read stays Loading and repeated retry cannot multiply the active request. */
    @Test fun heldAudienceCoalescesRetries() =
        logTest { f ->
            val held = CompletableDeferred<Unit>()
            f.notifications.getAudienceHandler = {
                held.await()
                NotificationAudience.NoRecordedInformation
            }
            val model = f.model()
            model.selectEvent("event")
            model.setAudienceVisible(true)
            runCurrent()
            model.retryAudience()
            model.setAudienceVisible(true)
            runCurrent()
            assertEquals(NotificationLogAudienceState.Loading("event"), model.audience.value)
            assertEquals(listOf("event"), f.notifications.audienceRequests)
            held.complete(Unit)
            runCurrent()
            assertEquals(
                NotificationLogAudienceState.Content("event", NotificationAudience.NoRecordedInformation),
                model.audience.value,
            )
        }

    /** Failed audience retries leave both the selected content and all-event subscription intact. */
    @Test fun failedAudienceRetriesIndependently() =
        logTest { f ->
            f.notifications.records.value = listOf(logEvent())
            f.notifications.getAudienceHandler = { throw IOException("audience offline") }
            val model = f.model()
            runCurrent()
            model.selectEvent(logEvent().id)
            model.setAudienceVisible(true)
            runCurrent()
            assertEquals(NotificationLogAudienceState.Failure(logEvent().id), model.audience.value)
            val eventReads = f.notifications.getRequests.toList()
            f.notifications.getAudienceHandler = { NotificationAudience.Recorded(emptyList()) }
            model.retryAudience()
            model.retryAudience()
            runCurrent()
            assertEquals(
                NotificationLogAudienceState.Content(logEvent().id, NotificationAudience.Recorded(emptyList())),
                model.audience.value,
            )
            assertEquals(eventReads, f.notifications.getRequests)
            assertEquals(1, f.listRequests)
            assertEquals(2, f.notifications.audienceRequests.size)
        }

    /** Refresh replaces captured receipt facts without restarting event or list reads. */
    @Test fun loadedAudienceRefreshesIndependently() =
        logTest { f ->
            val event = logEvent()
            f.notifications.records.value = listOf(event)
            f.notifications.getAudienceHandler = { NotificationAudience.NoRecordedInformation }
            val model = f.model()
            runCurrent()
            model.selectEvent(event.id)
            model.setAudienceVisible(true)
            runCurrent()
            val eventReads = f.notifications.getRequests.toList()
            val recorded =
                NotificationAudience.Recorded(
                    listOf(
                        NotificationAudienceMember("read", NotificationAudienceDelivery.DELIVERED, CampingFixtures.now),
                    ),
                )
            f.notifications.getAudienceHandler = { recorded }
            model.retryAudience()
            model.retryAudience()
            runCurrent()
            assertEquals(NotificationLogAudienceState.Content(event.id, recorded), model.audience.value)
            assertEquals(eventReads, f.notifications.getRequests)
            assertEquals(1, f.listRequests)
            assertEquals(listOf(event.id, event.id), f.notifications.audienceRequests)
            assertTrue(f.notifications.readRequests.isEmpty())
        }

    /** Leaving the audience cancels its work and ignores even a noncooperative late reply. */
    @Test fun hiddenAudienceRejectsLateResults() =
        logTest { f ->
            val held = CompletableDeferred<Unit>()
            f.notifications.getAudienceHandler =
                {
                    withContext(NonCancellable) { held.await() }
                    NotificationAudience.NoRecordedInformation
                }
            val model = f.model()
            model.selectEvent("event")
            model.setAudienceVisible(true)
            runCurrent()
            model.setAudienceVisible(false)
            held.complete(Unit)
            runCurrent()
            assertEquals(NotificationLogAudienceState.Idle, model.audience.value)
            assertEquals("event", model.detail.value.id)
        }

    /** Same-ID siblings reuse event facts while reopening the audience reads fresh receipt facts. */
    @Test fun siblingPagesReuseEventButRefreshAudienceOnReturn() =
        logTest { f ->
            f.notifications.getAudienceHandler = { NotificationAudience.NoRecordedInformation }
            val model = f.model()
            model.selectEvent("event")
            model.setAudienceVisible(true)
            runCurrent()
            model.setAudienceVisible(false)
            model.selectEvent("event")
            model.setAudienceVisible(true)
            runCurrent()
            assertEquals(listOf("event"), f.notifications.getRequests)
            assertEquals(listOf("event", "event"), f.notifications.audienceRequests)
        }

    /** Changing event identity retires its child and cannot publish another event's recipients. */
    @Test fun replacingEventRetiresItsAudience() =
        logTest { f ->
            val held = CompletableDeferred<Unit>()
            f.notifications.getAudienceHandler =
                {
                    withContext(NonCancellable) { held.await() }
                    NotificationAudience.NoRecordedInformation
                }
            val model = f.model()
            model.selectEvent("old")
            model.setAudienceVisible(true)
            runCurrent()
            model.selectEvent("new")
            held.complete(Unit)
            runCurrent()
            assertEquals(NotificationLogAudienceState.Idle, model.audience.value)
            assertEquals("new", model.detail.value.id)
        }

    /** A retired graph cannot publish audience data after its read finally returns. */
    @Test fun graphClearRejectsLateAudience() =
        logTest { f ->
            val held = CompletableDeferred<Unit>()
            f.notifications.getAudienceHandler =
                {
                    withContext(NonCancellable) { held.await() }
                    NotificationAudience.NoRecordedInformation
                }
            val model = f.model()
            model.selectEvent("event")
            model.setAudienceVisible(true)
            runCurrent()
            val before = model.audience.value
            f.store.clear()
            held.complete(Unit)
            runCurrent()
            assertEquals(before, model.audience.value)
            assertTrue(f.notifications.readRequests.isEmpty())
        }
}
