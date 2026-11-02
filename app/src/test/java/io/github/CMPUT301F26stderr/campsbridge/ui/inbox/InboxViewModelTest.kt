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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Inbox controller state and actions with controlled repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** Receipts load into readable rows with an unread count. */
    @Test
    fun observesReceiptsAndLoadsContent() =
        runTest {
            val notification =
                Notification(
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

    /** A source failure before any rows load shows the failure state. */
    @Test
    fun sourceFailureLeadsToFailureState() =
        runTest {
            val notifications = FakeNotificationRepository()
            notifications.inboxFailure.value = RuntimeException("offline")
            val lotteries = FakeLotteryRepository()
            val bookings = FakeBookingRepository()
            val loader = NotificationContentLoader(notifications, lotteries, bookings)

            val viewModel = InboxViewModel("account-1", notifications, loader)
            runCurrent()

            assertEquals(InboxUiState.Failure, viewModel.state.value)
        }

    /** A new receipt arriving before the route navigates keeps the open request. */
    @Test
    fun snapshotKeepsPendingOpenRequest() =
        runTest {
            val notifications = repository("n-1", "n-2")
            notifications.receipts.value = mapOf("account-1" to listOf(InboxReceipt("n-1", null)))
            val viewModel = viewModel(notifications)
            runCurrent()
            val item = (viewModel.state.value as InboxUiState.Content).items.single()

            viewModel.open(item)
            runCurrent()
            notifications.receipts.value =
                mapOf("account-1" to listOf(InboxReceipt("n-1", null), InboxReceipt("n-2", null)))
            runCurrent()

            val content = viewModel.state.value as InboxUiState.Content
            assertEquals(2, content.items.size)
            assertEquals(item, content.openingItem)
        }

    /** Retrying and a failed live source both keep rows that already loaded. */
    @Test
    fun retryAndSourceFailureKeepLoadedRows() =
        runTest {
            val notifications = repository("n-1")
            notifications.receipts.value = mapOf("account-1" to listOf(InboxReceipt("n-1", null)))
            val viewModel = viewModel(notifications)
            runCurrent()

            notifications.inboxFailure.value = RuntimeException("offline")
            runCurrent()
            val failed = viewModel.state.value as InboxUiState.Content
            assertEquals(1, failed.items.size)
            assertTrue(failed.sourceFailed)

            notifications.inboxFailure.value = null
            viewModel.retry()
            assertEquals(1, (viewModel.state.value as InboxUiState.Content).items.size)
            runCurrent()
            assertFalse((viewModel.state.value as InboxUiState.Content).sourceFailed)
        }

    /** A second tap while the first is pending doesn't write again. */
    @Test
    fun repeatedOpenMarksReadOnce() =
        runTest {
            val notifications = repository("n-1")
            notifications.receipts.value = mapOf("account-1" to listOf(InboxReceipt("n-1", null)))
            val write = CompletableDeferred<Unit>()
            notifications.markReadHandler = { _, _ -> write.await() }
            val viewModel = viewModel(notifications)
            runCurrent()
            val item = (viewModel.state.value as InboxUiState.Content).items.single()

            viewModel.open(item)
            viewModel.open(item)
            runCurrent()

            assertEquals(1, notifications.readRequests.size)
            write.complete(Unit)
            runCurrent()
        }

    /** Builds a repository holding one lottery event per ID.
     * @param ids Event IDs.
     */
    private fun repository(vararg ids: String) =
        FakeNotificationRepository(
            ids.map { id ->
                Notification(
                    id = id,
                    senderId = null,
                    type = NotificationType.LOTTERY_WON,
                    subject = NotificationSubject(SubjectType.LOTTERY, "l-1"),
                    announcement = null,
                    createdAt = Instant.EPOCH,
                )
            },
        )

    /** Creates the controller for the test account.
     * @param notifications Controlled notification source.
     */
    private fun viewModel(notifications: FakeNotificationRepository) =
        InboxViewModel(
            "account-1",
            notifications,
            NotificationContentLoader(
                notifications,
                FakeLotteryRepository(),
                FakeBookingRepository(),
            ),
        )
}
