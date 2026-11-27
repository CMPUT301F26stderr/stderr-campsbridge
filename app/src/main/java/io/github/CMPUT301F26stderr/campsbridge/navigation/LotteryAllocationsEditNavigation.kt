/*
 * Registers one saved owned-set editor and its temporary picker/calendar children.
 */

package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryAllocationChoiceRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryAllocationsCalendarRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryAllocationsEditRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryAllocationsEditViewModel

/** Registers one saved owned-set editor and its temporary picker/calendar children.
 * @param controller Account-owned navigation host.
 * @param factory Dependencies keyed by the confirmed saved lottery ID.
 */
internal fun NavGraphBuilder.lotteryAllocationsEditGraph(
    controller: NavHostController,
    factory: (String) -> ViewModelProvider.Factory,
) {
    val pattern = AppDestination.LOTTERY_ALLOCATIONS_EDIT
    navigation(
        startDestination = "$pattern/overview",
        route = pattern,
        arguments = listOf(navArgument("lotteryId") {}),
    ) {
        composable("$pattern/overview") { entry ->
            val model = lotteryAllocationsEditor(controller, entry, factory)
            val saved = AppDestination.lotteryAllocationsEdit(model.lotteryId)
            LotteryAllocationsEditRoute(
                model,
                onExit = { controller.popBackStack(pattern, inclusive = true) },
                onAdd = { controller.navigate("$saved/choose") { launchSingleTop = true } },
                onCalendar = { controller.navigate("$saved/calendar") { launchSingleTop = true } },
            )
        }
        composable("$pattern/choose") { entry ->
            val model = lotteryAllocationsEditor(controller, entry, factory)
            LotteryAllocationChoiceRoute(model, { controller.popBackStack() }) {
                controller.navigate(AppDestination.lotteryAllocationsEdit(model.lotteryId) + "/calendar") {
                    popUpTo("$pattern/choose") { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        composable("$pattern/calendar") { entry ->
            LotteryAllocationsCalendarRoute(
                lotteryAllocationsEditor(controller, entry, factory),
            ) { controller.popBackStack() }
        }
    }
}

/** Resolves one graph lifetime for applied offers and temporary child selection.
 * @param controller Current account's navigation host.
 * @param entry Active child retaining its parent reference.
 * @param factory Dependencies for the saved ID.
 */
@Composable
private fun lotteryAllocationsEditor(
    controller: NavHostController,
    entry: NavBackStackEntry,
    factory: (String) -> ViewModelProvider.Factory,
): LotteryAllocationsEditViewModel {
    val parent = remember(entry) { controller.getBackStackEntry(AppDestination.LOTTERY_ALLOCATIONS_EDIT) }
    val id = checkNotNull(parent.arguments?.getString("lotteryId"))
    return viewModel(viewModelStoreOwner = parent, factory = factory(id))
}
