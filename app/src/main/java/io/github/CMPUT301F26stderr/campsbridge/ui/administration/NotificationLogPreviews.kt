package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceDelivery
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceMember
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceUnavailable
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import java.time.Instant

/** Passive original event with long owner-supplied content for layout inspection. */
private fun notificationLogPreviewEvent(): Notification =
    Notification(
        "event/A ?1",
        "owner-account",
        NotificationType.OWNER_ANNOUNCEMENT,
        NotificationSubject(SubjectType.LOTTERY, "river-lottery"),
        Announcement(
            "Trail notice\nNorth meadow campsite",
            "Please bring drinking water. The path beside the river is closed until the next inspection.",
        ),
        Instant.parse("2027-05-01T12:00:00Z"),
    )

/** List source outcomes remain separate from user inputs in passive previews. */
class NotificationLogPreviewProvider : PreviewParameterProvider<NotificationLogUiState> {
    override val values =
        sequenceOf(
            NotificationLogUiState(),
            NotificationLogUiState(query = "trail", events = NotificationLogEvents.Failure),
            NotificationLogUiState(events = NotificationLogEvents.Content(emptyList())),
            NotificationLogUiState(events = NotificationLogEvents.Content(listOf(notificationLogPreviewEvent()))),
        )
}

/** Shows the list using the shared theme and inert actions.
 * @param state Passive source and filter fixture.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large log text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun NotificationLogPreview(
    @PreviewParameter(NotificationLogPreviewProvider::class) state: NotificationLogUiState,
) {
    CampsbridgeTheme { AppScreen(stringResource(R.string.admin_logs)) { NotificationLogScreen(state, {}, {}, {}, {}) } }
}

/** Original event facts accompany independent context outcomes. */
class NotificationLogDetailPreviewProvider : PreviewParameterProvider<NotificationLogDetailUiState> {
    override val values =
        sequence {
            val event = notificationLogPreviewEvent()
            yield(NotificationLogDetailUiState(event.id, event, isLoading = true))
            yield(NotificationLogDetailUiState(event.id, content = NotificationContent.Failure))
            yield(NotificationLogDetailUiState(event.id, content = NotificationContent.Unavailable))
            for (subject in listOf(
                NotificationSubjectContent.Unavailable,
                NotificationSubjectContent.Failure,
                NotificationSubjectContent.Unsupported,
            )) {
                yield(NotificationLogDetailUiState(event.id, event, NotificationContent.Loaded(event, subject)))
            }
        }
}

/** Shows event facts with long text and optional context outcomes.
 * @param state Passive original-event fixture.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large event text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun NotificationLogDetailPreview(
    @PreviewParameter(NotificationLogDetailPreviewProvider::class) state: NotificationLogDetailUiState,
) {
    CampsbridgeTheme {
        AppScreen(stringResource(R.string.log_event_details)) { NotificationLogDetailScreen(state, {}, {}) }
    }
}

/** Recipient loading, failure, absent information and recorded facts remain distinct. */
class NotificationLogAudiencePreviewProvider : PreviewParameterProvider<NotificationLogAudienceState> {
    override val values =
        sequenceOf(
            NotificationLogAudienceState.Loading("event"),
            NotificationLogAudienceState.Failure("event"),
            NotificationLogAudienceState.Content("event", NotificationAudience.NoRecordedInformation),
            NotificationLogAudienceState.Content("event", NotificationAudience.Recorded(emptyList())),
            NotificationLogAudienceState.Content(
                "event",
                NotificationAudience.Unavailable(NotificationAudienceUnavailable.EVENT_MISSING),
            ),
            NotificationLogAudienceState.Content(
                "event",
                NotificationAudience.Recorded(
                    listOf(
                        NotificationAudienceMember("pending-account", NotificationAudienceDelivery.PENDING),
                        NotificationAudienceMember("unread-account", NotificationAudienceDelivery.DELIVERED),
                        NotificationAudienceMember(
                            "read-account",
                            NotificationAudienceDelivery.DELIVERED,
                            notificationLogPreviewEvent().createdAt,
                        ),
                    ),
                ),
            ),
        )
}

/** Shows recorded recipients without constructing an event reader or acknowledging a receipt.
 * @param state Passive captured audience outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large recipient text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun NotificationLogAudiencePreview(
    @PreviewParameter(NotificationLogAudiencePreviewProvider::class) state: NotificationLogAudienceState,
) {
    CampsbridgeTheme { AppScreen(stringResource(R.string.log_recipients)) { NotificationAudienceScreen(state, {}) } }
}
