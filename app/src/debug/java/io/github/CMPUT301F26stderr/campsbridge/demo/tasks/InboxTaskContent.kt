package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen

/** Keeps the local example reachable while this feature is pending.
 * @param session Current task's local graph.
 * @param onBack Returns to the task catalogue.
 */
@Composable
internal fun InboxTaskExample(
    session: TaskExampleSession,
    onBack: () -> Unit,
) {
    AppScreen(stringResource(session.example.title), onBack = onBack) {
        EmptyContent(stringResource(R.string.feature_pending))
    }
}
