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
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteLocationRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteSetupRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteSetupViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyReaderScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.policyContent

/**
 * Registers implemented inventory and saved management routes; future editors add their own
 * entries.
 * @param controller Account-owned navigation host.
 * @param state Confirmed session.
 * @param onSelectWorkspace Root-owned capability-limited selection.
 * @param navigationItems Registered owner peers.
 * @param onSelectDestination Primary tab selection.
 * @param listFactory Inventory constructor dependencies.
 * @param detailFactory Saved reader dependencies for an ID/context.
 * @param availabilityFactory Dependencies for the separate saved inventory editor.
 * @param setupFactory Local editor dependencies for a new or existing draft.
 */
internal fun NavGraphBuilder.campsiteGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    navigationItems: List<WorkspaceNavigationItem>,
    onSelectDestination: (String) -> Unit,
    listFactory: ViewModelProvider.Factory,
    detailFactory: (String, CampsiteDetailContext) -> ViewModelProvider.Factory,
    setupFactory: (String?) -> ViewModelProvider.Factory,
    availabilityFactory: (String) -> ViewModelProvider.Factory,
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
            CampsiteListRoute(model, onCreate = {
                controller.navigate(AppDestination.campsiteSetupGraph()) { launchSingleTop = true }
            }) { id ->
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
            CampsiteDetailRoute(model, onCreateLottery = {
                controller.navigate(AppDestination.LOTTERY_SETUP) { launchSingleTop = true }
            }, onViewLotteries = {
                controller.navigate(AppDestination.campsiteLotteries(id)) { launchSingleTop = true }
            }, onViewReservations = {
                controller.navigate(AppDestination.campsiteReservations(id)) {
                    launchSingleTop =
                        true
                }
            }, onPreview = {
                controller.navigate(
                    AppDestination.campsiteGraph(
                        Role.OWNER,
                        id,
                        CampsiteDetailContext.PREVIEW,
                    ),
                ) { launchSingleTop = true }
            }, onAvailability = {
                controller.navigate(
                    AppDestination.availabilityEditor(id),
                ) { launchSingleTop = true }
            }, onResume = {
                controller.navigate(
                    AppDestination.campsiteSetupGraph(id),
                ) { launchSingleTop = true }
            }) { controller.popBackStack() }
        }
    }
    campsiteReadGraph(controller, Role.OWNER, CampsiteDetailContext.PREVIEW, detailFactory)
    composable(
        AppDestination.AVAILABILITY_EDITOR,
        arguments =
            listOf(
                navArgument("campsiteId") {
                },
            ),
    ) { entry ->
        val id = checkNotNull(entry.arguments?.getString("campsiteId"))
        val model: AvailabilityViewModel = viewModel(factory = availabilityFactory(id))
        AvailabilityRoute(model) { controller.popBackStack() }
    }
    for (pattern in listOf(
        AppDestination.CAMPSITE_SETUP_NEW,
        AppDestination.CAMPSITE_SETUP_SAVED,
    )) {
        navigation(
            startDestination = "$pattern/steps",
            route = pattern,
            arguments =
                if (pattern == AppDestination.CAMPSITE_SETUP_SAVED) {
                    listOf(navArgument("campsiteId") {})
                } else {
                    emptyList()
                },
        ) {
            composable("$pattern/steps") { entry ->
                val parent = remember(entry) { controller.getBackStackEntry(pattern) }
                val id = parent.arguments?.getString("campsiteId")
                val model: CampsiteSetupViewModel =
                    viewModel(viewModelStoreOwner = parent, factory = setupFactory(id))
                CampsiteSetupRoute(
                    model,
                    onExit = { controller.popBackStack(pattern, inclusive = true) },
                    onSaved = { savedId ->
                        controller.navigate(
                            AppDestination.campsiteGraph(
                                Role.OWNER,
                                savedId,
                                CampsiteDetailContext.MANAGE,
                            ),
                        ) {
                            popUpTo(pattern) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenPolicy = {
                        controller.navigate(AppDestination.campsiteSetupGraph(id) + "/refund")
                    },
                    onChooseLocation = {
                        controller.navigate(AppDestination.campsiteSetupGraph(id) + "/location")
                    },
                )
            }
            composable("$pattern/location") { entry ->
                val parent = remember(entry) { controller.getBackStackEntry(pattern) }
                val id = parent.arguments?.getString("campsiteId")
                val model: CampsiteSetupViewModel =
                    viewModel(viewModelStoreOwner = parent, factory = setupFactory(id))
                CampsiteLocationRoute(model) { controller.popBackStack() }
            }
            composable("$pattern/refund") {
                PolicyReaderScreen(policyContent(PolicyId.REFUND), { controller.popBackStack() })
            }
        }
    }
}
