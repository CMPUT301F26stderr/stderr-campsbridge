/*
 * ST-01 and ST-02 preserve immutable sender facts and keep recipient writes outside history.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationContextResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** ST-01 and ST-02 preserve immutable sender facts and keep recipient writes outside history. */
@OptIn(ExperimentalCoroutinesApi::class)
class SentNotificationsTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val now = CampingFixtures.now
    private val draw = CampingFixtures.lottery()
    private val stay = CampingFixtures.reservation()
    private val announcement =
        Notification(
            "announcement",
            "owner",
            NotificationType.OWNER_ANNOUNCEMENT,
            NotificationSubject(SubjectType.LOTTERY, draw.id),
            Announcement("Trail access", "Use the east gate"),
            now,
        )
    private val message = Message("message", stay.id, "owner", "We will meet you there", now.plusSeconds(60))
    private val messageEvent =
        Notification(
            "message-event",
            "owner",
            NotificationType.NEW_MESSAGE,
            NotificationSubject(SubjectType.MESSAGE, message.id),
            null,
            message.createdAt,
        )
    private val notifications = FakeNotificationRepository(listOf(announcement, messageEvent))
    private val lotteries = FakeLotteryRepository(listOf(draw))
    private val bookings = FakeBookingRepository(listOf(stay))
    private val messages = FakeMessageRepository(listOf(message))
    private val campsites = FakeCampsiteRepository(listOf(CampingFixtures.campsite()))

    /** ST-01: mixed events remain ordered by their original facts while optional context fails. */
    @Test fun mixedSentEventsKeepTheirOriginalIdentityAcrossLinkedReadOutcomes() =
        test {
            notifications.sentHandler = { notifications.records }
            lotteries.records.value = emptyList()
            bookings.getHandler = { throw IOException("stay offline") }
            val model = model()
            runCurrent()
            assertEquals(InboxSourceState.READY, model.state.value.source)
            assertEquals(
                listOf(messageEvent.id, announcement.id),
                model.state.value.items
                    .map { it.event.id },
            )
            assertEquals(
                listOf(messageEvent, announcement),
                model.state.value.items
                    .map { it.event },
            )
            assertEquals(
                NotificationSubjectContent.Failure,
                model.state.value.items
                    .first()
                    .subject,
            )
            assertEquals(
                NotificationSubjectContent.Unavailable,
                model.state.value.items
                    .last()
                    .subject,
            )
            assertEquals(setOf(announcement.id, messageEvent.id), notifications.getRequests.toSet())
            lotteries.records.value = listOf(draw)
            bookings.getHandler = null
            model.retryItem(announcement.id)
            model.retryItem(messageEvent.id)
            runCurrent()
            assertNotNull(model.targetFor(announcement.id))
            assertNotNull(model.targetFor(messageEvent.id))
            assertTrue(notifications.readRequests.isEmpty())
            assertTrue(messages.readRequests.isEmpty())
        }

    /** ST-02: search/open/retry are read-only, and closing an owner rejects late content. */
    @Test fun localSearchAndExplicitRetryNeverAcknowledgeAndClosedOwnersRejectLateReads() =
        test {
            val failure = MutableStateFlow<Exception?>(IOException("history offline"))
            notifications.sentHandler = { actor ->
                combine(notifications.records, failure) { rows, error ->
                    error?.let { throw it }
                    rows.filter { it.senderId == actor }
                }
            }
            val model = model()
            runCurrent()
            assertEquals(InboxSourceState.FAILURE, model.state.value.source)
            failure.value = null
            model.retry()
            runCurrent()
            assertEquals(InboxSourceState.READY, model.state.value.source)
            model.editQuery("Trail")
            assertEquals(
                listOf(announcement.id),
                model.state.value.items
                    .map { it.event.id },
            )
            assertNotNull(model.targetFor(announcement.id))
            model.editQuery("")
            assertNotNull(model.targetFor(messageEvent.id))
            assertTrue(notifications.readRequests.isEmpty())
            assertTrue(messages.readRequests.isEmpty())
            val gate = CompletableDeferred<Notification?>()
            notifications.getHandler = { withContext(NonCancellable) { gate.await() } }
            val held = model(key = "held")
            runCurrent()
            val before = held.state.value
            store.clear()
            runCurrent()
            assertEquals(0, notifications.activeSentObservers)
            notifications.getHandler = null
            val nextActor = model(actor = "next-owner", key = "next")
            runCurrent()
            val nextState = nextActor.state.value
            gate.complete(announcement)
            runCurrent()
            assertEquals(before, held.state.value)
            assertEquals(nextState, nextActor.state.value)
            assertTrue(notifications.readRequests.isEmpty())
            assertTrue(messages.readRequests.isEmpty())
        }

    /** Constructs a real immutable-actor controller with the normal read contracts.
     * @param actor Original sender identity.
     * @param key Explicit owned controller key.
     */
    private fun model(
        actor: String = "owner",
        key: String = "sent",
    ) =
        SentNotificationsViewModel(
            actor,
            notifications,
            NotificationContentLoader(notifications, lotteries, bookings, messages),
            ConversationContextResolver(campsites),
            SavedStateHandle(),
        ).also { store.put(key, it) }

    /** Releases source/content observations around the fixed contract outcomes.
     * @param block Declared read/lifecycle assertions.
     */
    private fun test(block: suspend TestScope.() -> Unit) =
        runTest {
            try {
                block()
            } finally {
                store.clear()
                runCurrent()
            }
        }
}
