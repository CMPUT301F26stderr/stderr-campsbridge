package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import java.util.Locale

/**
 * Hosts implemented destinations. The root keys this graph by account identity.
 * @param profileFactory Dependencies for the existing-profile destination.
 * @param state Ready session; workspace is distinct from granted capabilities.
 * @param onSelectWorkspace Root-owned capability-limited selection.
 * @param campsiteListFactory Owner inventory dependencies.
 * @param campsiteDetailFactory Saved reader dependencies keyed by identity/context.
 * @param campsiteSetupFactory Local editor dependencies for a new/saved draft.
 * @param modifier Host layout; child screens own their own insets.
 */
@Composable
fun AppNavigation(
    profileFactory: ViewModelProvider.Factory,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    campsiteListFactory: ViewModelProvider.Factory,
    campsiteDetailFactory: (String, CampsiteDetailContext) -> ViewModelProvider.Factory,
    campsiteSetupFactory: (String?) -> ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val controller = rememberNavController()
    val initialWorkspace = remember { state.workspace }
    var selectedPrimaries by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    val ownerItems =
        listOf(
            WorkspaceNavigationItem(
                AppDestination.ownerCampsites(),
                stringResource(R.string.campsites),
                Icons.Default.Home,
            ),
            WorkspaceNavigationItem(
                AppDestination.profileGraph(Role.OWNER),
                stringResource(R.string.profile_title),
                Icons.Default.Person,
            ),
        )
    val selectPrimary: (String) -> Unit = { destination ->
        if (state.workspace == Role.OWNER && ownerItems.any { it.id == destination }) {
            selectedPrimaries = selectedPrimaries + (state.workspace.name to destination)
            if (controller.currentDestination?.hierarchy?.none { it.route == destination } ==
                true
            ) {
                controller.navigate(destination) {
                    popUpTo(controller.graph.id) { saveState = true }
                    restoreState = true
                    launchSingleTop = true
                }
            }
        }
    }
    NavHost(controller, defaultPrimary(initialWorkspace), modifier) {
        for (workspace in Role.entries) {
            profileGraph(
                controller,
                workspace,
                state,
                onSelectWorkspace,
                profileFactory,
                if (workspace == Role.OWNER) ownerItems else emptyList(),
                selectPrimary,
            )
        }
        campsiteGraph(
            controller,
            state,
            onSelectWorkspace,
            ownerItems,
            selectPrimary,
            campsiteListFactory,
            campsiteDetailFactory,
            campsiteSetupFactory,
        )
    }
    LaunchedEffect(state.workspace) {
        val prefix = state.workspace.name.lowercase(Locale.ROOT) + "/"
        if (controller.currentBackStackEntry
                ?.destination
                ?.hierarchy
                ?.none { it.route?.startsWith(prefix) == true } ==
            true
        ) {
            controller.navigate(
                selectedPrimaries[state.workspace.name] ?: defaultPrimary(state.workspace),
            ) {
                popUpTo(controller.graph.id) { saveState = true }
                restoreState = true
                launchSingleTop = true
            }
        }
    }
}

/** Selects a real default only when the workspace has no saved primary selection.
 * @param workspace Confirmed selected workspace.
 */
private fun defaultPrimary(workspace: Role): String =
    if (workspace ==
        Role.OWNER
    ) {
        AppDestination.ownerCampsites()
    } else {
        AppDestination.profileGraph(workspace)
    }
