package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListViewModel

/** Registers implemented inventory and saved management routes; future editors add their own entries.
 * @param controller Account-owned navigation host.
 * @param state Confirmed session.
 * @param onSelectWorkspace Root-owned capability-limited selection.
 * @param navigationItems Registered owner peers.
 * @param onSelectDestination Primary tab selection.
 * @param listFactory Inventory constructor dependencies.
 * @param detailFactory Saved reader dependencies for an ID/context.
 */
internal fun NavGraphBuilder.campsiteGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    navigationItems: List<WorkspaceNavigationItem>,
    onSelectDestination: (String) -> Unit,
    listFactory: ViewModelProvider.Factory,
    detailFactory: (String, CampsiteDetailContext) -> ViewModelProvider.Factory,
) {
    composable(AppDestination.ownerCampsites()) {
        val model: CampsiteListViewModel = viewModel(factory = listFactory)
        WorkspaceScaffold(
            state,
            onSelectWorkspace,
            title = stringResource(R.string.my_campsites),
            navigationItems = navigationItems,
            selectedDestination = AppDestination.ownerCampsites(),
            onSelectDestination = onSelectDestination,
        ) {
            CampsiteListRoute(model) { id ->
                controller.navigate(
                    AppDestination.campsiteGraph(
                        Role.OWNER,
                        id,
                        CampsiteDetailContext.MANAGE,
                    ),
                ) { launchSingleTop = true }
            }
        }
    }
    val context = CampsiteDetailContext.MANAGE
    val parentPattern = AppDestination.campsitePattern(Role.OWNER, context)
    navigation(
        startDestination = parentPattern + "/overview",
        route = parentPattern,
        arguments = listOf(navArgument("campsiteId") {}),
    ) {
        composable(parentPattern + "/overview") { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(parentPattern) }
            val id = checkNotNull(parent.arguments?.getString("campsiteId"))
            val model: CampsiteDetailViewModel =
                viewModel(
                    viewModelStoreOwner = parent,
                    factory = detailFactory(id, context),
                )
            CampsiteDetailRoute(model) { controller.popBackStack() }
        }
    }
}
