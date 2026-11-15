/*
 * Supplies related events, subjects and recipient facts for notification log tests.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** Independent fixtures for the fixed log and recipient contracts. */
internal class NotificationLogTestFixture {
    val notifications = FakeNotificationRepository()
    val lotteries = FakeLotteryRepository()
    val bookings = FakeBookingRepository()
    val failure = MutableStateFlow<Exception?>(null)
    var listHandler: (() -> Flow<List<Notification>>)? = null
    var listRequests = 0
    var activeLists = 0
    val store = ViewModelStore()
    val repository =
        object : NotificationRepository by notifications {
            /** Observes only this fixture's explicit admin source and captures its lifetime. */
            override fun observeAll(): Flow<List<Notification>> =
                flow {
                    listRequests++
                    activeLists++
                    try {
                        val source =
                            listHandler?.invoke() ?: combine(notifications.records, failure) { events, error ->
                                error?.let { throw it }
                                events
                            }
                        source.collect { emit(it) }
                    } finally {
                        activeLists--
                    }
                }
        }

    /** Creates a graph-owned model with the actual common content reader.
     * @param saved Restored primitive values.
     * @param key Independent store identity when a scenario needs two lifetimes.
     */
    fun model(
        saved: SavedStateHandle = SavedStateHandle(),
        key: String = "log",
    ): NotificationLogViewModel =
        NotificationLogViewModel(
            repository,
            NotificationContentLoader(repository, lotteries, bookings, FakeMessageRepository()),
            saved,
        ).also { store.put(key, it) }
}

/** Creates a valid immutable event using the shared fixture time.
 * @param id Exact event identity.
 * @param type Matching event and subject category.
 */
internal fun logEvent(
    id: String = "event/A ?1",
    type: NotificationType = NotificationType.OWNER_ANNOUNCEMENT,
): Notification =
    Notification(
        id,
        if (type == NotificationType.OWNER_ANNOUNCEMENT) "owner" else null,
        type,
        NotificationSubject(
            when (type) {
                NotificationType.NEW_MESSAGE -> SubjectType.MESSAGE

                NotificationType.RESERVATION_CONFIRMED,
                NotificationType.RESERVATION_CANCELLED,
                -> SubjectType.RESERVATION

                else -> SubjectType.LOTTERY
            },
            "subject/A",
        ),
        if (type ==
            NotificationType.OWNER_ANNOUNCEMENT
        ) {
            Announcement("Trail notice", "Bring drinking water.")
        } else {
            null
        },
        CampingFixtures.now,
    )

/** Releases each scenario's graph jobs even after a failed assertion.
 * @param block Fixed contract scenario with controlled sources and coroutine scheduling.
 */
internal fun logTest(block: suspend TestScope.(NotificationLogTestFixture) -> Unit) =
    runTest {
        val fixture = NotificationLogTestFixture()
        try {
            block(fixture)
        } finally {
            fixture.store.clear()
        }
    }
