package io.github.CMPUT301F26stderr.campsbridge.domain.notification

import java.time.Instant

/** Assembled intended and Inbox-delivered recipient facts; these never describe OS delivery. */
sealed interface NotificationAudience {
    /** Known pending or delivered identities, including a captured empty intended set.
     * @param members Unique members in stable account-ID order.
     */
    data class Recorded(
        val members: List<NotificationAudienceMember>,
    ) : NotificationAudience

    /** No job and no receipts were found; this does not establish a zero intended audience. */
    data object NoRecordedInformation : NotificationAudience

    /** Original identity/context cannot support this read.
     * @param reason Explicit absent or unsupported subject fact.
     */
    data class Unavailable(
        val reason: NotificationAudienceUnavailable,
    ) : NotificationAudience
}

/** Why the original event cannot provide an audience read. */
enum class NotificationAudienceUnavailable {
    EVENT_MISSING,
    SUBJECT_MISSING,
    UNSUPPORTED_SUBJECT,
}

/** Intended membership or confirmed Inbox delivery; this does not describe an OS alert. */
enum class NotificationAudienceDelivery {
    PENDING,
    DELIVERED,
}

/** A factual identity remains meaningful even when its account no longer exists.
 * @param accountId Captured intended or confirmed receipt owner identity.
 * @param delivery PENDING from a captured job, DELIVERED only when a receipt exists.
 * @param readAt Original receipt first-read timestamp; null for unread or pending membership.
 */
data class NotificationAudienceMember(
    val accountId: String,
    val delivery: NotificationAudienceDelivery,
    val readAt: Instant? = null,
)
