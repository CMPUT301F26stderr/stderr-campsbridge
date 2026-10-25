package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
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
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
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
                )
            }
        }
        state.unreadCount?.let { count ->
            item("unread") { Text(stringResource(R.string.inbox_unread_count, count)) }
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
                Card(
                    Modifier.fillMaxWidth().testTag("inbox-receipt-${item.receipt.notificationId}"),
                ) {
                    Column(
                        Modifier.padding(Spacing.medium),
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
                        )
                        Text(
                            item.receipt.notificationId,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(
                                if (item.receipt.readAt ==
                                    null
                                ) {
                                    R.string.inbox_unread
                                } else {
                                    R.string.inbox_read
                                },
                            ),
                        )
                        if (item.status !=
                            InboxEventStatus.LOADING
                        ) {
                            TextButton({
                                onRetryItem(item.receipt.notificationId)
                            }) { Text(stringResource(R.string.inbox_retry_item)) }
                        }
                    }
                }
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
    val supported =
        item.subject is NotificationSubjectContent.LotteryContent ||
            item.subject is NotificationSubjectContent.ReservationContent
    val clickable =
        if (supported &&
            !item.isLoading
        ) {
            Modifier.clickable(role = Role.Button, onClick = { onOpen(event.id) })
        } else {
            Modifier
        }
    Card(Modifier.fillMaxWidth().testTag("inbox-event-${event.id}").then(clickable)) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(
                event.announcement?.title ?: stringResource(
                    when (event.type) {
                        NotificationType.LOTTERY_WON -> {
                            R.string.inbox_winner
                        }

                        NotificationType.LOTTERY_LOST -> {
                            R.string.inbox_loser
                        }

                        NotificationType.RESERVATION_CONFIRMED -> {
                            R.string.inbox_reservation_confirmed
                        }

                        NotificationType.RESERVATION_CANCELLED -> {
                            R.string.inbox_reservation_cancelled
                        }

                        NotificationType.OWNER_ANNOUNCEMENT -> {
                            R.string.inbox_announcement
                        }

                        NotificationType.NEW_MESSAGE -> {
                            R.string.inbox_message
                        }
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            when (val subject = item.subject) {
                is NotificationSubjectContent.LotteryContent -> {
                    Text(subject.lottery.title)
                }

                is NotificationSubjectContent.ReservationContent -> {
                    Text(
                        subject.reservation.bookedCampsite.name,
                    )
                }

                NotificationSubjectContent.Failure -> {
                    Text(
                        stringResource(R.string.inbox_context_failed),
                    )
                }

                NotificationSubjectContent.Unavailable -> {
                    Text(
                        stringResource(R.string.inbox_context_unavailable),
                    )
                }

                NotificationSubjectContent.Unsupported -> {
                    Text(
                        stringResource(R.string.inbox_context_unsupported),
                    )
                }
            }
            event.announcement?.let { Text(it.body) }
            Text(
                formatDateTime(
                    event.createdAt,
                    ZoneId.systemDefault(),
                    LocalConfiguration.current.locales[0],
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(
                    if (item.readAt ==
                        null
                    ) {
                        R.string.inbox_unread
                    } else {
                        R.string.inbox_read
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            if (item.isLoading) {
                Text(stringResource(R.string.inbox_context_loading))
            } else if (item.subject == NotificationSubjectContent.Failure ||
                item.subject == NotificationSubjectContent.Unavailable
            ) {
                TextButton(
                    { onRetryItem(event.id) },
                ) { Text(stringResource(R.string.inbox_retry_item)) }
            }
            if (readFailed) {
                Text(stringResource(R.string.inbox_read_failed))
                TextButton(
                    { onRetryRead(event.id) },
                ) { Text(stringResource(R.string.inbox_retry_read)) }
            }
        }
    }
}

/** Supplies readable win/loss/saved history and independent source/context failure variants. */
class InboxPreviewProvider : PreviewParameterProvider<InboxUiState> {
    override val values: Sequence<InboxUiState> =
        run {
            val reservation = previewReservation()
            val at = reservation.createdAt
            val lottery =
                Lottery(
                    "may-weekends",
                    "owner",
                    "May camping",
                    "Quiet riverside stays",
                    LotteryConfiguration(
                        at.minusSeconds(7200),
                        at.minusSeconds(3600),
                        at.minusSeconds(1800),
                        at.plusSeconds(3600),
                        1,
                        null,
                    ),
                    2,
                    at.minusSeconds(10800),
                    publishedAt = at.minusSeconds(9000),
                    drawnAt = at,
                )
            val win =
                Notification(
                    "winner",
                    null,
                    NotificationType.LOTTERY_WON,
                    NotificationSubject(SubjectType.LOTTERY, lottery.id),
                    null,
                    reservation.createdAt,
                )
            val loss =
                win.copy(
                    id = "loser",
                    type = NotificationType.LOTTERY_LOST,
                    subject = NotificationSubject(SubjectType.LOTTERY, "other-draw"),
                )
            val booked =
                win.copy(
                    id = "booked",
                    type = NotificationType.RESERVATION_CONFIRMED,
                    subject = NotificationSubject(SubjectType.RESERVATION, reservation.id),
                )
            val content =
                InboxUiState(
                    InboxSourceState.READY,
                    listOf(
                        InboxItem(win, null, NotificationSubjectContent.LotteryContent(lottery)),
                        InboxItem(
                            loss,
                            reservation.createdAt,
                            NotificationSubjectContent.LotteryContent(
                                lottery.copy(id = "other-draw", title = "Summer camping"),
                            ),
                        ),
                        InboxItem(
                            booked,
                            null,
                            NotificationSubjectContent.ReservationContent(reservation),
                        ),
                    ),
                    orderingPending = false,
                    unreadCountPending = false,
                    unreadCount = 2,
                )
            sequenceOf(
                content,
                content.copy(readFailures = setOf(win.id)),
                content.copy(
                    items =
                        listOf(
                            content.items.first().copy(
                                subject = NotificationSubjectContent.Failure,
                            ),
                        ),
                ),
                content.copy(
                    unavailable =
                        listOf(
                            UnavailableInboxReceipt(
                                InboxReceipt("unresolved", null),
                                InboxEventStatus.FAILURE,
                            ),
                        ),
                ),
                InboxUiState(source = InboxSourceState.READY),
                InboxUiState(),
                InboxUiState(source = InboxSourceState.FAILURE),
            )
        }
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
    CampsbridgeTheme { InboxScreen(state, {}, {}, {}, {}) }
}
