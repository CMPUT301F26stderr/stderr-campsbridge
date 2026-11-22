/*
 * Registers the owner announcement composer route under navigation host.
 */

package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.AnnouncementComposeRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.AnnouncementComposeViewModel

/** Registers the participant announcement composer for an owner's saved lottery.
 * @param controller Account-owned navigation host.
 * @param factory Dependencies keyed by saved lottery identity.
 */
internal fun NavGraphBuilder.announcementComposeGraph(
    controller: NavHostController,
    factory: (String) -> ViewModelProvider.Factory,
) {
    composable(
        AppDestination.ANNOUNCEMENT_COMPOSE,
        arguments = listOf(navArgument("lotteryId") {}),
    ) { entry ->
        val id = requireNotNull(entry.arguments?.getString("lotteryId"))
        val model: AnnouncementComposeViewModel = viewModel(factory = factory(id))
        AnnouncementComposeRoute(
            model,
            onBack = { controller.popBackStack() },
        )
    }
}
