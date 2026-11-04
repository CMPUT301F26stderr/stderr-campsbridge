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
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.notificationTitleResource
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Renders account inbox receipts, unread counts, unavailable items, and read acknowledgement actions.
 * @param state Current inbox presentation state.
 * @param onOpenItem Explicit user action to open an inbox item and mark it as read.
 * @param onRetry Retries inbox source observation or missing notification loading.
 * @param onRetryMarkRead Retries a failed read acknowledgement write.
 * @param modifier Caller layout modifier.
 */
@Composable
fun InboxScreen(
    state: InboxUiState,
    onOpenItem: (InboxItem) -> Unit,
    onRetry: () -> Unit,
    onRetryMarkRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when (state) {
                InboxUiState.Loading -> {
                    item { LoadingContent() }
                }

                InboxUiState.Failure -> {
                    item {
                        FailureContent(stringResource(R.string.inbox_source_failure), onRetry)
                    }
                }

                InboxUiState.Unavailable -> {
                    item { EmptyContent(stringResource(R.string.inbox_empty)) }
                }

                is InboxUiState.Content -> {
                    if (state.sourceFailed) {
                        item {
                            FailureContent(stringResource(R.string.inbox_source_failure), onRetry)
                        }
                    }
                    if (state.saveFailed) {
                        item {
                            Column(Modifier.padding(Spacing.medium)) {
                                Text(stringResource(R.string.inbox_ack_failure))
                                Text(stringResource(R.string.inbox_unread))
                                Button(onClick = onRetryMarkRead) {
                                    Text(stringResource(R.string.inbox_retry_ack))
                                }
                            }
                        }
                    }
                    if (state.items.isEmpty() && state.unavailable.isEmpty()) {
                        item { EmptyContent(stringResource(R.string.inbox_empty)) }
                    } else {
                        if (state.unavailable.isNotEmpty()) {
                            item {
                                Text(stringResource(R.string.inbox_unavailable_title), style = MaterialTheme.typography.titleMedium)
                            }
                            items(state.unavailable, key = { it.receipt.notificationId }) { unavail ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.medium)
                                        .testTag("inbox-receipt-${unavail.receipt.notificationId}")
                                ) {
                                    Text(stringResource(R.string.inbox_unavailable_row), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            item {
                                Button(onClick = onRetry) { Text(stringResource(R.string.inbox_retry_notification)) }
                            }
                        }
                        items(state.items, key = { it.notification.id }) { item ->
                            val isUnavailableSubject = item.subject is NotificationSubjectContent.Unavailable ||
                                item.subject is NotificationSubjectContent.Failure ||
                                item.subject is NotificationSubjectContent.Unsupported

                            val title = stringResource(notificationTitleResource(item.notification.type))
                            if (isUnavailableSubject) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.medium)
                                        .testTag("inbox-event-${item.notification.id}")
                                ) {
                                    Text(title)
                                    val body = when (item.subject) {
                                        NotificationSubjectContent.Unsupported -> stringResource(R.string.inbox_unsupported_body)
                                        NotificationSubjectContent.Unavailable -> stringResource(R.string.inbox_context_unavailable)
                                        NotificationSubjectContent.Failure -> stringResource(R.string.inbox_context_failed)
                                        is NotificationSubjectContent.LotteryContent,
                                        is NotificationSubjectContent.ReservationContent -> ""
                                    }
                                    Text(body)
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
    }
}

@Composable
private fun InboxItemRow(
    item: InboxItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(notificationTitleResource(item.notification.type))
    val subtitle = when (val subject = item.subject) {
        is NotificationSubjectContent.LotteryContent -> subject.lottery.title
        is NotificationSubjectContent.ReservationContent -> subject.reservation.bookedCampsite.name
        NotificationSubjectContent.Failure,
        NotificationSubjectContent.Unavailable,
        NotificationSubjectContent.Unsupported -> ""
    }
    val readStatus = if (item.readAt != null) stringResource(R.string.inbox_read) else stringResource(R.string.inbox_unread)
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
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

/** Previews the empty and populated inbox screens. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun InboxScreenPreview() {
    CampsbridgeTheme {
        InboxScreen(
            state = InboxUiState.Content(items = emptyList()),
            onOpenItem = {},
            onRetry = {},
            onRetryMarkRead = {},
        )
    }
}
