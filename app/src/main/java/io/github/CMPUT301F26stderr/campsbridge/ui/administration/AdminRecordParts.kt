/*
 * Shared pieces for admin records: monospace record IDs, status labels and an identity header.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AccountAvatar
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Shows a stored identifier in the heading monospace face, so it reads as a record key rather than a name.
 * @param id Stored identifier, shown exactly.
 * @param modifier Caller layout.
 */
@Composable
fun RecordId(
    id: String,
    modifier: Modifier = Modifier,
) {
    Text(
        id,
        modifier,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MaterialTheme.typography.titleLarge.fontFamily),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A small pill naming a record's state. The words carry the meaning; colour only reinforces it.
 * @param text Localized state, such as Active or Removed.
 * @param alert Whether the state needs attention, which uses the error container colours.
 */
@Composable
fun StatusLabel(
    text: String,
    alert: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (alert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor =
            if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = Spacing.small, vertical = Spacing.small / 2),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Card that opens an inspection screen with who or what is being inspected: picture, name, stored ID and state.
 * @param title Record name, or the screen's fallback when the record didn't load.
 * @param id Stored identifier of the inspected record.
 * @param leading Avatar or thumbnail beside the name.
 * @param status Optional state pill under the ID.
 * @param modifier Caller layout.
 */
@Composable
fun AdminRecordHeader(
    title: String,
    id: String,
    leading: @Composable () -> Unit,
    status: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.small / 2)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
                RecordId(id)
                status?.invoke()
            }
        }
    }
}

/**
 * Names what a notification is about in words an administrator reads, instead of the stored enum.
 * @param type Stored subject kind.
 * @return Localized subject kind.
 */
@Composable
fun subjectTypeLabel(type: SubjectType): String =
    stringResource(
        when (type) {
            SubjectType.LOTTERY -> R.string.log_subject_lottery
            SubjectType.RESERVATION -> R.string.log_subject_reservation
            SubjectType.MESSAGE -> R.string.log_subject_message
        },
    )

/** Shows the header with an avatar fallback, a stored ID and an attention state. */
@Preview(showBackground = true, widthDp = 390)
@Composable
private fun AdminRecordHeaderPreview() {
    CampsbridgeTheme {
        Column(Modifier.padding(Spacing.medium)) {
            AdminRecordHeader(
                "Jordan Lee",
                "preview-owner",
                { AccountAvatar(null, "Jordan Lee") },
                { StatusLabel(stringResource(R.string.admin_account_removed), alert = true) },
            )
        }
    }
}
