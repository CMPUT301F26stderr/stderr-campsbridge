package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyReaderScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.policyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Registers one ID-scoped winner parent with four steps and its packaged-policy child.
 * @param controller Current account navigation host.
 * @param factory Explicit dependencies keyed by actual lottery and campsite identities.
 */
internal fun NavGraphBuilder.bookingGraph(
    controller: NavHostController,
    factory: (String, String) -> ViewModelProvider.Factory,
) {
    val graph = AppDestination.BOOKING
    navigation(
        startDestination = "$graph/steps",
        route = graph,
        arguments = listOf(navArgument("lotteryId") {}, navArgument("campsiteId") {}),
    ) {
        composable("$graph/steps") { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(graph) }
            val drawId = requireNotNull(parent.arguments?.getString("lotteryId"))
            val siteId = requireNotNull(parent.arguments?.getString("campsiteId"))
            val model: BookingViewModel =
                viewModel(
                    viewModelStoreOwner = parent,
                    factory = factory(drawId, siteId),
                )
            BookingRoute(
                model,
                { controller.popBackStack(graph, inclusive = true) },
                {
                    controller.navigate("${AppDestination.booking(drawId, siteId)}/policy") {
                        launchSingleTop = true
                    }
                },
                { savedId ->
                    controller.popBackStack(graph, inclusive = true)
                    controller.navigate(
                        AppDestination.reservationDetail(savedId, ReservationContext.ENTRANT),
                    ) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable("$graph/policy") {
            PolicyReaderScreen(policyContent(PolicyId.REFUND), { controller.popBackStack() })
        }
    }
}
