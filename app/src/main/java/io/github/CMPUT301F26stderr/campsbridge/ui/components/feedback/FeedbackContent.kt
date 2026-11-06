package io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Loading feedback distinct from an empty result.
 * @param message Localized status text.
 */
@Composable
fun LoadingContent(message: String = stringResource(R.string.loading)) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        CircularProgressIndicator()
        Text(message)
    }
}

/**
 * A successful result containing no content.
 * @param message Localized explanation.
 */
@Composable
fun EmptyContent(message: String) {
    Text(message)
}

/**
 * Recoverable failure without discarding sibling content or user input.
 * @param message Localized explanation, not a raw SDK exception.
 * @param onRetry Explicit retry action.
 * @param retryEnabled Whether the owning feature currently permits retrying.
 * @param retryLabel Distinguishes independent retry actions when a screen has several failed reads.
 */
@Composable
fun FailureContent(
    message: String,
    onRetry: () -> Unit,
    retryEnabled: Boolean = true,
    retryLabel: String = stringResource(R.string.retry),
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(message)
        TextButton(
            onClick = onRetry,
            enabled = retryEnabled,
        ) { Text(retryLabel) }
    }
}
