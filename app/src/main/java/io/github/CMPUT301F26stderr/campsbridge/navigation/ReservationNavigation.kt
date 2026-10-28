package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailPage
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailViewModel
import java.util.Locale

/** Registers saved overview/payment/terms beneath one aggregate owner per actual ID/context.
 * @param controller Current account's navigation host.
 * @param factory Explicit saved target dependencies; child entry never creates a second owner.
 */
internal fun NavGraphBuilder.reservationGraph(
    controller: NavHostController,
    factory: (String, ReservationContext) -> ViewModelProvider.Factory,
) {
    for (context in ReservationContext.entries) {
        val pattern = AppDestination.reservationPattern(context)
        navigation(
            startDestination = "$pattern/overview",
            route = pattern,
            arguments = listOf(navArgument("reservationId") {}),
        ) {
            for (page in ReservationDetailPage.entries) {
                composable("$pattern/${page.name.lowercase(Locale.ROOT)}") { entry ->
                    val parent = remember(entry) { controller.getBackStackEntry(pattern) }
                    val id = requireNotNull(parent.arguments?.getString("reservationId"))
                    val model: ReservationDetailViewModel =
                        viewModel(viewModelStoreOwner = parent, factory = factory(id, context))
                    val actual = AppDestination.reservationDetail(id, context)
                    ReservationDetailRoute(
                        model,
                        page,
                        onBack = {
                            if (page ==
                                ReservationDetailPage.OVERVIEW
                            ) {
                                controller.popBackStack(pattern, inclusive = true)
                            } else {
                                controller.popBackStack()
                            }
                        },
                        onPayment = {
                            controller.navigate("$actual/payment") {
                                launchSingleTop =
                                    true
                            }
                        },
                        onTerms = {
                            controller.navigate(
                                "$actual/terms",
                            ) { launchSingleTop = true }
                        },
                    )
                }
            }
        }
    }
}
