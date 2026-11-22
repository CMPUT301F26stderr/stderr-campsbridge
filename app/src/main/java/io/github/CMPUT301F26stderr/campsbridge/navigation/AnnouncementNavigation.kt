package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.AnnouncementComposeRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.AnnouncementComposeViewModel

/** Registers the saved audience child without copying a recipient list into arguments.
 * @param controller Existing owner navigation host.
 * @param factory Actual actor and saved-lottery dependencies.
 */
internal fun NavGraphBuilder.announcementGraph(
    controller: NavHostController,
    factory: (String) -> ViewModelProvider.Factory,
) {
    composable(AppDestination.ANNOUNCEMENT_COMPOSE, arguments = listOf(navArgument("lotteryId") {})) { entry ->
        val id = checkNotNull(entry.arguments?.getString("lotteryId"))
        val model: AnnouncementComposeViewModel = viewModel(factory = factory(id))
        AnnouncementComposeRoute(model, onBack = { controller.popBackStack() }, onSent = {
            controller.previousBackStackEntry?.savedStateHandle?.set("announcementSent", true)
            controller.popBackStack()
        })
    }
}
