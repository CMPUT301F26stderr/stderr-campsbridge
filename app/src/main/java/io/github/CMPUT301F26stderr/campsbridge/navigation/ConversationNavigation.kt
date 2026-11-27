/*
 * Registers the same saved conversation for direct and resolved notification entry.
 */

package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Registers the same saved conversation for direct and resolved notification entry.
 * @param controller Current account's navigation host.
 * @param factory Dependencies keyed only by actual saved reservation identity.
 */
internal fun NavGraphBuilder.conversationGraph(
    controller: NavHostController,
    factory: (String) -> ViewModelProvider.Factory,
) {
    for (context in ReservationContext.entries) {
        composable(
            AppDestination.conversationPattern(context),
            arguments = listOf(navArgument("reservationId") {}),
        ) { entry ->
            val id = checkNotNull(entry.arguments?.getString("reservationId"))
            val model: ConversationViewModel = viewModel(factory = factory(id))
            ConversationRoute(
                model,
                onOpenReservation = {
                    controller.navigate(AppDestination.reservationDetail(id, context)) { launchSingleTop = true }
                },
            ) { controller.popBackStack() }
        }
    }
}
