package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen

/** Keeps the local example reachable while this feature is pending.
 * @param session Current task's local graph.
 * @param id Saved reservation identity, retained for the future reader.
 * @param onBack Returns to the task catalogue.
 */
@Composable
internal fun SavedTaskReservation(
    session: TaskExampleSession,
    id: String,
    onBack: () -> Unit,
) {
    AppScreen(stringResource(session.example.title), onBack = onBack) {
        EmptyContent(stringResource(R.string.feature_pending))
    }
}

/** Keeps the local example reachable while this feature is pending.
 * @param session Current task's local graph.
 * @param onBack Returns to the task catalogue.
 */
@Composable
internal fun ReservationsTaskExample(
    session: TaskExampleSession,
    onBack: () -> Unit,
) {
    AppScreen(stringResource(session.example.title), onBack = onBack) {
        EmptyContent(stringResource(R.string.feature_pending))
    }
}
