package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryListRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryListViewModel

/** Registers real owner primary/filter lists, using existing setup and saved-reader targets.
 * @param controller Account navigation host.
 * @param state Ready session and selected workspace.
 * @param onSelectWorkspace Root-owned capability switch.
 * @param navigationItems Registered owner peers.
 * @param onSelectDestination Retained primary navigation.
 * @param factory Scoped constructor keyed by optional campsite association.
 */
internal fun NavGraphBuilder.lotteryListGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    navigationItems: List<WorkspaceNavigationItem>,
    onSelectDestination: (String) -> Unit,
    factory: (String?) -> ViewModelProvider.Factory,
) {
    for (pattern in listOf(AppDestination.OWNER_LOTTERIES, AppDestination.CAMPSITE_LOTTERIES)) {
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
            val model: LotteryListViewModel = viewModel(factory = factory(id))
            val content: @androidx.compose.runtime.Composable () -> Unit = {
                LotteryListRoute(model, onCreate = {
                    controller.navigate(AppDestination.LOTTERY_SETUP) {
                        launchSingleTop =
                            true
                    }
                }) { item ->
                    controller.navigate(
                        if (item.lottery.publishedAt ==
                            null
                        ) {
                            AppDestination.lotterySetupGraph(item.lottery.id)
                        } else {
                            AppDestination.lotteryDetail(item.lottery.id)
                        },
                    ) {
                        launchSingleTop =
                            true
                    }
                }
            }
            if (pattern == AppDestination.OWNER_LOTTERIES) {
                WorkspaceScaffold(
                    state,
                    onSelectWorkspace,
                    stringResource(R.string.my_lotteries),
                    navigationItems,
                    AppDestination.OWNER_LOTTERIES,
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
