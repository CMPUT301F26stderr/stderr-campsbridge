package io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Distinguishes participant message direction from read-only administrative inspection. */
enum class MessagePresentation { SENT, RECEIVED, ADMIN }

/** Displays a saved message without sending or acknowledging it.
 * @param message Authoritative saved message.
 * @param senderLabel Resolved sender name or an explicit unavailable label.
 * @param presentation Participant direction or administrative presentation.
 * @param modifier Caller layout.
 * @param recipientLabel Optional resolved recipient for administrative inspection.
 */
@Composable
fun MessageRow(
    message: Message,
    senderLabel: String,
    presentation: MessagePresentation,
    modifier: Modifier = Modifier,
    recipientLabel: String? = null,
) {
    val outgoing = presentation == MessagePresentation.SENT
    Column(modifier.fillMaxWidth(), horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
        Column(
            Modifier.fillMaxWidth(if (presentation == MessagePresentation.ADMIN) 1f else 0.9f),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(senderLabel, style = MaterialTheme.typography.labelLarge)
                if (presentation != MessagePresentation.ADMIN) {
                    Text(
                        stringResource(if (outgoing) R.string.message_sent else R.string.message_received),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (presentation == MessagePresentation.ADMIN && recipientLabel != null) {
                Text(
                    stringResource(R.string.message_recipient, recipientLabel),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color =
                    if (outgoing) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                contentColor =
                    if (outgoing) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            ) {
                Text(message.body, Modifier.padding(Spacing.medium), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                formatDateTime(message.createdAt, LocalConfiguration.current.locales[0]),
                Modifier.align(Alignment.End),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
