package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationEntry
import io.github.CMPUT301F26stderr.campsbridge.ui.app.NotificationEntryFeedback
import io.github.CMPUT301F26stderr.campsbridge.ui.app.NotificationEntryUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Opens a resolved root tap through the actual ready account's registered graph.
 * @param controller Account-scoped existing navigation host.
 * @param session Actual confirmed account/workspace and selection progress.
 * @param state Root tap outcome; ordinary arrival never creates one.
 * @param onSelectWorkspace Root-owned persisted capability selection.
 * @param onOpenInbox Establishes the target workspace's primary Back destination.
 * @param onRetry Explicit event/context recovery.
 * @param onDismiss Retires pending work without shared-data mutation.
 * @param onConsume Acknowledges only an actually opened saved graph/identity.
 */
@Composable
internal fun NotificationEntryNavigation(
    controller: NavHostController,
    session: SessionState.Ready,
    state: NotificationEntryUiState,
    onSelectWorkspace: (Role) -> Unit,
    onOpenInbox: (Role) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onConsume: (NotificationEntry) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val target = state as? NotificationEntryUiState.Ready
    var attemptedEvent by rememberSaveable { mutableStateOf<String?>(null) }
    val selectionFailed =
        target != null && attemptedEvent == target.entry.eventId &&
            !session.isSelectingWorkspace && session.workspace != target.workspace

    LaunchedEffect(target, session, lifecycle, attemptedEvent) {
        if (target == null || lifecycleOwner.lifecycle.currentState != Lifecycle.State.RESUMED ||
            session.account.id != target.entry.accountId ||
            target.workspace !in session.account.roles || session.isSelectingWorkspace
        ) {
            return@LaunchedEffect
        }
        if (session.workspace != target.workspace) {
            if (attemptedEvent != target.entry.eventId) {
                attemptedEvent = target.entry.eventId
                onSelectWorkspace(target.workspace)
            }
            return@LaunchedEffect
        }
        if (!controller.isNotificationTarget(target)) {
            onOpenInbox(target.workspace)
            controller.navigate(notificationTargetRoute(target)) { launchSingleTop = true }
        }
        if (controller.isNotificationTarget(target)) onConsume(target.entry)
    }
    NotificationEntryFeedback(
        state,
        selectionFailed,
        onRetry = { if (selectionFailed) attemptedEvent = null else onRetry() },
        onDismiss = onDismiss,
    )
}

/** Uses the same saved-ID destination builders as Inbox and ordinary feature entry.
 * @param target Confirmed saved relationship and actual actor workspace.
 */
private fun notificationTargetRoute(target: NotificationEntryUiState.Ready): String =
    when (target.entry.subject.type) {
        SubjectType.LOTTERY -> {
            AppDestination.lotteryDetail(
                target.entry.subject.id,
                if (target.workspace ==
                    Role.OWNER
                ) {
                    LotteryContext.OWNER
                } else {
                    LotteryContext.ENTRANT
                },
            )
        }

        SubjectType.RESERVATION -> {
            AppDestination.reservationDetail(
                target.entry.subject.id,
                if (target.workspace ==
                    Role.OWNER
                ) {
                    ReservationContext.OWNER
                } else {
                    ReservationContext.ENTRANT
                },
            )
        }

        SubjectType.MESSAGE -> {
            error("Unsupported notification target cannot become Ready")
        }
    }

/** Checks the current graph and decoded argument before acknowledgement or duplicate navigation.
 * @param target Original pending target; an unrelated older parent cannot satisfy it.
 */
private fun NavHostController.isNotificationTarget(
    target: NotificationEntryUiState.Ready,
): Boolean {
    val lottery = target.entry.subject.type == SubjectType.LOTTERY
    val pattern =
        if (lottery) {
            AppDestination.lotteryDetailPattern(
                if (target.workspace ==
                    Role.OWNER
                ) {
                    LotteryContext.OWNER
                } else {
                    LotteryContext.ENTRANT
                },
            )
        } else {
            AppDestination.reservationPattern(
                if (target.workspace ==
                    Role.OWNER
                ) {
                    ReservationContext.OWNER
                } else {
                    ReservationContext.ENTRANT
                },
            )
        }
    if (currentDestination?.hierarchy?.none { it.route == pattern } != false) return false
    val parent = getBackStackEntry(pattern)
    return parent.arguments?.getString(if (lottery) "lotteryId" else "reservationId") ==
        target.entry.subject.id
}
