package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.notificationTitleResource
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant

/**
 * Renders account inbox receipts, unread counts, unavailable items, and read acknowledgement actions.
 * @param state Current inbox presentation state.
 * @param onOpenItem Explicit user action to open an inbox item and mark it as read.
 * @param onRetry Retries a failed receipt source observation.
 * @param onRetryMarkRead Retries a failed read acknowledgement write.
 * @param onRetryItem Retries one failed event or subject.
 * @param modifier Caller layout modifier.
 */
@Composable
fun InboxScreen(
    state: InboxUiState,
    onOpenItem: (InboxItem) -> Unit,
    onRetry: () -> Unit,
    onRetryMarkRead: () -> Unit,
    onRetryItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            if (state.source == InboxSourceState.LOADING) {
                item { LoadingContent() }
            }

            if (state.source == InboxSourceState.FAILURE) {
                item {
                    FailureContent(stringResource(R.string.inbox_source_failure), onRetry)
                }
            }
            if (state.saveFailed) {
                item {
                    Column(Modifier.padding(Spacing.medium)) {
                        Text(stringResource(R.string.inbox_ack_failure))
                        Button(onClick = onRetryMarkRead) {
                            Text(stringResource(R.string.inbox_retry_ack))
                        }
                    }
                }
            }
            if (state.source == InboxSourceState.READY && state.items.isEmpty() &&
                state.unavailable.isEmpty()
            ) {
                item { EmptyContent(stringResource(R.string.inbox_empty)) }
            } else {
                if (state.unavailable.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.inbox_unavailable_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(state.unavailable, key = { it.receipt.notificationId }) { unavail ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.medium)
                                    .testTag("inbox-receipt-${unavail.receipt.notificationId}"),
                        ) {
                            Text(
                                stringResource(R.string.inbox_unavailable_row),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (unavail.status != InboxEventStatus.LOADING) {
                                Button(onClick = { onRetryItem(unavail.receipt.notificationId) }) {
                                    Text(stringResource(R.string.inbox_retry_notification))
                                }
                            }
                        }
                    }
                }
                items(state.items, key = { it.notification.id }) { item ->
                    val isUnavailableSubject =
                        item.subject is NotificationSubjectContent.Unavailable ||
                            item.subject is NotificationSubjectContent.Failure ||
                            item.subject is NotificationSubjectContent.Unsupported

                    val title = stringResource(notificationTitleResource(item.notification.type))
                    if (isUnavailableSubject) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium)
                                .testTag("inbox-event-${item.notification.id}"),
                        ) {
                            Text(title)
                            val body =
                                when (item.subject) {
                                    NotificationSubjectContent.Unsupported -> {
                                        stringResource(
                                            R.string.inbox_unsupported_body,
                                        )
                                    }

                                    NotificationSubjectContent.Unavailable -> {
                                        stringResource(
                                            R.string.inbox_context_unavailable,
                                        )
                                    }

                                    NotificationSubjectContent.Failure -> {
                                        stringResource(
                                            R.string.inbox_context_failed,
                                        )
                                    }

                                    is NotificationSubjectContent.LotteryContent,
                                    is NotificationSubjectContent.ReservationContent,
                                    -> {
                                        ""
                                    }
                                }
                            Text(body)
                            if (!item.isLoading && item.subject != NotificationSubjectContent.Unsupported) {
                                Button(onClick = { onRetryItem(item.notification.id) }) {
                                    Text(stringResource(R.string.inbox_retry_notification))
                                }
                            }
                        }
                    } else {
                        InboxItemRow(
                            item = item,
                            onClick = { onOpenItem(item) },
                            modifier = Modifier.testTag("inbox-event-${item.notification.id}"),
                        )
                    }
                }
            }
        }
    }
}

/** One openable notification with its title, linked record name and read status.
 * @param item Loaded notification whose subject has a destination.
 * @param onClick Opens the item and marks it read.
 * @param modifier Caller layout modifier.
 */
@Composable
private fun InboxItemRow(
    item: InboxItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(notificationTitleResource(item.notification.type))
    val subtitle =
        when (val subject = item.subject) {
            is NotificationSubjectContent.LotteryContent -> subject.lottery.title

            is NotificationSubjectContent.ReservationContent -> subject.reservation.bookedCampsite.name

            NotificationSubjectContent.Failure,
            NotificationSubjectContent.Unavailable,
            NotificationSubjectContent.Unsupported,
            -> ""
        }
    val readStatus =
        if (item.readAt !=
            null
        ) {
            stringResource(R.string.inbox_read)
        } else {
            stringResource(R.string.inbox_unread)
        }
    val colors = MaterialTheme.colorScheme

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(Spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        Text(readStatus, style = MaterialTheme.typography.bodySmall, color = colors.primary)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
    }
}

/** Rows covering an openable unread stay and a notification with no destination. */
private fun previewInboxItems(): List<InboxItem> {
    val reservation = previewReservation()
    val confirmed =
        Notification(
            id = "preview-confirmed",
            senderId = null,
            type = NotificationType.RESERVATION_CONFIRMED,
            subject = NotificationSubject(SubjectType.RESERVATION, reservation.id),
            announcement = null,
            createdAt = Instant.EPOCH,
        )
    val message =
        confirmed.copy(
            id = "preview-message",
            type = NotificationType.NEW_MESSAGE,
            subject = NotificationSubject(SubjectType.MESSAGE, "preview-thread"),
        )
    return listOf(
        InboxItem(confirmed, null, NotificationSubjectContent.ReservationContent(reservation)),
        InboxItem(message, Instant.EPOCH, NotificationSubjectContent.Unsupported),
    )
}

/** Shows one inbox state in the app theme with inert actions.
 * @param state Inbox state to render.
 */
@Composable
private fun InboxPreviewHost(state: InboxUiState) {
    CampsbridgeTheme {
        InboxScreen(
            state = state,
            onOpenItem = {},
            onRetry = {},
            onRetryMarkRead = {},
            onRetryItem = {},
        )
    }
}

/** Previews the first load. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun InboxLoadingPreview() {
    InboxPreviewHost(InboxUiState())
}

/** Previews a source failure before any rows load. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun InboxFailurePreview() {
    InboxPreviewHost(InboxUiState(source = InboxSourceState.FAILURE))
}

/** Previews an account with no notifications. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun InboxEmptyPreview() {
    InboxPreviewHost(InboxUiState(source = InboxSourceState.READY, items = emptyList()))
}

/** Previews loaded rows. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun InboxContentPreview() {
    InboxPreviewHost(InboxUiState(source = InboxSourceState.READY, items = previewInboxItems(), unreadCount = 1))
}

/** Previews stale rows with source, read and missing-event failures. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun InboxRecoveryPreview() {
    InboxPreviewHost(
        InboxUiState(
            items = previewInboxItems(),
            unavailable =
                listOf(
                    UnavailableInboxReceipt(
                        InboxReceipt("preview-missing", null),
                        InboxEventStatus.UNAVAILABLE,
                    ),
                ),
            unreadCount = 2,
            saveFailed = true,
            source = InboxSourceState.FAILURE,
        ),
    )
}
