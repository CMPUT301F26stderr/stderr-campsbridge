package io.github.CMPUT301F26stderr.campsbridge.platform.notifications

import android.app.Application
import android.content.Intent
import android.net.Uri
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Four fixed tap-envelope contracts without Activity launch or root consumption. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationEntryTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()
    private val entry =
        NotificationEntry(
            "account/α",
            "event:β",
            NotificationSubject(SubjectType.LOTTERY, "draw/one"),
        )

    /** Both supported target categories preserve original encoded account/event/subject strings. */
    @Test fun supportedTargetsRoundTripWithEncodedIdentity() {
        for (type in listOf(SubjectType.LOTTERY, SubjectType.RESERVATION)) {
            val value = entry.copy(subject = NotificationSubject(type, "target/日本語"))
            val intent = value.intent(context)
            assertEquals(value, NotificationEntry.fromIntent(intent))
            assertEquals(listOf(value.accountId, value.eventId), intent.data!!.pathSegments)
            assertEquals(value.identityUri(), intent.data)
        }
    }

    /** Missing, blank, wrong-type and unsupported fields remain malformed rather than guessed. */
    @Test fun invalidExtrasAreRejected() {
        for (key in listOf(
            NotificationEntry.EXTRA_ACCOUNT_ID,
            NotificationEntry.EXTRA_EVENT_ID,
            NotificationEntry.EXTRA_SUBJECT_ID,
            NotificationEntry.EXTRA_SUBJECT_TYPE,
        )) {
            assertNull(
                NotificationEntry.fromIntent(entry.intent(context).apply { removeExtra(key) }),
            )
            assertNull(NotificationEntry.fromIntent(entry.intent(context).putExtra(key, " ")))
            assertNull(NotificationEntry.fromIntent(entry.intent(context).putExtra(key, 42)))
        }
        for (type in listOf("UNKNOWN", SubjectType.MESSAGE.name)) {
            assertNull(
                NotificationEntry.fromIntent(
                    entry.intent(context).putExtra(NotificationEntry.EXTRA_SUBJECT_TYPE, type),
                ),
            )
        }
    }

    /** Ordinary launcher intents, unrelated actions and conflicting identity cannot become taps. */
    @Test fun unrelatedOrConflictingEnvelopesAreRejected() {
        assertNull(NotificationEntry.fromIntent(null))
        assertNull(NotificationEntry.fromIntent(Intent(Intent.ACTION_MAIN)))
        assertNull(NotificationEntry.fromIntent(entry.intent(context).setAction("other")))
        assertNull(
            NotificationEntry.fromIntent(
                entry.intent(context).setData(Uri.parse("campsbridge://other/account/event")),
            ),
        )
        assertNull(
            NotificationEntry.fromIntent(
                entry.intent(context).putExtra(NotificationEntry.EXTRA_ACCOUNT_ID, "different"),
            ),
        )
        assertNull(
            NotificationEntry.fromIntent(
                entry.intent(context).putExtra(NotificationEntry.EXTRA_EVENT_ID, "different"),
            ),
        )
    }

    /** Authoring rejects invalid values and identity is stable without delimiter collisions. */
    @Test fun builderValidatesInputsAndDerivesUnambiguousIdentity() {
        for (invalid in listOf(
            entry.copy(accountId = ""),
            entry.copy(eventId = " "),
            entry.copy(subject = NotificationSubject(SubjectType.LOTTERY, "")),
            entry.copy(subject = NotificationSubject(SubjectType.MESSAGE, "message")),
        )) {
            assertTrue(
                runCatching {
                    invalid.intent(
                        context,
                    )
                }.exceptionOrNull() is IllegalArgumentException,
            )
        }
        assertEquals(entry.identityUri(), entry.copy().identityUri())
        assertNotEquals(entry.identityUri(), entry.copy(accountId = "other").identityUri())
        assertNotEquals(entry.identityUri(), entry.copy(eventId = "other").identityUri())
        assertNotEquals(
            entry.copy(accountId = "a:b", eventId = "c").identityUri(),
            entry.copy(accountId = "a", eventId = "b:c").identityUri(),
        )
    }
}
