package io.github.CMPUT301F26stderr.campsbridge.platform.notifications

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject

/** Resolved UI copy; rendering and repository reads happen before SDK publication.
 * @param title Localized event title or saved owner announcement title.
 * @param body Factual subject/announcement text, including explicit unavailable context.
 */
data class NotificationText(
    val title: String,
    val body: String,
)

/** One transient platform request; event/receipt facts remain in their original repositories.
 * @param accountId Recipient whose session owns the request.
 * @param eventId Immutable event identity used for stable Android replacement.
 * @param subject Original typed reference for the notification tap.
 * @param text Already resolved title/body; the SDK adapter never queries repositories.
 */
data class NotificationAlert(
    val accountId: String,
    val eventId: String,
    val subject: NotificationSubject,
    val text: NotificationText,
)

/** Small OS effect boundary; it owns neither preference decisions nor durable Inbox delivery. */
interface NotificationPublisher {
    /** Submits one permitted standard notification; failures propagate for coordinator retry.
     * Android still controls visibility and heads-up presentation.
     * @param alert Resolved recipient, stable identity, original target and display text.
     */
    fun post(alert: NotificationAlert)
}
