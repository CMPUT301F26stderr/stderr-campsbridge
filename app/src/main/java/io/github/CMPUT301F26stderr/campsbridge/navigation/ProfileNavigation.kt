package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceMenu
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.common.roleLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PoliciesScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyReaderScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.policyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ContactDetailsRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileDetailsRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.SettingsScreen

/** Registers one workspace's profile/menu/editors and offline policy readers.
 * @param controller Account-scoped host controller.
 * @param workspace Graph's stable workspace identity.
 * @param state Confirmed session used by the primary frame and role picker.
 * @param onSelectWorkspace Root-owned workspace selection.
 * @param profileFactory Dependencies for the shared parent profile model.
 * @param navigationItems Registered peer destinations only.
 * @param onSelectDestination Parent-owned primary navigation.
 */
internal fun NavGraphBuilder.profileGraph(
    controller: NavHostController,
    workspace: Role,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    profileFactory: ViewModelProvider.Factory,
    navigationItems: List<WorkspaceNavigationItem>,
    onSelectDestination: (String) -> Unit,
) {
    navigation(
        AppDestination.profileMenu(workspace),
        AppDestination.profileGraph(workspace),
    ) {
        composable(AppDestination.profileMenu(workspace)) { entry ->
            val model = profileGraphModel(controller, entry, workspace, profileFactory)
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                navigationItems = navigationItems,
                selectedDestination = AppDestination.profileGraph(workspace),
                onSelectDestination = onSelectDestination,
            ) {
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
                    onSettings = {
                        controller.navigate(AppDestination.settings(workspace)) {
                            launchSingleTop =
                                true
                        }
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
        composable(AppDestination.settings(workspace)) {
            SettingsScreen(
                onPolicies = {
                    controller.navigate(AppDestination.policies(workspace)) {
                        launchSingleTop =
                            true
                    }
                },
                onBack = { controller.popBackStack() },
            )
        }
        composable(AppDestination.policies(workspace)) {
            PoliciesScreen(
                onOpen = { policy ->
                    controller.navigate(AppDestination.policyReader(workspace, policy)) {
                        launchSingleTop =
                            true
                    }
                },
                onBack = { controller.popBackStack() },
            )
        }
        composable(
            AppDestination.policyPattern(workspace),
            arguments =
                listOf(
                    navArgument("version") {
                        defaultValue =
                            ""
                    },
                ),
        ) { entry ->
            val policy =
                PolicyId.entries.firstOrNull {
                    it.routeKey ==
                        entry.arguments?.getString("policyKey")
                }
            val content =
                policy?.let {
                    policyContent(
                        it,
                        entry.arguments?.getString("version").orEmpty(),
                    )
                }
            PolicyReaderScreen(content, onBack = { controller.popBackStack() })
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
