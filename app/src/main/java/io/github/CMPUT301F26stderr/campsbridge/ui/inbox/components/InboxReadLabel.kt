/*
 * Shows actual receipt state through text and emphasis, without marking it read.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R

/** Shows actual receipt state through text and emphasis, without marking it read.
 * @param unread Whether the original receipt has no acknowledgement timestamp.
 */
@Composable
internal fun InboxReadLabel(unread: Boolean) {
    Text(
        stringResource(if (unread) R.string.inbox_unread else R.string.inbox_read),
        style = MaterialTheme.typography.labelMedium,
        color =
            if (unread) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    )
}
