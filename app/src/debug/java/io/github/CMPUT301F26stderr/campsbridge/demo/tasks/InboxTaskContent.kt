package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailRoute

/** Reads a real event target and acknowledges the local receipt through the actual Inbox owner.
 * @param session Current fixed event graph.
 * @param onBack Returns to the catalogue.
 */
@Composable
internal fun InboxTaskExample(
    session: TaskExampleSession,
    onBack: () -> Unit,
) {
    var lottery by rememberSaveable { mutableStateOf<String?>(null) }
    var reservation by rememberSaveable { mutableStateOf<String?>(null) }
    val lotteryId = lottery
    val reservationId = reservation
    when {
        reservationId != null -> {
            SavedTaskReservation(session, reservationId) { reservation = null }
        }

        lotteryId != null -> {
            LotteryDetailRoute(session.lottery(lotteryId), { lottery = null }, { lottery = null })
        }

        else -> {
            AppScreen(stringResource(R.string.inbox_title), onBack = onBack) {
                InboxRoute(
                    session.inbox(),
                    onOpenLottery = { id, _ -> lottery = id },
                    onOpenReservation = { id, _ -> reservation = id },
                )
            }
        }
    }
}
