/*
 * Renders distinct loading, empty-result and failure feedback with caller-owned recovery actions.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Loading feedback distinct from an empty result.
 * @param message Localized status text.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LoadingContent(message: String = stringResource(R.string.loading)) {
    Column(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        LoadingIndicator()
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.padding(Spacing.medium), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(
                message,
                Modifier.semantics {
                    error(message)
                    liveRegion = LiveRegionMode.Polite
                },
            )
            TextButton(
                onClick = onRetry,
                enabled = retryEnabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) { Text(retryLabel) }
        }
    }
}
