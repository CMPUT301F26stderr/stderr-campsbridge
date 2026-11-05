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
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
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
import java.time.ZoneId

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
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        SummarySection(
            state.event?.let { stringResource(notificationTitleResource(it.type)) }
                ?: stringResource(R.string.log_event_details),
        ) {
            if (state.event == null) {
                state.id?.let { DetailRow(stringResource(R.string.log_event_id), it) }
            }
            state.event?.let { event ->
                DetailRow(stringResource(R.string.log_event_id), event.id)
                DetailRow(
                    stringResource(R.string.log_event_type),
                    stringResource(notificationTitleResource(event.type)),
                )
                DetailRow(
                    stringResource(R.string.log_sender),
                    event.senderId ?: stringResource(R.string.log_system_sender),
                )
                DetailRow(
                    stringResource(R.string.log_created),
                    formatDateTime(event.createdAt, ZoneId.systemDefault(), locale),
                )
                DetailRow(
                    stringResource(R.string.log_linked_type),
                    stringResource(
                        when (event.subject.type) {
                            SubjectType.LOTTERY -> R.string.log_lottery
                            SubjectType.RESERVATION -> R.string.log_reservation_subject
                            SubjectType.MESSAGE -> R.string.log_message_subject
                        },
                    ),
                )
                DetailRow(stringResource(R.string.log_linked_id), event.subject.id)
            }
        }
        if (state.event != null) NavigationRow(stringResource(R.string.log_recipients), onAudience)
        SummarySection(stringResource(R.string.log_message_content)) {
            state.event?.announcement?.let {
                Text(it.title, style = MaterialTheme.typography.titleSmall)
                Text(it.body)
            }
            when (val content = state.content) {
                null -> {
                    if (state.isLoading) LoadingContent(stringResource(R.string.log_detail_loading))
                }

                NotificationContent.Failure -> {
                    FailureContent(stringResource(R.string.log_detail_failed), onRetry)
                }

                NotificationContent.Unavailable -> {
                    Text(stringResource(R.string.log_event_unavailable))
                    TextButton(onRetry) { Text(stringResource(R.string.log_retry_details)) }
                }

                is NotificationContent.Loaded -> {
                    if (content.notification.announcement == null) {
                        Text(notificationText(LocalContext.current, content).body)
                    }
                    when (val subject = content.subject) {
                        is NotificationSubjectContent.LotteryContent -> {
                            DetailRow(stringResource(R.string.log_lottery), subject.lottery.title)
                        }

                        is NotificationSubjectContent.ReservationContent -> {
                            DetailRow(stringResource(R.string.log_reservation), subject.reservation.bookedCampsite.name)
                        }

                        NotificationSubjectContent.Unavailable -> {
                            Text(stringResource(R.string.inbox_context_unavailable))
                        }

                        NotificationSubjectContent.Unsupported -> {
                            Text(stringResource(R.string.inbox_context_unsupported))
                        }

                        NotificationSubjectContent.Failure -> {
                            Text(stringResource(R.string.inbox_context_failed))
                            TextButton(onRetry) { Text(stringResource(R.string.log_retry_details)) }
                        }
                    }
                }
            }
        }
    }
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
