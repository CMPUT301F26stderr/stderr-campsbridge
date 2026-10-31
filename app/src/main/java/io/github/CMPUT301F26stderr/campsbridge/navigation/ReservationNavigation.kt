package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailPage
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailViewModel

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
            startDestination = "$pattern/${ReservationDetailPage.OVERVIEW.routeKey}",
            route = pattern,
            arguments = listOf(navArgument("reservationId") {}),
        ) {
            for (page in ReservationDetailPage.entries) {
                composable("$pattern/${page.routeKey}") { entry ->
                    val parent = remember(entry) { controller.getBackStackEntry(pattern) }
                    val id = requireNotNull(parent.arguments?.getString("reservationId"))
                    val model: ReservationDetailViewModel =
                        viewModel(viewModelStoreOwner = parent, factory = factory(id, context))
                    val savedRoute = AppDestination.reservationDetail(id, context)
                    ReservationDetailRoute(
                        model,
                        page,
                        onBack = {
                            if (page == ReservationDetailPage.OVERVIEW) {
                                controller.popBackStack(pattern, inclusive = true)
                            } else {
                                controller.popBackStack()
                            }
                        },
                        onPayment = {
                            controller.navigate(
                                "$savedRoute/${ReservationDetailPage.PAYMENT.routeKey}",
                            ) {
                                launchSingleTop = true
                            }
                        },
                        onTerms = {
                            controller.navigate(
                                "$savedRoute/${ReservationDetailPage.TERMS.routeKey}",
                            ) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Registers actual Active primaries and the ID-filtered saved-campsite management child.
 * @param controller Current account navigation host.
 * @param state Ready session and actual selected workspace.
 * @param onSelectWorkspace Root-owned workspace selection.
 * @param ownerItems Actual owner primary peers.
 * @param entrantItems Actual entrant primary peers.
 * @param onSelectDestination Retained primary navigation.
 * @param factory Scoped list dependencies, keeping F17/F18 absent until reviewed integration.
 */
internal fun NavGraphBuilder.reservationListGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    ownerItems: List<WorkspaceNavigationItem>,
    entrantItems: List<WorkspaceNavigationItem>,
    onSelectDestination: (String) -> Unit,
    factory: (ReservationContext, String?) -> ViewModelProvider.Factory,
) {
    for (context in ReservationContext.entries) {
        val route = AppDestination.reservations(context)
        composable(route) {
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                stringResource(R.string.my_reservations),
                if (context == ReservationContext.OWNER) ownerItems else entrantItems,
                route,
                onSelectDestination,
            ) { EmptyContent(stringResource(R.string.feature_pending)) }
        }
    }
    composable(
        AppDestination.CAMPSITE_RESERVATIONS,
        arguments = listOf(navArgument("campsiteId") {}),
    ) {
        AppScreen(stringResource(R.string.my_reservations), onBack = {
            controller.popBackStack()
        }) { EmptyContent(stringResource(R.string.feature_pending)) }
    }
}
