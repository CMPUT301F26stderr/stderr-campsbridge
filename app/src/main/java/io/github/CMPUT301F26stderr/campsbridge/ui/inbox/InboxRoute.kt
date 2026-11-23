/*
 * Delivers saved-target navigation only from the resumed Inbox owner.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Delivers saved-target navigation only from the resumed Inbox owner.
 * @param model Account inbox state and actions.
 * @param onOpenLottery Opens a lottery with the viewer's role on that lottery.
 * @param onOpenReservation Opens a reservation with the viewer's role on that stay.
 * @param onOpenConversation Opens the resolved saved conversation when the host supplies this path.
 */
@Composable
fun InboxRoute(
    model: InboxViewModel,
    onOpenLottery: (String, LotteryContext) -> Unit,
    onOpenReservation: (String, ReservationContext) -> Unit,
    onOpenConversation: (String, ReservationContext) -> Unit = { _, _ -> },
) {
    val state by model.state.collectAsStateWithLifecycle()

    val pending by model.pendingOpen.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val lifecycle by owner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(pending, lifecycle) {
        if (lifecycle != Lifecycle.State.RESUMED || owner.lifecycle.currentState != Lifecycle.State.RESUMED) {
            return@LaunchedEffect
        }
        val request = pending ?: return@LaunchedEffect
        if (model.pendingOpen.value != request) return@LaunchedEffect
        val opening =
            model.state.value.items
                .firstOrNull { it.notification.id == request.notificationId }
                ?: return@LaunchedEffect
        when (val subject = opening.subject) {
            is NotificationSubjectContent.LotteryContent -> {
                val context =
                    if (model.accountId in subject.lottery.participantOwnerIds) {
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

            is NotificationSubjectContent.MessageContent -> {
                val context =
                    if (subject.reservation.entrantId == model.accountId) {
                        ReservationContext.ENTRANT
                    } else {
                        ReservationContext.OWNER
                    }
                onOpenConversation(subject.reservation.id, context)
            }

            NotificationSubjectContent.Failure,
            NotificationSubjectContent.Unavailable,
            NotificationSubjectContent.Unsupported,
            -> {}
        }
        model.acknowledgeOpen(request.notificationId)
    }

    InboxScreen(
        state = state,
        onOpen = {
            if (owner.lifecycle.currentState == Lifecycle.State.RESUMED) model.openItem(it)
        },
        onRetrySource = model::retry,
        onRetryRead = model::retryReadAcknowledgement,
        onRetryItem = model::retryItem,
    )
}
