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
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.notificationTitleResource
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing


@Composable
fun InboxScreen(
    state: InboxUiState,
    onOpenItem: (InboxItem) -> Unit,
    onRetry: () -> Unit,
    onRetryMarkRead: () -> Unit,
    onDismissSaveFailure: () -> Unit,
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
                        Column {
                            FailureContent("Could not refresh notifications.", onRetry)
                            Button(onClick = onRetry) { Text("Refresh") }
                        }
                    }
                }

                InboxUiState.Unavailable -> {
                    item { EmptyContent("No notifications yet.") }
                }

                is InboxUiState.Content -> {
                    if (state.saveFailed) {
                        item {
                            Column(Modifier.padding(Spacing.medium)) {
//                                Button(onClick = onRetry){
//                                    Text("Return to Inbox")
//                                }
                                Text("Could not mark this notification as read.")
//                                Text("Unread")        // Don't think we need this at all
                                Button(onClick = onRetryMarkRead) {
                                    Text("Retry read acknowledgement")
                                }
                            }
                        }
                    }
                    if (state.items.isEmpty() && state.unavailable.isEmpty()) {
                        item { EmptyContent("No notifications yet.") }
                    } else {
                        if (state.unavailable.isNotEmpty()) {
                            item {
                                Text("Unavailable notifications", style = MaterialTheme.typography.titleMedium)
                            }
                            items(state.unavailable, key = { it.receipt.notificationId }) { unavail ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.medium)
                                        .testTag("inbox-receipt-${unavail.receipt.notificationId}")
                                ) {
                                    Text("Notification unavailable", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            item {
                                Button(onClick = onRetry) { Text("Retry notification") }
                            }
                        }
                        items(state.items, key = { it.notification.id }) { item ->
                            val isUnsupported = item.subject == NotificationSubjectContent.Unsupported
                            if (isUnsupported) {
                                val title = stringResource(notificationTitleResource(item.notification.type))
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.medium)
                                        .testTag("inbox-event-${item.notification.id}")
                                ) {
                                    Text(title)
                                    Text("This notification has no supported destination in this build.")
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
        else -> ""
    }
    val readStatus = if (item.readAt != null) "Read" else "Unread"
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
