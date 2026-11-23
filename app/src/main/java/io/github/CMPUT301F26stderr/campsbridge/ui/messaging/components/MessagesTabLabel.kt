/*
 * Inbox Messages tab label with a numbered unread badge.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Shows "Messages" and, when needed, the unread count; it's announced as one label, not by colour alone.
 * @param unread Unread messages across the reader's conversations.
 */
@Composable
fun MessagesTabLabel(unread: Int) {
    val label = stringResource(R.string.inbox_messages)
    val description =
        if (unread > 0) pluralStringResource(R.plurals.inbox_messages_unread, unread, unread) else label
    Row(
        Modifier.testTag("inbox-messages-tab").clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        if (unread > 0) Badge { Text(unread.toString()) }
    }
}
