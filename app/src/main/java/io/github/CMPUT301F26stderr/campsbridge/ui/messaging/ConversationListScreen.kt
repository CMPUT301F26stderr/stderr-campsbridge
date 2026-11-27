/*
 * Lists a participant's reservation conversations, newest first, with unread counts.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.common.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.InlineNotice
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.logic.ConversationSummary
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Shows the Messages tab's source, empty and partial-failure outcomes inside the Inbox frame.
 * @param state Current list outcome.
 * @param onOpen Opens a reservation's existing conversation.
 * @param onRetry Retries the reservation source or the failed conversations.
 */
@Composable
fun ConversationListScreen(
    state: ConversationListUiState,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        ConversationListUiState.Loading -> {
            LoadingContent(stringResource(R.string.conversation_list_loading))
        }

        ConversationListUiState.Failure -> {
            FailureContent(stringResource(R.string.conversation_list_failed), onRetry)
        }

        is ConversationListUiState.Content -> {
            ConversationListContent(state, onOpen, onRetry)
        }
    }
}

/** Lists loaded conversations, keeping failed ones retryable without hiding the rest.
 * @param state Loaded reservations and conversations.
 * @param onOpen Opens a reservation's existing conversation.
 * @param onRetry Retries the failed conversations.
 */
@Composable
private fun ConversationListContent(
    state: ConversationListUiState.Content,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(top = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        if (state.failedReservationIds.isNotEmpty()) {
            InlineNotice(
                stringResource(R.string.conversation_list_partial_title),
                stringResource(R.string.conversation_list_partial_body),
                action = { TextButton(onRetry) { Text(stringResource(R.string.conversation_list_retry)) } },
            )
        }
        if (state.rows.isEmpty()) {
            if (state.loading) {
                LoadingContent(stringResource(R.string.conversation_list_loading))
            } else if (state.failedReservationIds.isEmpty()) {
                EmptyContent(stringResource(R.string.conversation_list_empty))
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Spacing.small)) {
            items(state.rows, key = { it.reservation.id }) { row ->
                ConversationRow(row, state.names[row.reservation.id], onOpen)
            }
        }
    }
}

/** One conversation: other person and campsite, stay dates, newest message and a numbered unread badge.
 * @param row Conversation summary.
 * @param otherName The other participant's name, or null while unknown.
 * @param onOpen Opens this reservation's conversation.
 */
@Composable
private fun ConversationRow(
    row: ConversationSummary,
    otherName: String?,
    onOpen: (String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val reservation = row.reservation
    val unread = row.unread > 0
    val unreadText = pluralStringResource(R.plurals.conversation_list_unread, row.unread, row.unread)
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("conversation-row-${reservation.id}")
                .clickable(role = Role.Button) { onOpen(reservation.id) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = {
            Text(
                otherName?.let {
                    stringResource(
                        R.string.conversation_list_row_title,
                        it,
                        reservation.bookedCampsite.name,
                    )
                }
                    ?: reservation.bookedCampsite.name,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(
                    stringResource(
                        R.string.conversation_stay,
                        formatDate(reservation.checkInDate, locale),
                        formatDate(reservation.checkOutDate, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (row.latestIsMine) {
                        stringResource(R.string.conversation_list_you, row.latest.body)
                    } else {
                        row.latest.body
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        // The time sits above the title, as in the approved Inbox Messages design, leaving the title room.
        overlineContent = { Text(formatDateTime(row.latest.createdAt, locale)) },
        trailingContent =
            if (unread) {
                // The badge shows the number and announces it, so unread isn't conveyed by colour alone.
                { Badge(Modifier.semantics { contentDescription = unreadText }) { Text(row.unread.toString()) } }
            } else {
                null
            },
    )
}
