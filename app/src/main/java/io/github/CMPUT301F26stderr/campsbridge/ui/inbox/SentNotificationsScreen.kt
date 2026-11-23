/*
 * Displays sender facts and independent linked-content recovery without recipient read state.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.notificationTitleResource
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppSearchField
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Displays sender facts and independent linked-content recovery without recipient read state.
 * @param state Original sender events and local query.
 * @param onQuery Retains local search text.
 * @param onOpen Opens only a resolved saved target by original event ID.
 * @param onRetry Retries the primary sender query.
 * @param onRetryItem Retries one linked read without changing its event.
 */
@Composable
fun SentNotificationsScreen(
    state: SentNotificationsUiState,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryItem: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        AppSearchField(state.query, onQuery, stringResource(R.string.sent_search), Modifier.testTag("sent-query"))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Spacing.medium)) {
            when (state.source) {
                InboxSourceState.LOADING -> {
                    item("loading") { LoadingContent() }
                }

                InboxSourceState.FAILURE -> {
                    item("failure") { FailureContent(stringResource(R.string.sent_failed), onRetry) }
                }

                InboxSourceState.READY -> {
                    if (state.items.isEmpty()) {
                        item("empty") {
                            EmptyContent(
                                stringResource(
                                    if (state.query.isBlank()) R.string.sent_empty else R.string.sent_no_matches,
                                ),
                            )
                        }
                    }
                }
            }
            items(state.items, key = { it.event.id }) { item -> SentNotificationRow(item, onOpen, onRetryItem) }
        }
    }
}

/** Uses the shared event titles, saved context and date helper without an Inbox receipt model.
 * @param item Original immutable event and separately read subject.
 * @param onOpen Explicit resolved target selection.
 * @param onRetry Explicit read-only context recovery.
 */
@Composable
private fun SentNotificationRow(
    item: SentNotificationItem,
    onOpen: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    val event = item.event
    val title =
        event.announcement?.title ?: stringResource(
            if (event.type == NotificationType.NEW_MESSAGE) {
                R.string.sent_message
            } else {
                notificationTitleResource(event.type)
            },
        )
    val target = item.target
    val modifier =
        Modifier
            .fillMaxWidth()
            .testTag("sent-event-${event.id}")
            .then(
                if (target != null &&
                    !item.loading
                ) {
                    Modifier.clickable(role = Role.Button) { onOpen(event.id) }
                } else {
                    Modifier
                },
            )
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = { Text(title) },
        trailingContent =
            if (target != null && !item.loading) {
                { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
            } else {
                null
            },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                event.announcement?.let { Text(it.body) }
                when (val subject = item.subject) {
                    is NotificationSubjectContent.LotteryContent -> {
                        Text(subject.lottery.title)
                    }

                    is NotificationSubjectContent.MessageContent -> {
                        Text(subject.reservation.bookedCampsite.name)
                        Text(subject.message.body)
                    }

                    is NotificationSubjectContent.ReservationContent -> {
                        Text(subject.reservation.bookedCampsite.name)
                    }

                    NotificationSubjectContent.Failure -> {
                        Text(stringResource(R.string.inbox_context_failed))
                    }

                    NotificationSubjectContent.Unavailable, NotificationSubjectContent.Unsupported -> {
                        Text(stringResource(R.string.inbox_context_unavailable))
                    }

                    null -> {
                        Unit
                    }
                }
                Text(
                    formatDateTime(event.createdAt, LocalConfiguration.current.locales[0]),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (item.loading) {
                    LoadingContent()
                } else if (item.subject == NotificationSubjectContent.Failure ||
                    item.subject == NotificationSubjectContent.Unavailable
                ) {
                    TextButton({ onRetry(event.id) }) { Text(stringResource(R.string.inbox_retry_item)) }
                }
            }
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
