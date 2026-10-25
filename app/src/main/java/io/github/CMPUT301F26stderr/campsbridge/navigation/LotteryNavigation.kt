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

/** Registers one context-qualified parent shared by the saved overview and its later children.
 * @param controller Current account's navigation host.
 * @param factory Dependencies keyed by actual saved ID and explicit caller context.
 */
internal fun NavGraphBuilder.lotteryDetailGraph(
    controller: NavHostController,
    factory: (String, LotteryContext) -> ViewModelProvider.Factory,
) {
    for (context in LotteryContext.entries) {
        val pattern = AppDestination.lotteryDetailPattern(context)
        navigation(
            startDestination = "$pattern/overview",
            route = pattern,
            arguments = listOf(navArgument("lotteryId") {}),
        ) {
            composable("$pattern/overview") { entry ->
                val parent = remember(entry) { controller.getBackStackEntry(pattern) }
                val id = requireNotNull(parent.arguments?.getString("lotteryId"))
                val model: LotteryDetailViewModel =
                    viewModel(viewModelStoreOwner = parent, factory = factory(id, context))
                LotteryDetailRoute(
                    model,
                    { controller.popBackStack(pattern, inclusive = true) },
                    {
                        controller.navigate(AppDestination.lotterySetupGraph(id)) {
                            launchSingleTop =
                                true
                        }
                    },
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
                )
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
