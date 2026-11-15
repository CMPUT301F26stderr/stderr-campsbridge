package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteEditRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteEditSection
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteEditViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyReaderScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.policyContent

/** Shares one saved editor across details, photo, location and policy children. */
internal fun NavGraphBuilder.campsiteEditGraph(
    controller: NavHostController,
    factory: (String) -> ViewModelProvider.Factory,
) {
    val pattern = AppDestination.CAMPSITE_EDIT
    navigation(
        startDestination = "$pattern/summary",
        route = pattern,
        arguments = listOf(navArgument("campsiteId") {}),
    ) {
        val children = listOf("summary", "photo", "map") + CampsiteEditSection.entries.map { it.name }
        for (child in children) {
            composable("$pattern/$child") { entry ->
                val parent = remember(entry) { controller.getBackStackEntry(pattern) }
                val id = checkNotNull(parent.arguments?.getString("campsiteId"))
                val actual = AppDestination.campsiteEdit(id)
                val model: CampsiteEditViewModel = viewModel(viewModelStoreOwner = parent, factory = factory(id))
                CampsiteEditRoute(
                    model,
                    onExit = { controller.popBackStack(pattern, inclusive = true) },
                    onSection = { controller.navigate("$actual/${it.name}") { launchSingleTop = true } },
                    onPhoto = { controller.navigate("$actual/photo") { launchSingleTop = true } },
                    onAvailability = {
                        controller.navigate(
                            AppDestination.availabilityEditor(id),
                        ) { launchSingleTop = true }
                    },
                    onChooseLocation = { controller.navigate("$actual/map") { launchSingleTop = true } },
                    onOpenPolicy = { controller.navigate("$actual/refund") { launchSingleTop = true } },
                    onBack = { controller.popBackStack() },
                    section = CampsiteEditSection.entries.find { it.name == child },
                    photoPage = child == "photo",
                    locationPage = child == "map",
                )
            }
        }
        composable("$pattern/refund") {
            PolicyReaderScreen(policyContent(PolicyId.REFUND), { controller.popBackStack() })
        }
    }
}
