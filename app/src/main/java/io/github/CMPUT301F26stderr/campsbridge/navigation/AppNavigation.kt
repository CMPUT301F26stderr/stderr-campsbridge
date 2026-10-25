package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceMenu
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.common.roleLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ContactDetailsRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileDetailsRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileViewModel

/**
 * Hosts implemented destinations. The root keys this graph by account identity.
 * @param profileFactory Dependencies for the existing-profile destination.
 * @param state Ready session; workspace is distinct from granted capabilities.
 * @param onSelectWorkspace Root-owned capability-limited selection.
 * @param modifier Host layout; child screens own their own insets.
 */
@Composable
fun AppNavigation(
    profileFactory: ViewModelProvider.Factory,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = rememberNavController()
    val initialWorkspace = remember { state.workspace }
    NavHost(controller, AppDestination.profileGraph(initialWorkspace), modifier) {
        for (workspace in Role.entries) {
            navigation(
                AppDestination.profileMenu(workspace),
                AppDestination.profileGraph(workspace),
            ) {
                composable(AppDestination.profileMenu(workspace)) { entry ->
                    val model = profileGraphModel(controller, entry, workspace, profileFactory)
                    WorkspaceScaffold(state, onSelectWorkspace) {
                        ProfileRoute(
                            model,
                            onPersonalDetails = {
                                controller.navigate(
                                    AppDestination.personalDetails(workspace),
                                )
                            },
                            onContactDetails = {
                                controller.navigate(
                                    AppDestination.contactDetails(workspace),
                                )
                            },
                            workspaceContent = {
                                if (state.account.roles.size >
                                    1
                                ) {
                                    WorkspaceMenu(state, onSelectWorkspace) { open ->
                                        NavigationRow(
                                            stringResource(R.string.switch_workspace),
                                            open,
                                            supportingText = roleLabel(state.workspace),
                                            enabled = !state.isSelectingWorkspace,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                composable(AppDestination.personalDetails(workspace)) { entry ->
                    ProfileDetailsRoute(
                        profileGraphModel(controller, entry, workspace, profileFactory),
                    ) {
                        controller.popBackStack()
                    }
                }
                composable(AppDestination.contactDetails(workspace)) { entry ->
                    ContactDetailsRoute(
                        profileGraphModel(controller, entry, workspace, profileFactory),
                    ) {
                        controller.popBackStack()
                    }
                }
            }
        }
    }
    LaunchedEffect(state.workspace) {
        val graph = AppDestination.profileGraph(state.workspace)
        if (controller.currentBackStackEntry
                ?.destination
                ?.hierarchy
                ?.none { it.route == graph } ==
            true
        ) {
            controller.navigate(graph) {
                popUpTo(controller.graph.id) { saveState = true }
                restoreState = true
                launchSingleTop = true
            }
        }
    }
}

/** Acquires one model from the profile parent entry, shared by its menu and child editors.
 * @param controller Account-scoped navigation controller.
 * @param entry Current child entry, used to remember the correct parent.
 * @param workspace Owning workspace's graph identity.
 * @param factory Explicit constructor dependencies.
 */
@Composable
private fun profileGraphModel(
    controller: NavHostController,
    entry: NavBackStackEntry,
    workspace: Role,
    factory: ViewModelProvider.Factory,
): ProfileViewModel {
    val parent =
        remember(entry) { controller.getBackStackEntry(AppDestination.profileGraph(workspace)) }
    return viewModel(viewModelStoreOwner = parent, factory = factory)
}
