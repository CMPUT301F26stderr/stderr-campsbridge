package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
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
