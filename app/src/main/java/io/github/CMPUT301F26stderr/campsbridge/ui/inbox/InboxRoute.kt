package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Connects the inbox ViewModel to the screen and handles navigation to saved lottery/reservation targets.
 * @param model Account inbox state and actions.
 * @param onOpenLottery Opens a lottery with the viewer's role on that lottery.
 * @param onOpenReservation Opens a reservation with the viewer's role on that stay.
 */
@Composable
fun InboxRoute(
    model: InboxViewModel,
    onOpenLottery: (String, LotteryContext) -> Unit,
    onOpenReservation: (String, ReservationContext) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val content = state as? InboxUiState.Content ?: return@LaunchedEffect
        val opening = content.openingItem ?: return@LaunchedEffect
        model.consumeOpen()
        when (val subject = opening.subject) {
            is NotificationSubjectContent.LotteryContent -> {
                val context =
                    if (subject.lottery.ownerId == model.accountId) {
                        LotteryContext.OWNER
                    } else {
                        LotteryContext.ENTRANT
                    }
                onOpenLottery(subject.lottery.id, context)
            }

            is NotificationSubjectContent.ReservationContent -> {
                val resContext =
                    if (subject.reservation.entrantId == model.accountId) {
                        ReservationContext.ENTRANT
                    } else {
                        ReservationContext.OWNER
                    }
                onOpenReservation(subject.reservation.id, resContext)
            }

            NotificationSubjectContent.Failure,
            NotificationSubjectContent.Unavailable,
            NotificationSubjectContent.Unsupported,
            -> {}
        }
    }

    InboxScreen(
        state = state,
        onOpenItem = model::open,
        onRetry = model::retry,
        onRetryMarkRead = model::retryMarkRead,
    )
}
