package io.github.CMPUT301F26stderr.campsbridge.platform.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.CMPUT301F26stderr.campsbridge.MainActivity
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Eight fixed real SDK contracts; system visual heads-up and root navigation remain separate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidNotificationPublisherTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()
    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)
    private val alert =
        NotificationAlert(
            "entrant",
            "won",
            NotificationSubject(SubjectType.LOTTERY, "draw"),
            NotificationText("You were selected", "May camping"),
        )

    /** Grants actual shadow platform permission; individual denial cases replace this fact. */
    @Before fun permitNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** A permitted submission creates the shared HIGH channel and ordinary OS notification. */
    @Test fun permittedSubmissionUsesHighChannelAndResolvedContent() {
        AndroidNotificationPublisher(context).post(alert)
        assertEquals(1, manager.activeNotifications.size)
        val saved = manager.activeNotifications.single().notification
        assertEquals(RESULT_NOTIFICATION_CHANNEL, saved.channelId)
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            manager.getNotificationChannel(RESULT_NOTIFICATION_CHANNEL).importance,
        )
        assertEquals(
            alert.text.title,
            saved.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(
            alert.text.body,
            saved.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(
            alert.text.body,
            saved.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString(),
        )
        assertTrue(saved.smallIcon.resId != 0)
        assertEquals(Notification.CATEGORY_EVENT, saved.category)
        assertTrue(saved.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertTrue(saved.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    /** The same account/event updates one stable notification rather than creating duplicates. */
    @Test fun repeatedIdentityReplacesItsExistingNotification() {
        val publisher = AndroidNotificationPublisher(context)
        publisher.post(alert)
        publisher.post(alert.copy(text = NotificationText("Updated title", "Updated body")))
        assertEquals(1, manager.activeNotifications.size)
        val saved = manager.activeNotifications.single()
        assertEquals(
            NotificationEntry(
                alert.accountId,
                alert.eventId,
                alert.subject,
            ).identityUri().toString(),
            saved.tag,
        )
        assertEquals(
            "Updated title",
            saved.notification.extras
                .getCharSequence(Notification.EXTRA_TITLE)
                .toString(),
        )
    }

    /** Event hash collisions and another account's matching event keep distinct OS identities. */
    @Test fun distinctAccountAndEventIdentitiesKeepSeparateTagsAndTapData() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val requests =
            listOf(
                alert.copy(eventId = "Aa"),
                alert.copy(eventId = "BB"),
                alert.copy(accountId = "other", eventId = "Aa"),
            )
        val publisher = AndroidNotificationPublisher(context)
        requests.forEach(publisher::post)
        val saved = manager.activeNotifications
        assertEquals(3, saved.size)
        assertEquals(3, saved.map { it.tag }.toSet().size)
        assertEquals(1, saved.map { it.id }.toSet().size)
        assertEquals(
            requests.map { NotificationEntry(it.accountId, it.eventId, it.subject) }.toSet(),
            saved
                .map {
                    NotificationEntry.fromIntent(
                        shadowOf(it.notification.contentIntent).savedIntent,
                    )
                }.toSet(),
        )
        assertEquals(
            3,
            saved.map { shadowOf(it.notification.contentIntent).savedIntent.data }.toSet().size,
        )
    }

    /** Denied runtime permission is an explicit failure without creating or posting a channel. */
    @Test fun runtimeDenialDoesNotPretendToPost() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val failure =
            runCatching {
                AndroidNotificationPublisher(
                    context,
                ).post(alert)
            }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(manager.activeNotifications.isEmpty())
        assertTrue(manager.notificationChannels.isEmpty())
    }

    /** App-wide disabling prevents an OS submission even with runtime permission granted. */
    @Test fun appWideBlockDoesNotPretendToPost() {
        shadowOf(manager).setNotificationsEnabled(false)
        val failure =
            runCatching {
                AndroidNotificationPublisher(
                    context,
                ).post(alert)
            }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    /** Existing LOW importance is preserved, and later blocked importance cannot be reset. */
    @Test fun existingPermittedOrBlockedChannelKeepsItsImportance() {
        manager.createNotificationChannel(
            NotificationChannel(
                RESULT_NOTIFICATION_CHANNEL,
                "User channel",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val publisher = AndroidNotificationPublisher(context)
        publisher.post(alert)
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            manager.getNotificationChannel(RESULT_NOTIFICATION_CHANNEL).importance,
        )
        manager.cancelAll()
        manager.deleteNotificationChannel(RESULT_NOTIFICATION_CHANNEL)
        manager.createNotificationChannel(
            NotificationChannel(
                RESULT_NOTIFICATION_CHANNEL,
                "Blocked",
                NotificationManager.IMPORTANCE_NONE,
            ),
        )
        assertEquals(
            NotificationManager.IMPORTANCE_NONE,
            manager.getNotificationChannel(RESULT_NOTIFICATION_CHANNEL).importance,
        )
        assertTrue(runCatching { publisher.post(alert) }.exceptionOrNull() is IllegalStateException)
        assertTrue(manager.activeNotifications.isEmpty())
        assertEquals(
            NotificationManager.IMPORTANCE_NONE,
            manager.getNotificationChannel(RESULT_NOTIFICATION_CHANNEL).importance,
        )
    }

    /** Android 7 receives high-priority standard content without accessing channel-only APIs. */
    @Test
    @Config(sdk = [24])
    fun android24UsesPriorityWithoutChannelApis() {
        AndroidNotificationPublisher(context).post(alert)
        assertEquals(1, manager.activeNotifications.size)
        val saved = manager.activeNotifications.single().notification
        assertEquals(NotificationCompat.PRIORITY_HIGH, saved.priority)
        assertEquals(
            alert.text.title,
            saved.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
    }

    /** The immutable tap preserves the original typed target and cold/warm component contract. */
    @Test fun realPendingIntentIsImmutableAndTargetsTheOriginalRecord() {
        AndroidNotificationPublisher(context).post(alert)
        assertEquals(1, manager.activeNotifications.size)
        val pending =
            manager.activeNotifications
                .single()
                .notification.contentIntent
        assertNotNull(pending)
        assertTrue(pending.isImmutable)
        val intent = shadowOf(pending).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component!!.className)
        assertEquals(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            intent.flags,
        )
        assertEquals(
            NotificationEntry(alert.accountId, alert.eventId, alert.subject),
            NotificationEntry.fromIntent(intent),
        )
    }
}
