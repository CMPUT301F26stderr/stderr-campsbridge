package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryAllocationRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupViewModel

/** Registers one local setup parent shared by six steps and its working-allocation child.
 * @param controller Current account's navigation host.
 * @param factory Explicit owner/source/clock/validator dependencies keyed by optional saved ID.
 */
internal fun NavGraphBuilder.lotterySetupGraph(
    controller: NavHostController,
    factory: (String?) -> ViewModelProvider.Factory,
) {
    for (graph in listOf(AppDestination.LOTTERY_SETUP, AppDestination.LOTTERY_SETUP_SAVED)) {
        val arguments =
            if (graph ==
                AppDestination.LOTTERY_SETUP_SAVED
            ) {
                listOf(
                    navArgument("lotteryId") {
                        type =
                            NavType.StringType
                    },
                )
            } else {
                emptyList()
            }
        navigation(startDestination = "$graph/steps", route = graph, arguments = arguments) {
            composable("$graph/steps") { entry ->
                val parent = remember(entry) { controller.getBackStackEntry(graph) }
                val model: LotterySetupViewModel =
                    viewModel(
                        viewModelStoreOwner = parent,
                        factory = factory(parent.arguments?.getString("lotteryId")),
                    )
                LotterySetupRoute(
                    model,
                    { controller.popBackStack(graph, inclusive = true) },
                    {
                        controller.navigate(
                            AppDestination.lotterySetupGraph(
                                parent.arguments?.getString("lotteryId"),
                            ) +
                                "/allocation",
                        ) { launchSingleTop = true }
                    },
                    { id ->
                        controller.popBackStack(graph, inclusive = true)
                        val showingOwnerDetail =
                            controller.currentDestination?.hierarchy?.any {
                                it.route == AppDestination.LOTTERY_DETAIL
                            } == true
                        val shownId =
                            if (showingOwnerDetail) {
                                controller
                                    .getBackStackEntry(AppDestination.LOTTERY_DETAIL)
                                    .arguments
                                    ?.getString("lotteryId")
                            } else {
                                null
                            }
                        if (shownId != id) {
                            controller.navigate(AppDestination.lotteryDetail(id)) {
                                launchSingleTop =
                                    true
                            }
                        }
                    },
                )
            }
            composable("$graph/allocation") { entry ->
                val parent = remember(entry) { controller.getBackStackEntry(graph) }
                val model: LotterySetupViewModel =
                    viewModel(
                        viewModelStoreOwner = parent,
                        factory = factory(parent.arguments?.getString("lotteryId")),
                    )
                LotteryAllocationRoute(model) { controller.popBackStack() }
            }
        }
    }
}
