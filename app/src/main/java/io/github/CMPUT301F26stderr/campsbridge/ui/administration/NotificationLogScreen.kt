package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.notificationTitleResource
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceOption
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.DropdownField
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.ZoneOffset

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
    val focus = LocalFocusManager.current
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        OutlinedTextField(
            state.query,
            onQuery,
            Modifier.fillMaxWidth().testTag("notification-log-query"),
            label = { Text(stringResource(R.string.log_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        )
        DropdownField(
            stringResource(R.string.log_event_type),
            listOf(ChoiceOption("all", stringResource(R.string.log_all_types))) +
                NotificationType.entries.map { ChoiceOption(it.name, stringResource(notificationTitleResource(it))) },
            state.type?.name ?: "all",
            { key -> onType(NotificationType.entries.firstOrNull { it.name == key }) },
            Modifier.fillMaxWidth().testTag("notification-log-type"),
        )
        when (val source = state.events) {
            NotificationLogEvents.Loading -> {
                LoadingContent(stringResource(R.string.log_loading))
            }

            NotificationLogEvents.Failure -> {
                FailureContent(stringResource(R.string.log_failed), onRetry)
            }

            is NotificationLogEvents.Content -> {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(vertical = Spacing.small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    if (source.events.isEmpty()) {
                        item {
                            EmptyContent(
                                stringResource(
                                    if (state.query.isBlank() &&
                                        state.type == null
                                    ) {
                                        R.string.log_empty
                                    } else {
                                        R.string.log_no_matches
                                    },
                                ),
                            )
                        }
                    }
                    items(source.events, key = { it.id }) { event ->
                        OutlinedCard(
                            onClick = { onOpen(event.id) },
                            modifier = Modifier.fillMaxWidth().testTag("notification-log-event:${event.id}"),
                        ) {
                            Column(
                                Modifier.padding(Spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(Spacing.small),
                            ) {
                                Text(
                                    event.announcement?.title ?: stringResource(notificationTitleResource(event.type)),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (event.announcement != null) {
                                    Text(
                                        stringResource(notificationTitleResource(event.type)),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                Text(
                                    formatDateTime(event.createdAt, ZoneOffset.UTC, locale),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    event.senderId ?: stringResource(R.string.log_system_sender),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
