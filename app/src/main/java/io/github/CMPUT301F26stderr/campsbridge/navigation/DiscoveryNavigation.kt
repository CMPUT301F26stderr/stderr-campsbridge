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
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.SearchFiltersRoute

/** Registers discovery/filter lifetime and a separate saved public listing reader.
 * @param controller Account-scoped navigation host.
 * @param state Confirmed root session.
 * @param onSelectWorkspace Root-owned permitted workspace selection.
 * @param items Registered entrant primary destinations.
 * @param onSelectDestination Primary selection callback.
 * @param exploreFactory Dependencies for one parent discovery model.
 * @param detailFactory Dependencies for an ID/context-qualified saved reader.
 */
internal fun NavGraphBuilder.discoveryGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    items: List<WorkspaceNavigationItem>,
    onSelectDestination: (String) -> Unit,
    exploreFactory: ViewModelProvider.Factory,
    detailFactory: (String, CampsiteDetailContext) -> ViewModelProvider.Factory,
) {
    navigation(
        startDestination = AppDestination.EXPLORE + "/results",
        route = AppDestination.EXPLORE,
    ) {
        composable(AppDestination.EXPLORE + "/results") { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(AppDestination.EXPLORE) }
            val model: ExploreViewModel =
                viewModel(viewModelStoreOwner = parent, factory = exploreFactory)
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                title = stringResource(R.string.discovery_title),
                navigationItems = items,
                selectedDestination = AppDestination.EXPLORE,
                onSelectDestination = onSelectDestination,
            ) {
                ExploreRoute(model, {
                    controller.navigate(AppDestination.EXPLORE + "/filters") {
                        launchSingleTop =
                            true
                    }
                }, { id ->
                    controller.navigate(
                        AppDestination.campsiteGraph(Role.ENTRANT, id, CampsiteDetailContext.VIEW),
                    ) {
                        launchSingleTop = true
                    }
                })
            }
        }
        composable(AppDestination.EXPLORE + "/filters") { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(AppDestination.EXPLORE) }
            val model: ExploreViewModel =
                viewModel(viewModelStoreOwner = parent, factory = exploreFactory)
            SearchFiltersRoute(model) { controller.popBackStack() }
        }
    }
    val context = CampsiteDetailContext.VIEW
    val pattern = AppDestination.campsitePattern(Role.ENTRANT, context)
    navigation(
        startDestination = "$pattern/overview",
        route = pattern,
        arguments = listOf(navArgument("campsiteId") {}),
    ) {
        composable("$pattern/overview") { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(pattern) }
            val id = checkNotNull(parent.arguments?.getString("campsiteId"))
            val model: CampsiteDetailViewModel =
                viewModel(viewModelStoreOwner = parent, factory = detailFactory(id, context))
            CampsiteDetailRoute(model) { controller.popBackStack() }
        }
    }
}
