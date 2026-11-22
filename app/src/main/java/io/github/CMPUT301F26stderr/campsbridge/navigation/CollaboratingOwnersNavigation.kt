package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.CollaboratingOwnersRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.CollaboratingOwnersViewModel

/** Registers one saved owner picker without creating a second lottery-authoring graph.
 * @param controller Current account's navigation host.
 * @param factory Dependencies keyed by the confirmed saved ID.
 */
internal fun NavGraphBuilder.collaboratingOwnersGraph(
    controller: NavHostController,
    factory: (String) -> ViewModelProvider.Factory,
) {
    composable(AppDestination.LOTTERY_OWNERS, arguments = listOf(navArgument("lotteryId") {})) { entry ->
        val id = checkNotNull(entry.arguments?.getString("lotteryId"))
        val model: CollaboratingOwnersViewModel = viewModel(factory = factory(id))
        CollaboratingOwnersRoute(model) { controller.popBackStack() }
    }
}
