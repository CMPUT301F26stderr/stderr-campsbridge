/*
 * Uses actual admin navigation with a separately controlled read-only log source.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.time.Instant

/** Uses actual admin navigation with a separately controlled read-only log source. */
internal class NotificationLogUiFixture {
    val host = TestAppHost()
    val failure = MutableStateFlow<Exception?>(null)
    var listRequests = 0
    var activeLists = 0
    val event =
        Notification(
            "event/A ?1",
            "owner",
            NotificationType.OWNER_ANNOUNCEMENT,
            NotificationSubject(SubjectType.LOTTERY, "subject/A"),
            Announcement("Trail notice", "Bring drinking water."),
            Instant.parse("2027-05-01T12:00:00Z"),
        )

    init {
        host.logNotifications =
            object : NotificationRepository by host.notifications {
                /** Supplies a controlled log without changing account Inbox history. */
                override fun observeAll(): Flow<List<Notification>> =
                    flow {
                        listRequests++
                        activeLists++
                        try {
                            combine(host.notifications.records, failure) { rows, error ->
                                error?.let { throw it }
                                rows
                            }.collect { emit(it) }
                        } finally {
                            activeLists--
                        }
                    }
            }
        host.accounts.account.value = sampleAccount(setOf(Role.ENTRANT, Role.ADMIN))
        host.preferences.progress.value =
            host.preferences.progress.value
                .copy(workspace = Role.ADMIN)
        host.notifications.records.value = listOf(event)
    }

    /** Enters Logs through the actual workspace primary destination.
     * @param compose Test host for actual app navigation.
     * @param restoration Optional actual composition restoration owner.
     * @param fontScale System text scale for the feature.
     */
    fun start(
        compose: ComposeContentTestRule,
        restoration: StateRestorationTester? = null,
        fontScale: Float = 1f,
    ) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) { host.Content() }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.onNodeWithText("Logs").performClick()
    }
}
