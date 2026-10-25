package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryAllocationRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupViewModel

/** Registers one local setup parent shared by six steps and its working-allocation child.
 * @param controller Current account's navigation host.
 * @param factory Explicit owner/source/clock/validator dependencies.
 */
internal fun NavGraphBuilder.lotterySetupGraph(
    controller: NavHostController,
    factory: ViewModelProvider.Factory,
) {
    val graph = AppDestination.LOTTERY_SETUP
    navigation(startDestination = "$graph/steps", route = graph) {
        composable("$graph/steps") { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(graph) }
            val model: LotterySetupViewModel =
                viewModel(viewModelStoreOwner = parent, factory = factory)
            LotterySetupRoute(model, { controller.popBackStack(graph, inclusive = true) }, {
                controller.navigate("$graph/allocation") { launchSingleTop = true }
            })
        }
        composable("$graph/allocation") { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(graph) }
            val model: LotterySetupViewModel =
                viewModel(viewModelStoreOwner = parent, factory = factory)
            LotteryAllocationRoute(model) { controller.popBackStack() }
        }
    }
}
