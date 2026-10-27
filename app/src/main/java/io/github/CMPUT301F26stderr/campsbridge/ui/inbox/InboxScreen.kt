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
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.ZoneId

/** Delivers only the active route's current explicit request; receipt writes stay in the model.
 * @param model One account-scoped receipt owner shared by both workspace primaries.
 * @param onOpen Opens a registered target and returns synchronously before request consumption.
 */
@Composable
fun InboxRoute(
    model: InboxViewModel,
    onOpen: (InboxOpenRequest) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val pending by model.pendingOpen.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val lifecycle by owner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(pending, lifecycle) {
        val request = pending
        if (lifecycle == Lifecycle.State.RESUMED &&
            owner.lifecycle.currentState == Lifecycle.State.RESUMED &&
            request != null &&
            model.pendingOpen.value == request
        ) {
            onOpen(request)
            model.acknowledgeOpen(request.notificationId)
        }
    }
    InboxScreen(state, model::retry, model::retryItem, { id ->
        if (owner.lifecycle.currentState == Lifecycle.State.RESUMED) model.openItem(id)
    }, model::retryReadAcknowledgement)
}

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

/** One ordinary Material row retaining actual event identity/time through context failures.
 * @param item Loaded immutable event and original receipt state.
 * @param readFailed Separate first-read acknowledgement failure.
 * @param onOpen Explicit supported row selection.
 * @param onRetryItem Retries only this context.
 * @param onRetryRead Retries this receipt write without navigating.
 */
@Composable
private fun InboxEventRow(
    item: InboxItem,
    readFailed: Boolean,
    onOpen: (String) -> Unit,
    onRetryItem: (String) -> Unit,
    onRetryRead: (String) -> Unit,
) {
    val event = item.notification
    val canOpen =
        !item.isLoading &&
            (
                item.subject is NotificationSubjectContent.LotteryContent ||
                    item.subject is NotificationSubjectContent.ReservationContent
            )
    val clickable =
        if (canOpen) {
            Modifier.clickable(role = Role.Button, onClick = { onOpen(event.id) })
        } else {
            Modifier
        }
    Column {
        Surface(
            Modifier.fillMaxWidth().testTag("inbox-event-${event.id}").then(clickable),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.padding(vertical = Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    Text(
                        event.announcement?.title
                            ?: stringResource(notificationTitleResource(event.type)),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight =
                            if (item.readAt ==
                                null
                            ) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                    )
                    if (canOpen) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val context =
                    when (val subject = item.subject) {
                        is NotificationSubjectContent.LotteryContent -> {
                            subject.lottery.title
                        }

                        is NotificationSubjectContent.ReservationContent -> {
                            subject.reservation.bookedCampsite.name
                        }

                        NotificationSubjectContent.Failure -> {
                            stringResource(
                                R.string.inbox_context_failed,
                            )
                        }

                        NotificationSubjectContent.Unavailable -> {
                            stringResource(
                                R.string.inbox_context_unavailable,
                            )
                        }

                        NotificationSubjectContent.Unsupported -> {
                            stringResource(
                                R.string.inbox_context_unsupported,
                            )
                        }
                    }
                Text(
                    context,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                event.announcement?.let {
                    Text(
                        it.body,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    Text(
                        formatDateTime(
                            event.createdAt,
                            ZoneId.systemDefault(),
                            LocalConfiguration.current.locales[0],
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    InboxReadLabel(item.readAt == null)
                }
                if (item.isLoading) {
                    Text(
                        stringResource(R.string.inbox_context_loading),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (item.subject == NotificationSubjectContent.Failure ||
                    item.subject == NotificationSubjectContent.Unavailable
                ) {
                    TextButton(
                        { onRetryItem(event.id) },
                    ) { Text(stringResource(R.string.inbox_retry_item)) }
                }
                if (readFailed) {
                    Text(
                        stringResource(R.string.inbox_read_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        { onRetryRead(event.id) },
                    ) { Text(stringResource(R.string.inbox_retry_read)) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Keeps missing-event history read-only without inventing its title or creation time.
 * @param item Original receipt and independent event read outcome.
 * @param onRetryItem Retries this receipt's exact immutable event identity.
 */
@Composable
private fun UnavailableInboxRow(
    item: UnavailableInboxReceipt,
    onRetryItem: (String) -> Unit,
) {
    Column {
        Surface(
            Modifier.fillMaxWidth().testTag("inbox-receipt-${item.receipt.notificationId}"),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.padding(vertical = Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    stringResource(
                        when (item.status) {
                            InboxEventStatus.LOADING -> R.string.inbox_event_loading
                            InboxEventStatus.FAILURE -> R.string.inbox_event_failed
                            InboxEventStatus.UNAVAILABLE -> R.string.inbox_event_unavailable
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                InboxReadLabel(item.receipt.readAt == null)
                if (item.status != InboxEventStatus.LOADING) {
                    TextButton({ onRetryItem(item.receipt.notificationId) }) {
                        Text(stringResource(R.string.inbox_retry_item))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Shows actual receipt state through text and emphasis, without marking it read.
 * @param unread Whether the original receipt has no acknowledgement timestamp.
 */
@Composable
private fun InboxReadLabel(unread: Boolean) {
    Text(
        stringResource(if (unread) R.string.inbox_unread else R.string.inbox_read),
        style = MaterialTheme.typography.labelMedium,
        color =
            if (unread) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    )
}

/** Passive supplied-state previews; the primary frame belongs to its workspace host.
 * @param state Real-shaped history or read outcome without a backend.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun InboxPreview(
    @PreviewParameter(InboxPreviewProvider::class) state: InboxUiState,
) {
    CampsbridgeTheme {
        AppScreen(stringResource(R.string.inbox_title)) { InboxScreen(state, {}, {}, {}, {}) }
    }
}
