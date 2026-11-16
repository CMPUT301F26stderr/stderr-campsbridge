package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.platform.maps.LocationPicker
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteEditFieldsScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteEditRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteEditSection
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteEditSectionRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteEditUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteEditViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsitePhotoRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyReaderScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.policyContent
import java.util.Locale

/** Registers one saved edit owner with retained fields, photo, map and policy children.
 * @param controller Account-owned navigation host.
 * @param factory Dependencies for this existing saved identity.
 */
internal fun NavGraphBuilder.campsiteEditGraph(
    controller: NavHostController,
    factory: (String) -> ViewModelProvider.Factory,
) {
    val pattern = AppDestination.CAMPSITE_EDIT
    navigation(
        startDestination = "$pattern/overview",
        route = pattern,
        arguments = listOf(navArgument("campsiteId") {}),
    ) {
        composable("$pattern/overview") { entry ->
            val model = campsiteEditor(controller, entry, factory)
            val path = AppDestination.campsiteEditGraph(model.campsiteId)
            CampsiteEditRoute(
                model,
                onExit = { controller.popBackStack(pattern, inclusive = true) },
                onSection = {
                    controller.navigate(
                        "$path/${it.name.lowercase(Locale.ROOT)}",
                    ) { launchSingleTop = true }
                },
                onPhoto = { controller.navigate("$path/photo") { launchSingleTop = true } },
                onAvailability = {
                    controller.navigate(AppDestination.availabilityEditor(model.campsiteId)) {
                        launchSingleTop =
                            true
                    }
                },
            )
        }
        for (section in CampsiteEditSection.entries) {
            composable("$pattern/${section.name.lowercase(Locale.ROOT)}") { entry ->
                val model = campsiteEditor(controller, entry, factory)
                val path = AppDestination.campsiteEditGraph(model.campsiteId)
                CampsiteEditSectionRoute(
                    model,
                    section,
                    onBack = { controller.popBackStack() },
                    onChooseLocation = { controller.navigate("$path/location-picker") { launchSingleTop = true } },
                    onOpenPolicy = { controller.navigate("$path/refund") { launchSingleTop = true } },
                )
            }
        }
        composable("$pattern/photo") { entry ->
            CampsitePhotoRoute(campsiteEditor(controller, entry, factory)) { controller.popBackStack() }
        }
        composable("$pattern/location-picker") { entry ->
            val model = campsiteEditor(controller, entry, factory)
            val state by model.state.collectAsStateWithLifecycle()
            val content = state as? CampsiteEditUiState.Content
            if (content != null) {
                LocationPicker(content.draft.location, {
                    model.applyLocation(it)
                    controller.popBackStack()
                }, { controller.popBackStack() })
            } else {
                CampsiteEditFieldsScreen(stringResource(R.string.location_picker_title), state, {
                    controller.popBackStack()
                }, model::retry) {}
            }
        }
        composable("$pattern/refund") {
            PolicyReaderScreen(policyContent(PolicyId.REFUND), { controller.popBackStack() })
        }
    }
}

/** Returns the same controller for every child of this saved edit graph.
 * @param controller Account navigation host.
 * @param entry Current child used to retain the parent reference.
 * @param factory Dependencies for the confirmed navigation identity.
 */
@Composable
private fun campsiteEditor(
    controller: NavHostController,
    entry: NavBackStackEntry,
    factory: (String) -> ViewModelProvider.Factory,
): CampsiteEditViewModel {
    val parent = remember(entry) { controller.getBackStackEntry(AppDestination.CAMPSITE_EDIT) }
    val id = checkNotNull(parent.arguments?.getString("campsiteId"))
    return viewModel(viewModelStoreOwner = parent, factory = factory(id))
}
