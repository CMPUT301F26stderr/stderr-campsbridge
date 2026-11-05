package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.notificationTitleResource
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.components.InboxEventRow
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.components.UnavailableInboxRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.ZoneId

/** Persistent account history, framed by WorkspaceScaffold rather than a second app bar.
 * @param state Receipt source, known events, unknown-time receipts and real acknowledgement errors.
 * @param onRetrySource Retries only a failed receipt observation.
 * @param onRetryItem Retries one failed/missing event or subject.
 * @param onOpen Explicit supported row selection; arrival/recomposition does not call it.
 * @param onRetryRead Retries acknowledgement without reopening the destination.
 * @param modifier Caller layout.
 */
@Composable
fun InboxScreen(
    state: InboxUiState,
    onRetrySource: () -> Unit,
    onRetryItem: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRetryRead: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        if (state.orderingPending || state.unreadCountPending) {
            item("pending") {
                Text(
                    stringResource(
                        when {
                            state.orderingPending && state.unreadCountPending -> {
                                R.string.inbox_helpers_pending
                            }

                            state.orderingPending -> {
                                R.string.inbox_ordering_pending
                            }

                            else -> {
                                R.string.inbox_count_pending
                            }
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.unreadCount?.let { count ->
            item("unread") {
                Text(
                    stringResource(R.string.inbox_unread_count, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        when (state.source) {
            InboxSourceState.LOADING -> {
                item("loading") { LoadingContent() }
            }

            InboxSourceState.FAILURE -> {
                item(
                    "failure",
                ) { FailureContent(stringResource(R.string.inbox_load_failed), onRetrySource) }
            }

            InboxSourceState.READY -> {
                if (state.items.isEmpty() &&
                    state.unavailable.isEmpty()
                ) {
                    item("empty") { EmptyContent(stringResource(R.string.inbox_empty)) }
                }
            }
        }
        items(state.items, key = { "event:${it.notification.id}" }) { item ->
            InboxEventRow(
                item,
                item.notification.id in state.readFailures,
                onOpen,
                onRetryItem,
                onRetryRead,
            )
        }
        if (state.unavailable.isNotEmpty()) {
            item("unknown") {
                Text(
                    stringResource(R.string.inbox_unknown_section),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(state.unavailable, key = { "receipt:${it.receipt.notificationId}" }) { item ->
                UnavailableInboxRow(item, onRetryItem)
            }
        }
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
            onOpen = {},
            onRetrySource = {},
            onRetryRead = {},
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
            readFailures = setOf("preview-confirmed"),
            source = InboxSourceState.FAILURE,
        ),
    )
}
