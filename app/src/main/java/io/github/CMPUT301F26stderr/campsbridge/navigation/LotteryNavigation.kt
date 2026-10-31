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
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailSection
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryListRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.ParticipantListRoute

/** Registers account-scoped primary lists and the owner-management association filter.
 * @param controller Account navigation host.
 * @param state Ready session and selected workspace.
 * @param onSelectWorkspace Root-owned capability switch.
 * @param navigationItems Registered owner peers.
 * @param entrantItems Registered entrant peers.
 * @param onSelectDestination Retained primary navigation.
 * @param factory Scoped constructor keyed by context and optional campsite association.
 */
internal fun NavGraphBuilder.lotteryListGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    navigationItems: List<WorkspaceNavigationItem>,
    entrantItems: List<WorkspaceNavigationItem>,
    onSelectDestination: (String) -> Unit,
    factory: (LotteryContext, String?) -> ViewModelProvider.Factory,
) {
    for (pattern in listOf(
        AppDestination.OWNER_LOTTERIES,
        AppDestination.CAMPSITE_LOTTERIES,
        AppDestination.ENTRANT_LOTTERIES,
    )) {
        composable(
            pattern,
            arguments =
                if (pattern ==
                    AppDestination.CAMPSITE_LOTTERIES
                ) {
                    listOf(navArgument("campsiteId") {})
                } else {
                    emptyList()
                },
        ) { entry ->
            val id = entry.arguments?.getString("campsiteId")
            val context =
                if (pattern ==
                    AppDestination.ENTRANT_LOTTERIES
                ) {
                    LotteryContext.ENTRANT
                } else {
                    LotteryContext.OWNER
                }
            val model: LotteryListViewModel = viewModel(factory = factory(context, id))
            val content: @androidx.compose.runtime.Composable () -> Unit = {
                LotteryListRoute(
                    model,
                    onCreate =
                        if (context == LotteryContext.OWNER) {
                            {
                                controller.navigate(AppDestination.LOTTERY_SETUP) {
                                    launchSingleTop =
                                        true
                                }
                            }
                        } else {
                            null
                        },
                ) { item ->
                    controller.navigate(
                        if (item.lottery.publishedAt ==
                            null
                        ) {
                            AppDestination.lotterySetupGraph(item.lottery.id)
                        } else {
                            AppDestination.lotteryDetail(item.lottery.id, context)
                        },
                    ) {
                        launchSingleTop =
                            true
                    }
                }
            }
            if (pattern != AppDestination.CAMPSITE_LOTTERIES) {
                WorkspaceScaffold(
                    state,
                    onSelectWorkspace,
                    stringResource(R.string.my_lotteries),
                    if (context == LotteryContext.OWNER) navigationItems else entrantItems,
                    pattern,
                    onSelectDestination,
                ) { content() }
            } else {
                AppScreen(stringResource(R.string.campsite_lotteries), onBack = {
                    controller.popBackStack()
                }) { content() }
            }
        }
    }
}

/** Registers context-qualified overview, schedule, allocation and owner participant children.
 * @param controller Current account's navigation host.
 * @param factory Dependencies keyed by actual saved ID and explicit caller context.
 * @param onReservation Actual registered consuming-record destination, absent in isolated hosts.
 * @param onBooking Actual registered single-site winner flow, absent in isolated hosts.
 * @param onCampsite Actual saved campsite calendar, absent in isolated hosts.
 */
internal fun NavGraphBuilder.lotteryDetailGraph(
    controller: NavHostController,
    factory: (String, LotteryContext) -> ViewModelProvider.Factory,
    onReservation: ((String) -> Unit)? = null,
    onBooking: ((String, String) -> Unit)? = null,
    onCampsite: ((String, LotteryContext) -> Unit)? = null,
) {
    for (context in LotteryContext.entries) {
        val pattern = AppDestination.lotteryDetailPattern(context)
        navigation(
            startDestination = "$pattern/overview",
            route = pattern,
            arguments = listOf(navArgument("lotteryId") {}),
        ) {
            for (section in LotteryDetailSection.entries) {
                composable("$pattern/${section.routeKey}") { entry ->
                    val parent = remember(entry) { controller.getBackStackEntry(pattern) }
                    val id = requireNotNull(parent.arguments?.getString("lotteryId"))
                    val model: LotteryDetailViewModel =
                        viewModel(viewModelStoreOwner = parent, factory = factory(id, context))
                    LotteryDetailRoute(
                        model,
                        {
                            if (section == LotteryDetailSection.OVERVIEW) {
                                controller.popBackStack(pattern, inclusive = true)
                            } else {
                                controller.popBackStack()
                            }
                        },
                        {
                            controller.navigate(AppDestination.lotterySetupGraph(id)) {
                                launchSingleTop =
                                    true
                            }
                        },
                        onReservation =
                            if (context ==
                                LotteryContext.ENTRANT
                            ) {
                                onReservation
                            } else {
                                null
                            },
                        onBooking = if (context == LotteryContext.ENTRANT) onBooking else null,
                        onParticipants =
                            if (context == LotteryContext.OWNER) {
                                {
                                    controller.navigate(AppDestination.lotteryParticipants(id)) {
                                        launchSingleTop =
                                            true
                                    }
                                }
                            } else {
                                null
                            },
                        section = section,
                        onOpenSection = { selected ->
                            controller.navigate(
                                AppDestination.lotterySection(id, context, selected),
                            ) {
                                launchSingleTop = true
                            }
                        },
                        onCampsite =
                            onCampsite?.let { open ->
                                { campsiteId -> open(campsiteId, context) }
                            },
                    )
                }
            }
            if (context == LotteryContext.OWNER) {
                composable("$pattern/participants") { entry ->
                    val parent = remember(entry) { controller.getBackStackEntry(pattern) }
                    val id = requireNotNull(parent.arguments?.getString("lotteryId"))
                    val model: LotteryDetailViewModel =
                        viewModel(viewModelStoreOwner = parent, factory = factory(id, context))
                    ParticipantListRoute(model) { controller.popBackStack() }
                }
            }
        }
    }
}
