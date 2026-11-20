package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppSearchField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components.MessagePresentation
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components.MessageRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Lists actual saved messages and keeps local filtering separate from linked-read recovery.
 * @param state Primary source and filtered saved rows.
 * @param onQuery Retains raw local search input.
 * @param onRetry Retries the failed primary source.
 * @param onRetryContext Retries one failed linked read.
 * @param onOpen Opens the original saved message identity.
 * @param listState Caller-owned retained scroll.
 */
@Composable
fun MessageLogScreen(
    state: MessageLogUiState,
    onQuery: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryContext: (String) -> Unit,
    onOpen: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        AppSearchField(state.query, onQuery, stringResource(R.string.message_log_search))
        when (state.read) {
            MessageLogRead.LOADING -> {
                LoadingContent()
            }

            MessageLogRead.FAILURE -> {
                FailureContent(stringResource(R.string.message_log_failed), onRetry)
            }

            MessageLogRead.READY -> {
                if (state.rows.isEmpty()) {
                    EmptyContent(stringResource(R.string.message_log_empty))
                } else {
                    LazyColumn(Modifier.weight(1f), state = listState) {
                        items(state.rows, key = { it.message.id }) { row ->
                            Surface(onClick = {
                                onOpen(row.message.id)
                            }, modifier = Modifier.fillMaxWidth().testTag("admin-message-${row.message.id}")) {
                                Column(Modifier.padding(vertical = Spacing.medium)) {
                                    MessageLogContent(row, { onRetryContext(row.message.id) })
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** Displays immutable content and descriptive relationship facts without participant actions.
 * @param row Saved message and independently resolved context.
 * @param onRetryContext Retries only failed descriptive reads.
 */
@Composable
fun MessageLogContent(
    row: MessageLogRow,
    onRetryContext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        MessageRow(
            row.message,
            row.senderLabel ?: row.message.senderId,
            MessagePresentation.ADMIN,
            recipientLabel =
                row.recipientLabel ?: row.recipientId ?: stringResource(R.string.message_log_recipient_unavailable),
        )
        Text(
            stringResource(
                when (row.direction) {
                    MessageLogDirection.OWNER_TO_ENTRANT -> R.string.message_log_owner_direction
                    MessageLogDirection.ENTRANT_TO_OWNER -> R.string.message_log_entrant_direction
                    MessageLogDirection.UNAVAILABLE -> R.string.message_log_direction_unavailable
                },
            ),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            stringResource(R.string.message_log_reservation, row.message.reservationId),
            style = MaterialTheme.typography.bodySmall,
        )
        row.reservation?.let { stay ->
            Text(stay.bookedCampsite.name, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.message_log_dates, stay.checkInDate.toString(), stay.checkOutDate.toString()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (row.isLoading) {
            LoadingContent(stringResource(R.string.message_log_context_loading))
        } else if (row.contextFailed) {
            FailureContent(stringResource(R.string.message_log_context_failed), onRetryContext)
        }
    }
}

/** Shows ordered saved reservation history or an explicit primary-source outcome.
 * @param row Original saved record and any descriptive facts already resolved.
 * @param read Primary message observation outcome.
 * @param id Original message identity, independent of notification event IDs.
 * @param onRetry Retries the primary observation.
 * @param onRetryContext Retries one history record's failed linked facts.
 * @param onBack Returns to the retained caller.
 * @param history Selected reservation's saved messages, oldest first and independent of search.
 */
@Composable
fun MessageLogDetailScreen(
    row: MessageLogRow?,
    read: MessageLogRead,
    id: String,
    onRetry: () -> Unit,
    onRetryContext: (String) -> Unit,
    onBack: () -> Unit,
    history: List<MessageLogRow> = listOfNotNull(row),
) {
    AppScreen(stringResource(R.string.message_log_details), onBack = onBack) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when {
                row != null -> {
                    Text(stringResource(R.string.message_log_identity, id))
                    Text(
                        stringResource(R.string.message_log_history),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (read == MessageLogRead.FAILURE) {
                        FailureContent(stringResource(R.string.message_log_failed), onRetry)
                    } else if (read == MessageLogRead.LOADING) {
                        LoadingContent()
                    }
                    if (history.isEmpty()) {
                        EmptyContent(stringResource(R.string.message_log_history_empty))
                    }
                    for (entry in history) {
                        MessageLogContent(entry, { onRetryContext(entry.message.id) })
                        HorizontalDivider()
                    }
                }

                read == MessageLogRead.LOADING -> {
                    LoadingContent()
                }

                read == MessageLogRead.FAILURE -> {
                    FailureContent(stringResource(R.string.message_log_failed), onRetry)
                }

                else -> {
                    EmptyContent(stringResource(R.string.message_log_unavailable))
                }
            }
        }
    }
}
