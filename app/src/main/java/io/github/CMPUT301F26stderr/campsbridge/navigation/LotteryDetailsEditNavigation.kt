/*
 * Registers one saved organizer editor shared by its menu and field children.
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
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailsEditRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailsEditViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailsSectionRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryEditSection
import java.util.Locale

/** Registers one saved organizer editor shared by its menu and field children.
 * @param controller Account-owned navigation host.
 * @param factory Dependencies keyed by the existing lottery identity.
 */
internal fun NavGraphBuilder.lotteryDetailsEditGraph(
    controller: NavHostController,
    factory: (String) -> ViewModelProvider.Factory,
) {
    val pattern = AppDestination.LOTTERY_DETAILS_EDIT
    navigation(
        startDestination = "$pattern/overview",
        route = pattern,
        arguments = listOf(navArgument("lotteryId") {}),
    ) {
        composable("$pattern/overview") { entry ->
            val model = lotteryDetailsEditor(controller, entry, factory)
            LotteryDetailsEditRoute(
                model,
                onExit = { controller.popBackStack(pattern, inclusive = true) },
                onSection = {
                    controller.navigate(
                        AppDestination.lotteryDetailsEdit(model.lotteryId) + "/${it.name.lowercase(Locale.ROOT)}",
                    ) {
                        launchSingleTop =
                            true
                    }
                },
            )
        }
        for (section in LotteryEditSection.entries) {
            composable("$pattern/${section.name.lowercase(Locale.ROOT)}") { entry ->
                LotteryDetailsSectionRoute(
                    lotteryDetailsEditor(controller, entry, factory),
                    section,
                ) { controller.popBackStack() }
            }
        }
    }
}

/** Resolves the same controller for every child of this saved details graph.
 * @param controller Account navigation host.
 * @param entry Current child retaining the parent reference.
 * @param factory Dependencies for this confirmed navigation identity.
 */
@Composable
private fun lotteryDetailsEditor(
    controller: NavHostController,
    entry: NavBackStackEntry,
    factory: (String) -> ViewModelProvider.Factory,
): LotteryDetailsEditViewModel {
    val parent = remember(entry) { controller.getBackStackEntry(AppDestination.LOTTERY_DETAILS_EDIT) }
    val id = checkNotNull(parent.arguments?.getString("lotteryId"))
    return viewModel(viewModelStoreOwner = parent, factory = factory(id))
}
