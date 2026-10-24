package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen

/**
 * Presents startup loading/failure with an explicit retry.
 * @param failed Whether the initialization attempt failed.
 * @param onRetry Starts another initialization attempt.
 */
@Composable
fun StartupContent(
    failed: Boolean,
    onRetry: () -> Unit,
) {
    AppScreen(stringResource(R.string.app_name)) {
        if (failed) {
            FailureContent(stringResource(R.string.startup_failure), onRetry)
        } else {
            LoadingContent(stringResource(R.string.startup_loading))
        }
    }
}

/** Removed accounts have no active profile or mutation controls. */
@Composable
fun UnavailableAccountScreen() {
    AppScreen(stringResource(R.string.account_unavailable)) {
        androidx.compose.material3.Text(stringResource(R.string.account_unavailable_body))
    }
}
