/*
 * Only the resumed destination may deliver a shared account's explicit navigation request.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Only the resumed destination may deliver a shared account's explicit navigation request. */
class InboxRouteLifecycleTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val owners = mutableListOf<RouteOwner>()
    private val opened = mutableListOf<Pair<String, String>>()
    private lateinit var model: InboxViewModel
    private val reservation = previewReservation().copy(entrantId = "entrant")

    /** Releases account work and every test-owned route lifecycle. */
    @After fun close() {
        compose.runOnIdle {
            store.clear()
            owners.forEach { it.registry.currentState = Lifecycle.State.DESTROYED }
        }
    }

    /** A started destination retains the request until it resumes and delivers it only once. */
    @Test fun startedRouteWaitsForResumeWithoutLosingTheRequest() {
        mount("inbox" to Lifecycle.State.STARTED)
        compose.runOnIdle { model.openItem("notice") }
        compose.runOnIdle {
            assertTrue(opened.isEmpty())
            assertNotNull(model.pendingOpen.value)
            owners.single().registry.currentState = Lifecycle.State.RESUMED
        }
        compose.waitUntil { opened.size == 1 }
        compose.runOnIdle {
            assertEquals(listOf("inbox" to reservation.id), opened)
            assertNull(model.pendingOpen.value)
            owners.single().registry.currentState = Lifecycle.State.STARTED
            owners.single().registry.currentState = Lifecycle.State.RESUMED
        }
        compose.runOnIdle { assertEquals(1, opened.size) }
    }

    /** An inactive workspace cannot consume the request belonging to its active peer. */
    @Test fun sharedRequestIsDeliveredOnlyByTheResumedWorkspace() {
        mount("inactive" to Lifecycle.State.STARTED, "active" to Lifecycle.State.RESUMED)
        compose.runOnIdle { model.openItem("notice") }
        compose.waitUntil { opened.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(listOf("active" to reservation.id), opened)
            assertNull(model.pendingOpen.value)
        }
    }

    /** Mounts actual routes against one saved reservation and one account controller.
     * @param destinations Route labels and their independently controlled lifecycle states.
     */
    private fun mount(vararg destinations: Pair<String, Lifecycle.State>) {
        compose.runOnUiThread {
            val event =
                Notification(
                    "notice",
                    null,
                    NotificationType.RESERVATION_CONFIRMED,
                    NotificationSubject(SubjectType.RESERVATION, reservation.id),
                    null,
                    Instant.parse("2027-05-01T12:00:00Z"),
                )
            val notifications = FakeNotificationRepository(listOf(event))
            notifications.receipts.value = mapOf("entrant" to listOf(InboxReceipt(event.id, null)))
            model =
                InboxViewModel(
                    "entrant",
                    notifications,
                    NotificationContentLoader(
                        notifications,
                        FakeLotteryRepository(),
                        FakeBookingRepository(listOf(reservation)),
                    ),
                )
            store.put("inbox", model)
            destinations.forEach { (_, state) ->
                owners.add(RouteOwner().also { it.registry.currentState = state })
            }
        }
        compose.setContent {
            CampsbridgeTheme {
                Column(Modifier.fillMaxSize()) {
                    destinations.forEachIndexed { index, (label, _) ->
                        Box(Modifier.weight(1f)) {
                            CompositionLocalProvider(LocalLifecycleOwner provides owners[index]) {
                                InboxRoute(
                                    model,
                                    onOpenLottery = { id, _ -> opened.add(label to id) },
                                    onOpenReservation = { id, _ -> opened.add(label to id) },
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitUntil { model.state.value.items.size == 1 }
    }

    /** A route lifecycle controlled independently of the hosting test activity. */
    private class RouteOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
