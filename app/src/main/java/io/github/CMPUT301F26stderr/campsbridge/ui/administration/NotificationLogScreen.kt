/*
 * Displays immutable event rows and parent-owned filters without acquiring repositories.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.notificationTitleResource
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppSearchField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceOption
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.DropdownField
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Displays immutable event rows and parent-owned filters without acquiring repositories.
 * @param state Current filters and all-event source outcome.
 * @param onQuery Retains user-entered search text.
 * @param onType Applies an exact event category or All.
 * @param onOpen Opens the original event ID.
 * @param onRetry Retries only the all-event observation.
 * @param listState Retained scroll position, measured only when actual event content is ready.
 */
@Composable
fun NotificationLogScreen(
    state: NotificationLogUiState,
    onQuery: (String) -> Unit,
    onType: (NotificationType?) -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        AppSearchField(
            state.query,
            onQuery,
            stringResource(R.string.log_search),
            Modifier.testTag("notification-log-query"),
        )
        DropdownField(
            stringResource(R.string.log_event_type),
            listOf(ChoiceOption("all", stringResource(R.string.log_all_types))) +
                NotificationType.entries.map { ChoiceOption(it.name, stringResource(notificationTitleResource(it))) },
            state.type?.name ?: "all",
            { id -> onType(NotificationType.entries.firstOrNull { it.name == id }) },
            Modifier.testTag("notification-log-type"),
        )
        LazyColumn(
            Modifier.weight(1f).testTag("admin-log-records"),
            state = listState,
            contentPadding = PaddingValues(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            when (val records = state.events) {
                NotificationLogEvents.Loading -> {
                    item("loading") {
                        LoadingContent(stringResource(R.string.log_loading))
                    }
                }

                NotificationLogEvents.Failure -> {
                    item("failure") {
                        FailureContent(stringResource(R.string.log_failed), onRetry)
                    }
                }

                is NotificationLogEvents.Content -> {
                    if (records.events.isEmpty()) {
                        item("empty") { EmptyContent(stringResource(R.string.log_empty)) }
                    }
                    items(records.events, key = { "event:${it.id}" }) { event ->
                        NotificationLogRow(event, { onOpen(event.id) })
                    }
                }
            }
        }
    }
}

/** Shows only original event facts; list presentation never fetches related records.
 * @param event Original immutable event.
 * @param onOpen Explicit row action owned by the route.
 */
@Composable
private fun NotificationLogRow(
    event: Notification,
    onOpen: () -> Unit,
) {
    OutlinedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("notification-log-event:${event.id}"),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(notificationTitleResource(event.type)),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            Text(
                formatDateTime(
                    event.createdAt,
                    LocalConfiguration.current.locales[0],
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.log_sender_summary,
                    event.senderId ?: stringResource(R.string.log_system_sender),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(event.subject.id, style = MaterialTheme.typography.bodySmall)
            Text(
                event.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
