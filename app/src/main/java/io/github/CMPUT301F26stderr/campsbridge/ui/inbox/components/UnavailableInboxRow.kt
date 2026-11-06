/*
 * Keeps missing-event history read-only without inventing its title or creation time.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxEventStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.UnavailableInboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Keeps missing-event history read-only without inventing its title or creation time.
 * @param item Original receipt and independent event read outcome.
 * @param onRetryItem Retries this receipt's exact immutable event identity.
 */
@Composable
internal fun UnavailableInboxRow(
    item: UnavailableInboxReceipt,
    onRetryItem: (String) -> Unit,
) {
    Column {
        Surface(
            Modifier.fillMaxWidth().testTag("inbox-receipt-${item.receipt.notificationId}"),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.padding(vertical = Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    stringResource(
                        when (item.status) {
                            InboxEventStatus.LOADING -> R.string.inbox_event_loading
                            InboxEventStatus.FAILURE -> R.string.inbox_event_failed
                            InboxEventStatus.UNAVAILABLE -> R.string.inbox_event_unavailable
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                InboxReadLabel(item.receipt.readAt == null)
                if (item.status != InboxEventStatus.LOADING) {
                    TextButton({ onRetryItem(item.receipt.notificationId) }) {
                        Text(stringResource(R.string.inbox_retry_item))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
