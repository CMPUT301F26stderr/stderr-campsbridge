package io.github.CMPUT301F26stderr.campsbridge.platform.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.CMPUT301F26stderr.campsbridge.MainActivity
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType

/** Typed notification-tap envelope; root readiness and navigation consumption have separate owners.
 * @param accountId Intended ready recipient.
 * @param eventId Original immutable event.
 * @param subject Original saved lottery/reservation reference.
 */
data class NotificationEntry(
    val accountId: String,
    val eventId: String,
    val subject: NotificationSubject,
) {
    /** Derives unambiguous OS/tag identity from encoded account/event path segments. */
    fun identityUri(): Uri =
        Uri
            .Builder()
            .scheme("campsbridge")
            .authority("notification")
            .appendPath(accountId)
            .appendPath(eventId)
            .build()

    /** Creates an explicit cold/warm Activity intent for a valid supported entry.
     * @param context Actual application package/component context.
     * @throws IllegalArgumentException When an identifier is blank or its target unsupported.
     */
    fun intent(context: Context): Intent {
        require(isValid()) { "Invalid or unsupported notification entry" }
        return Intent(context, MainActivity::class.java)
            .setAction(ACTION)
            .setData(identityUri())
            .putExtra(EXTRA_ACCOUNT_ID, accountId)
            .putExtra(EXTRA_EVENT_ID, eventId)
            .putExtra(EXTRA_SUBJECT_TYPE, subject.type.name)
            .putExtra(EXTRA_SUBJECT_ID, subject.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /** Requires complete original IDs and a target registered by this build. */
    internal fun isValid(): Boolean =
        accountId.isNotBlank() && eventId.isNotBlank() && subject.id.isNotBlank() &&
            (subject.type == SubjectType.LOTTERY || subject.type == SubjectType.RESERVATION)

    /** Decodes only this app's valid supported notification envelope. */
    companion object {
        const val EXTRA_ACCOUNT_ID = "campsbridge.notification.account"
        const val EXTRA_EVENT_ID = "campsbridge.notification.event"
        const val EXTRA_SUBJECT_TYPE = "campsbridge.notification.subjectType"
        const val EXTRA_SUBJECT_ID = "campsbridge.notification.subjectId"
        private const val ACTION = "io.github.CMPUT301F26stderr.campsbridge.OPEN_NOTIFICATION"

        /** Returns null for unrelated/malformed intent data without launching or writing anything.
         * @param intent Cold/warm Android intent, including ordinary launcher intents.
         */
        fun fromIntent(intent: Intent?): NotificationEntry? {
            if (intent == null || intent.action != ACTION) return null
            val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: return null
            val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return null
            val subjectId = intent.getStringExtra(EXTRA_SUBJECT_ID) ?: return null
            val typeName = intent.getStringExtra(EXTRA_SUBJECT_TYPE) ?: return null
            val type = SubjectType.entries.firstOrNull { it.name == typeName } ?: return null
            val entry = NotificationEntry(accountId, eventId, NotificationSubject(type, subjectId))
            return entry.takeIf { it.isValid() && intent.data == it.identityUri() }
        }
    }
}
