package io.github.CMPUT301F26stderr.campsbridge.platform.notifications

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Three fixed Activity delivery contracts; the separate root owner handles replay/navigation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationEntryHostTest {
    private val entry =
        NotificationEntry(
            "account/α",
            "event/one",
            NotificationSubject(SubjectType.LOTTERY, "draw/日本語"),
        )

    /** Cold/warm validated envelopes preserve identity and malformed launches have no callback. */
    @Test fun coldAndWarmDeliveryUseValidatedCurrentIntent() {
        val controller =
            Robolectric
                .buildActivity(
                    ComponentActivity::class.java,
                    intent(entry),
                ).setup()
        val received = mutableListOf<NotificationEntry>()
        try {
            controller.get().bindNotificationEntries(received::add)
            assertEquals(listOf(entry), received)
            val next = entry.copy(eventId = "event/two")
            controller.newIntent(intent(next))
            assertEquals(listOf(entry, next), received)
            assertEquals(next, NotificationEntry.fromIntent(controller.get().intent))
            controller.newIntent(Intent(Intent.ACTION_MAIN))
            controller.newIntent(
                intent(next).apply { removeExtra(NotificationEntry.EXTRA_SUBJECT_ID) },
            )
            assertEquals(listOf(entry, next), received)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    /** Restored state cannot suppress a new launch; repeated current identity goes to root dedupe. */
    @Test fun recreatedHostStillSuppliesCurrentOrNewLaunch() {
        val first = Robolectric.buildActivity(ComponentActivity::class.java, intent(entry)).setup()
        val state = Bundle()
        first
            .saveInstanceState(state)
            .pause()
            .stop()
            .destroy()
        for (launch in listOf(entry, entry.copy(eventId = "new-on-restore"))) {
            val received = mutableListOf<NotificationEntry>()
            val restored =
                Robolectric
                    .buildActivity(ComponentActivity::class.java, intent(launch))
                    .setup(Bundle(state))
            try {
                restored.get().bindNotificationEntries(received::add)
                assertEquals(listOf(launch), received)
            } finally {
                restored.pause().stop().destroy()
            }
        }
    }

    /** Destroyed Activity registrations no longer forward a later framework callback. */
    @Test fun destructionRemovesWarmListener() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java, Intent()).setup()
        val received = mutableListOf<NotificationEntry>()
        controller.get().bindNotificationEntries(received::add)
        controller.newIntent(intent(entry))
        assertEquals(listOf(entry), received)
        controller.pause().stop().destroy()
        controller.newIntent(intent(entry.copy(eventId = "retired")))
        assertEquals(listOf(entry), received)
    }

    /** Uses the production encoder while avoiding real Firebase MainActivity startup.
     * @param entry Original account/event/typed target.
     */
    private fun intent(entry: NotificationEntry): Intent =
        entry.intent(RuntimeEnvironment.getApplication()).setClass(
            RuntimeEnvironment.getApplication(),
            ComponentActivity::class.java,
        )
}
