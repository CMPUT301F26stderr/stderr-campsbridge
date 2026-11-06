/*
 * Displays recorded Inbox facts without acknowledging receipts or inferring Android alerts.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceDelivery
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceMember
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceUnavailable
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.notificationTitleResource
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.ZoneId

/** Displays recorded Inbox facts without acknowledging receipts or inferring Android alerts.
 * @param state One selected event's audience outcome.
 * @param onRetry Reloads the current audience without changing its event or receipts.
 * @param event Independently loaded event header for this exact audience identity.
 */
@Composable
fun NotificationAudienceScreen(
    state: NotificationLogAudienceState,
    onRetry: () -> Unit,
    event: Notification? = null,
) {
    LazyColumn(
        Modifier.testTag("admin-audience-records"),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        item("header") {
            SummarySection(
                event?.let { stringResource(notificationTitleResource(it.type)) }
                    ?: stringResource(R.string.log_event_details),
            ) {
                state.id?.let { DetailRow(stringResource(R.string.log_event_id), it) }
            }
        }
        when (val result = state) {
            NotificationLogAudienceState.Idle, is NotificationLogAudienceState.Loading -> {
                item("loading") {
                    LoadingContent(stringResource(R.string.log_audience_loading))
                }
            }

            is NotificationLogAudienceState.Failure -> {
                item("failure") {
                    FailureContent(stringResource(R.string.log_audience_failed), onRetry)
                }
            }

            is NotificationLogAudienceState.Content -> {
                when (val audience = result.audience) {
                    NotificationAudience.NoRecordedInformation -> {
                        item("unknown") {
                            EmptyContent(stringResource(R.string.log_audience_no_information))
                        }
                    }

                    is NotificationAudience.Recorded -> {
                        if (audience.members.isEmpty()) {
                            item("empty") {
                                EmptyContent(stringResource(R.string.log_audience_empty))
                            }
                        }
                        items(
                            audience.members,
                            key = { "recipient:${it.accountId}" },
                        ) { member ->
                            NotificationLogRecipientRow(member)
                        }
                    }

                    is NotificationAudience.Unavailable -> {
                        item("unavailable") {
                            EmptyContent(
                                stringResource(
                                    when (audience.reason) {
                                        NotificationAudienceUnavailable.EVENT_MISSING -> {
                                            R.string.log_event_unavailable
                                        }

                                        NotificationAudienceUnavailable.SUBJECT_MISSING -> {
                                            R.string.log_audience_subject_missing
                                        }

                                        NotificationAudienceUnavailable.UNSUPPORTED_SUBJECT -> {
                                            R.string.log_audience_unsupported
                                        }
                                    },
                                ),
                            )
                        }
                    }
                }
                val unsupported =
                    result.audience ==
                        NotificationAudience.Unavailable(
                            NotificationAudienceUnavailable.UNSUPPORTED_SUBJECT,
                        )
                if (!unsupported) {
                    item("refresh") {
                        TextButton(onRetry) {
                            Text(stringResource(R.string.log_audience_refresh))
                        }
                    }
                }
            }
        }
        item("meaning") {
            Text(
                stringResource(R.string.log_delivery_explanation),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Presents the original recipient identity and actual delivery/read facts; names are optional.
 * @param member Confirmed assembled membership from the existing repository.
 */
@Composable
private fun NotificationLogRecipientRow(member: NotificationAudienceMember) {
    Column(
        Modifier.fillMaxWidth().testTag("admin-audience-recipient:${member.accountId}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Column(
            Modifier.padding(vertical = Spacing.small),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(member.accountId, style = MaterialTheme.typography.titleSmall)
            val readAt = member.readAt
            when {
                member.delivery == NotificationAudienceDelivery.PENDING -> {
                    Text(stringResource(R.string.log_delivery_pending))
                }

                readAt == null -> {
                    Text(stringResource(R.string.log_delivery_confirmed))
                }

                else -> {
                    Text(
                        stringResource(
                            R.string.log_read_at,
                            formatDateTime(
                                readAt,
                                ZoneId.systemDefault(),
                                LocalConfiguration.current.locales[0],
                            ),
                        ),
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

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
    val detail by model.detail.collectAsStateWithLifecycle()
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
        event = detail.event?.takeIf { it.id == notificationId },
    )
}
