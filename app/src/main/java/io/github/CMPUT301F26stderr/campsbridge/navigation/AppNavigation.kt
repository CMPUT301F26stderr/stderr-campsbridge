package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState

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
            profileGraph(controller, workspace, state, onSelectWorkspace, profileFactory)
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
