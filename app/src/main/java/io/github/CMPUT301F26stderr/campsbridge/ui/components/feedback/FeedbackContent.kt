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
 */
@Composable
fun FailureContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(message)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
