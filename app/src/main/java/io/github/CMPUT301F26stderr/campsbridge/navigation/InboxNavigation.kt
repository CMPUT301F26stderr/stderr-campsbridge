package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Registers two workspace entries sharing one lazily acquired account-root receipt owner.
 * @param controller Current account's navigation host.
 * @param state Actual ready identity and workspace state.
 * @param onSelectWorkspace Explicit root capability selection.
 * @param ownerItems Registered owner peers, including this primary.
 * @param entrantItems Registered entrant peers, including this primary.
 * @param onSelectDestination Root-owned retained primary navigation.
 * @param factory Current account's receipt/content dependencies.
 */
internal fun NavGraphBuilder.inboxGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    ownerItems: List<WorkspaceNavigationItem>,
    entrantItems: List<WorkspaceNavigationItem>,
    onSelectDestination: (String) -> Unit,
    factory: ViewModelProvider.Factory,
) {
    for (workspace in listOf(Role.ENTRANT, Role.OWNER)) {
        val route = AppDestination.inbox(workspace)
        composable(route) { entry ->
            val accountParent =
                remember(entry) { controller.getBackStackEntry(controller.graph.id) }
            val model: InboxViewModel =
                viewModel(
                    viewModelStoreOwner = accountParent,
                    key = "account-inbox",
                    factory = factory,
                )
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                stringResource(R.string.inbox_title),
                if (workspace ==
                    Role.OWNER
                ) {
                    ownerItems
                } else {
                    entrantItems
                },
                route,
                onSelectDestination,
            ) {
                InboxRoute(model) { request ->
                    val target =
                        when (request.subject.type) {
                            SubjectType.LOTTERY -> {
                                AppDestination.lotteryDetail(
                                    request.subject.id,
                                    LotteryContext.ENTRANT,
                                )
                            }

                            SubjectType.RESERVATION -> {
                                val subject =
                                    model.state.value.items
                                        .firstOrNull {
                                            it.notification.id ==
                                                request.notificationId
                                        }?.subject as? NotificationSubjectContent.ReservationContent
                                val reservation = subject?.reservation
                                if (reservation != null && reservation.id == request.subject.id) {
                                    AppDestination.reservationDetail(
                                        reservation.id,
                                        if (reservation.entrantId ==
                                            state.account.id
                                        ) {
                                            ReservationContext.ENTRANT
                                        } else {
                                            ReservationContext.OWNER
                                        },
                                    )
                                } else {
                                    null
                                }
                            }

                            SubjectType.MESSAGE -> {
                                null
                            }
                        }
                    if (target != null) controller.navigate(target) { launchSingleTop = true }
                }
            }
        }
    }
}
