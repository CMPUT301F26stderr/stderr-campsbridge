package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

@Composable
fun InboxRoute(
    model: InboxViewModel,
    workspace: Role,
    onOpenLottery: (String, LotteryContext) -> Unit,
    onOpenReservation: (String, ReservationContext) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val content = state as? InboxUiState.Content ?: return@LaunchedEffect
        val opening = content.openingItem ?: return@LaunchedEffect
        model.consumeOpen()
        val context = if (workspace == Role.OWNER) LotteryContext.OWNER else LotteryContext.ENTRANT
        when (val subject = opening.subject) {
            is NotificationSubjectContent.LotteryContent -> {
                onOpenLottery(subject.lottery.id, context)
            }
            is NotificationSubjectContent.ReservationContent -> {
                val resContext = if (subject.reservation.entrantId == model.accountId) {
                    ReservationContext.ENTRANT
                } else {
                    ReservationContext.OWNER
                }
                onOpenReservation(subject.reservation.id, resContext)
            }
            else -> {}
        }
    }

    InboxScreen(
        state = state,
        onOpenItem = model::open,
        onRetry = model::retry,
        onRetryMarkRead = model::retryMarkRead,
        onDismissSaveFailure = model::dismissSaveFailure,
    )
}
