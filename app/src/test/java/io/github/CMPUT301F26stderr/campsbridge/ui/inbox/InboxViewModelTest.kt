package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun observesReceiptsAndLoadsContent() = runTest {
        val notification = Notification(
            id = "n-1",
            senderId = null,
            type = NotificationType.LOTTERY_WON,
            subject = NotificationSubject(SubjectType.LOTTERY, "l-1"),
            announcement = null,
            createdAt = Instant.EPOCH,
        )
        val notifications = FakeNotificationRepository(listOf(notification))
        notifications.receipts.value = mapOf("account-1" to listOf(InboxReceipt("n-1", null)))
        val lotteries = FakeLotteryRepository()
        val bookings = FakeBookingRepository()
        val loader = NotificationContentLoader(notifications, lotteries, bookings)

        val viewModel = InboxViewModel("account-1", notifications, loader)
        runCurrent()

        val state = viewModel.state.value
        assertTrue(state is InboxUiState.Content)
        val content = state as InboxUiState.Content
        assertEquals(1, content.items.size)
        assertEquals("n-1", content.items[0].notification.id)
        assertEquals(1, content.unreadCount)
    }

    @Test
    fun sourceFailureLeadsToFailureState() = runTest {
        val notifications = FakeNotificationRepository()
        notifications.inboxFailure.value = RuntimeException("offline")
        val lotteries = FakeLotteryRepository()
        val bookings = FakeBookingRepository()
        val loader = NotificationContentLoader(notifications, lotteries, bookings)

        val viewModel = InboxViewModel("account-1", notifications, loader)
        runCurrent()

        assertEquals(InboxUiState.Failure, viewModel.state.value)
    }
}
