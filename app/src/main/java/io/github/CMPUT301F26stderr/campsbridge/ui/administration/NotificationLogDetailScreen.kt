/*
 * Displays original event facts independently of linked-record availability.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.notificationText
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.notificationTitleResource
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Displays original event facts independently of linked-record availability.
 * @param state Actual selected identity, immutable facts and common context outcome.
 * @param onRetry Retries only the selected event/context reader.
 * @param onAudience Opens recorded recipients for this actual event.
 */
@Composable
fun NotificationLogDetailScreen(
    state: NotificationLogDetailUiState,
    onRetry: () -> Unit,
    onAudience: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        SummarySection(
            state.event?.let { stringResource(notificationTitleResource(it.type)) }
                ?: stringResource(R.string.log_metadata),
        ) {
            state.event?.let { NotificationEventFacts(it) }
            state.id?.let { DetailRow(stringResource(R.string.log_event_id), it) }
        }
        if (state.event != null) {
            NavigationRow(stringResource(R.string.log_recipients), onAudience)
        }
        SummarySection(stringResource(R.string.log_message_content)) {
            when (val result = state.content) {
                null -> {
                    if (state.isLoading) LoadingContent(stringResource(R.string.log_detail_loading))
                }

                NotificationContent.Failure -> {
                    FailureContent(
                        stringResource(R.string.log_detail_failed),
                        onRetry,
                    )
                }

                NotificationContent.Unavailable -> {
                    Text(stringResource(R.string.log_event_unavailable))
                    TextButton(onRetry) { Text(stringResource(R.string.log_retry_details)) }
                }

                is NotificationContent.Loaded -> {
                    val loaded = result
                    val copy = notificationText(LocalContext.current, loaded)
                    if (loaded.notification.announcement != null) {
                        Text(copy.title, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(copy.body)
                    if (loaded.subject == NotificationSubjectContent.Failure ||
                        loaded.subject == NotificationSubjectContent.Unavailable
                    ) {
                        // Saved announcement text survives unavailable current lottery context.
                        if (loaded.notification.announcement != null) {
                            Text(
                                stringResource(
                                    if (loaded.subject == NotificationSubjectContent.Failure) {
                                        R.string.inbox_context_failed
                                    } else {
                                        R.string.inbox_context_unavailable
                                    },
                                ),
                            )
                        }
                        TextButton(
                            onRetry,
                        ) { Text(stringResource(R.string.log_retry_details)) }
                    }
                }
            }
        }
    }
}

/** Renders known immutable event metadata without treating it as a successful content read.
 * @param event Actual observed or loaded event header.
 */
@Composable
private fun NotificationEventFacts(event: Notification) {
    DetailRow(
        stringResource(R.string.log_created),
        formatDateTime(
            event.createdAt,
            LocalConfiguration.current.locales[0],
        ),
    )
    DetailRow(
        stringResource(R.string.log_sender),
        event.senderId ?: stringResource(R.string.log_system_sender),
    )
    DetailRow(stringResource(R.string.log_linked_type), event.subject.type.name)
    DetailRow(stringResource(R.string.log_linked_id), event.subject.id)
}

/** Projects only this route's selected identity while the graph owns its reader lifetime.
 * @param model Shared list/detail/audience parent.
 * @param notificationId Actual decoded route argument.
 * @param onAudience Opens the registered same-ID audience child.
 */
@Composable
fun NotificationLogDetailRoute(
    model: NotificationLogViewModel,
    notificationId: String,
    onAudience: () -> Unit,
) {
    val detail by model.detail.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    NotificationLogDetailScreen(
        if (detail.id ==
            notificationId
        ) {
            detail
        } else {
            NotificationLogDetailUiState(id = notificationId, isLoading = true)
        },
        onRetry = {
            if (lifecycle.currentState == Lifecycle.State.RESUMED &&
                model.detail.value.id == notificationId
            ) {
                model.retryDetail()
            }
        },
        onAudience = {
            if (lifecycle.currentState == Lifecycle.State.RESUMED &&
                model.detail.value.event
                    ?.id == notificationId
            ) {
                onAudience()
            }
        },
    )
}
