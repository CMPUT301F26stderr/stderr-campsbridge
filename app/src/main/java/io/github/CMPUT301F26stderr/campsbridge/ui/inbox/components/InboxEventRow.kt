/*
 * One ordinary Material row retaining actual event identity/time through context failures.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.notificationTitleResource
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxItem
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.ZoneId

/** One ordinary Material row retaining actual event identity/time through context failures.
 * @param item Loaded immutable event and original receipt state.
 * @param readFailed Separate first-read acknowledgement failure.
 * @param onOpen Explicit supported row selection.
 * @param onRetryItem Retries only this context.
 * @param onRetryRead Retries this receipt write without navigating.
 */
@Composable
internal fun InboxEventRow(
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
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                Modifier.padding(Spacing.medium),
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
    }
}
