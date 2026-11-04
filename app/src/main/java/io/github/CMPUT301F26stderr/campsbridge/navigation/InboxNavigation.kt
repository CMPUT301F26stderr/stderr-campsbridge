package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxViewModel

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
    for (role in listOf(Role.ENTRANT, Role.OWNER)) {
        val route = AppDestination.inbox(role)
        composable(route) { entry ->
            val rootEntry = remember(entry) { controller.getBackStackEntry(controller.graph.id) }
            val model: InboxViewModel = viewModel(viewModelStoreOwner = rootEntry, factory = factory)
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                stringResource(R.string.inbox_title),
                if (role == Role.OWNER) ownerItems else entrantItems,
                route,
                onSelectDestination,
            ) {
                InboxRoute(
                    model = model,
                    workspace = role,
                    onOpenLottery = { lotteryId, ctx ->
                        controller.navigate(AppDestination.lotteryDetail(lotteryId, ctx))
                    },
                    onOpenReservation = { resId, ctx ->
                        controller.navigate(AppDestination.reservationDetail(resId, ctx))
                    },
                )
            }
        }
    }
}
