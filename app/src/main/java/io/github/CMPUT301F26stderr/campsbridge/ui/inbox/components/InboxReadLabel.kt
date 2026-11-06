/*
 * Shows actual receipt state through text and emphasis, without marking it read.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R

/** Shows actual receipt state through text and emphasis, without marking it read.
 * @param unread Whether the original receipt has no acknowledgement timestamp.
 */
@Composable
internal fun InboxReadLabel(unread: Boolean) {
    Surface(
        shape = CircleShape,
        color =
            if (unread) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        contentColor =
            if (unread) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    ) {
        Text(
            stringResource(if (unread) R.string.inbox_unread else R.string.inbox_read),
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
