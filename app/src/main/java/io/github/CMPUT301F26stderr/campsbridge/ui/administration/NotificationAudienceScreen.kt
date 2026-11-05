package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceDelivery
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceMember
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceUnavailable
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.ZoneOffset

/** Displays recorded Inbox facts without acknowledging receipts or inferring Android alerts.
 * @param state One selected event's audience outcome.
 * @param onRetry Retries only a failed audience read.
 */
@Composable
fun NotificationAudienceScreen(
    state: NotificationLogAudienceState,
    onRetry: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        state.id?.let { id -> item("event-id") { DetailRow(stringResource(R.string.log_event_id), id) } }
        item("delivery-explanation") {
            Text(stringResource(R.string.log_delivery_explanation), style = MaterialTheme.typography.bodySmall)
        }
        when (state) {
            NotificationLogAudienceState.Idle, is NotificationLogAudienceState.Loading -> {
                item("loading") { LoadingContent(stringResource(R.string.log_audience_loading)) }
            }

            is NotificationLogAudienceState.Failure -> {
                item("failure") { FailureContent(stringResource(R.string.log_audience_failed), onRetry) }
            }

            is NotificationLogAudienceState.Content -> {
                when (val audience = state.audience) {
                    NotificationAudience.NoRecordedInformation -> {
                        item("no-information") { EmptyContent(stringResource(R.string.log_audience_no_information)) }
                    }

                    is NotificationAudience.Unavailable -> {
                        item("unavailable") { Text(audienceUnavailableText(audience.reason)) }
                    }

                    is NotificationAudience.Recorded -> {
                        if (audience.members.isEmpty()) {
                            item("empty") { EmptyContent(stringResource(R.string.log_audience_empty)) }
                        } else {
                            items(
                                audience.members,
                                key = { "recipient:${it.accountId}" },
                            ) { NotificationLogRecipientRow(it) }
                        }
                    }
                }
            }
        }
    }
}

/** Keeps each recorded identity and its actual delivery/read facts together.
 * @param member Captured recipient fact; missing profiles cannot remove it.
 */
@Composable
private fun NotificationLogRecipientRow(member: NotificationAudienceMember) {
    val locale = LocalConfiguration.current.locales[0]
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.medium), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(member.accountId, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    when (member.delivery) {
                        NotificationAudienceDelivery.PENDING -> R.string.log_delivery_pending
                        NotificationAudienceDelivery.DELIVERED -> R.string.log_delivery_confirmed
                    },
                ),
            )
            val readAt = member.readAt
            if (readAt !=
                null
            ) {
                DetailRow(stringResource(R.string.log_read_at), formatDateTime(readAt, ZoneOffset.UTC, locale))
            } else if (member.delivery ==
                NotificationAudienceDelivery.DELIVERED
            ) {
                Text(stringResource(R.string.log_unread))
            }
        }
    }
}

/** Explains an explicit unavailable audience without replacing it with an empty recipient list.
 * @param reason Actual repository outcome for the original event or subject.
 */
@Composable
private fun audienceUnavailableText(reason: NotificationAudienceUnavailable): String =
    stringResource(
        when (reason) {
            NotificationAudienceUnavailable.EVENT_MISSING -> R.string.log_event_unavailable
            NotificationAudienceUnavailable.SUBJECT_MISSING -> R.string.log_audience_subject_missing
            NotificationAudienceUnavailable.UNSUPPORTED_SUBJECT -> R.string.log_audience_unsupported
        },
    )

/** Shows only the route's audience identity; navigation binding owns query start and cleanup.
 * @param model Existing log parent.
 * @param notificationId Actual decoded event route argument.
 */
@Composable
fun NotificationLogAudienceRoute(
    model: NotificationLogViewModel,
    notificationId: String,
) {
    val audience by model.audience.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    NotificationAudienceScreen(
        if (audience.id == notificationId) audience else NotificationLogAudienceState.Loading(notificationId),
        onRetry = {
            if (lifecycle.currentState == Lifecycle.State.RESUMED &&
                model.detail.value.id == notificationId
            ) {
                model.retryAudience()
            }
        },
    )
}
