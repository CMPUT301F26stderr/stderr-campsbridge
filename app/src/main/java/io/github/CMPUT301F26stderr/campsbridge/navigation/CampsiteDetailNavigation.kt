package io.github.CMPUT301F26stderr.campsbridge.navigation

import android.net.Uri
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailSection
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyReaderScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.policyContent

/** Registers saved children with entrant actions confined to the actual VIEW graph.
 * @param controller Current account's navigation host.
 * @param workspace Owning workspace; preview/inspection never borrow an entrant model.
 * @param context Fixed presentation validated by the owning graph.
 * @param factory Saved ID/context constructor binding.
 */
internal fun NavGraphBuilder.campsiteReadGraph(
    controller: NavHostController,
    workspace: Role,
    context: CampsiteDetailContext,
    factory: (String, CampsiteDetailContext) -> ViewModelProvider.Factory,
) {
    require(context != CampsiteDetailContext.MANAGE)
    val pattern = AppDestination.campsitePattern(workspace, context)
    navigation(
        startDestination = "$pattern/overview",
        route = pattern,
        arguments = listOf(navArgument("campsiteId") {}),
    ) {
        for (section in CampsiteDetailSection.entries) {
            if (section == CampsiteDetailSection.LOTTERIES &&
                context != CampsiteDetailContext.VIEW
            ) {
                continue
            }
            composable("$pattern/${section.routeKey}") { entry ->
                val parent = remember(entry) { controller.getBackStackEntry(pattern) }
                val id = checkNotNull(parent.arguments?.getString("campsiteId"))
                val actual = AppDestination.campsiteGraph(workspace, id, context)
                val model: CampsiteDetailViewModel =
                    viewModel(
                        viewModelStoreOwner = parent,
                        factory = factory(id, context),
                    )
                CampsiteDetailRoute(
                    model,
                    section = section,
                    onOpenLottery =
                        if (context ==
                            CampsiteDetailContext.VIEW
                        ) {
                            { lotteryId ->
                                controller.navigate(
                                    AppDestination.lotteryDetail(lotteryId, LotteryContext.ENTRANT),
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        } else {
                            null
                        },
                    onOpenSection = { selected ->
                        controller.navigate(
                            "$actual/${selected.routeKey}",
                        ) { launchSingleTop = true }
                    },
                    onOpenPolicy = { policy ->
                        controller.navigate(
                            "$actual/policy/${policy.routeKey}?version=" +
                                Uri.encode(policy.currentVersion),
                        ) { launchSingleTop = true }
                    },
                ) { controller.popBackStack() }
            }
        }
        composable(
            "$pattern/policy/{policyKey}?version={version}",
            arguments = listOf(navArgument("policyKey") {}, navArgument("version") {}),
        ) { entry ->
            val policy =
                PolicyId.entries.firstOrNull {
                    it.routeKey == entry.arguments?.getString("policyKey")
                }
            val version = entry.arguments?.getString("version")
            val content =
                if (policy != null && version != null) {
                    policyContent(policy, version)
                } else {
                    null
                }
            PolicyReaderScreen(content, { controller.popBackStack() })
        }
    }
}
