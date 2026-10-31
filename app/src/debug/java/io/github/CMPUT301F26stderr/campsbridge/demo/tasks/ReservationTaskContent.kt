package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailPage
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailRoute

/** Opens the real saved pages against the task's existing local aggregate.
 * @param session Current task's local graph.
 * @param id Actual saved reservation identity.
 * @param onBack Returns to the task catalogue.
 */
@Composable
internal fun SavedTaskReservation(
    session: TaskExampleSession,
    id: String,
    onBack: () -> Unit,
) {
    var page by rememberSaveable(id) { mutableStateOf(ReservationDetailPage.OVERVIEW) }
    ReservationDetailRoute(
        session.reservation(id),
        page,
        onBack = {
            if (page == ReservationDetailPage.OVERVIEW) {
                onBack()
            } else {
                page = ReservationDetailPage.OVERVIEW
            }
        },
        onPayment = { page = ReservationDetailPage.PAYMENT },
        onTerms = { page = ReservationDetailPage.TERMS },
    )
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
