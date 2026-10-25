package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
 * @param availabilityFactory Dependencies for an independent saved-campsite inventory editor.
 * @param campsiteSetupFactory Local editor dependencies for a new/saved draft.
 * @param exploreFactory Discovery/filter dependencies for the current account.
 * @param lotterySetupFactory New/saved lottery-setup dependencies for the current owner.
 * @param lotteryDetailFactory Saved owner-reader dependencies.
 * @param lotteryListFactory Owner inventory dependencies keyed by optional campsite filter.
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
    availabilityFactory: (String) -> ViewModelProvider.Factory,
    exploreFactory: ViewModelProvider.Factory,
    lotterySetupFactory: (String?) -> ViewModelProvider.Factory,
    lotteryDetailFactory: (String) -> ViewModelProvider.Factory,
    lotteryListFactory: (String?) -> ViewModelProvider.Factory,
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
                AppDestination.OWNER_LOTTERIES,
                stringResource(R.string.lotteries),
                Icons.Default.DateRange,
            ),
            WorkspaceNavigationItem(
                AppDestination.profileGraph(Role.OWNER),
                stringResource(R.string.profile_title),
                Icons.Default.Person,
            ),
        )
    val entrantItems =
        listOf(
            WorkspaceNavigationItem(
                AppDestination.EXPLORE,
                stringResource(R.string.discovery_title),
                Icons.Default.Home,
            ),
            WorkspaceNavigationItem(
                AppDestination.profileGraph(Role.ENTRANT),
                stringResource(R.string.profile_title),
                Icons.Default.Person,
            ),
        )
    val selectedItems =
        when (state.workspace) {
            Role.OWNER -> ownerItems
            Role.ENTRANT -> entrantItems
            Role.ADMIN -> emptyList()
        }
    val selectPrimary: (String) -> Unit = { destination ->
        if (selectedItems.any { it.id == destination }) {
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

    /** Opens the retained primary for a confirmed workspace after any editor-owned handoff.
     * @param workspace Root-confirmed capability selection.
     */
    fun openWorkspace(workspace: Role) {
        val prefix = workspace.name.lowercase(Locale.ROOT) + "/"
        if (controller.currentBackStackEntry?.destination?.hierarchy?.none {
                it.route?.startsWith(prefix) ==
                    true
            } ==
            true
        ) {
            controller.navigate(selectedPrimaries[workspace.name] ?: defaultPrimary(workspace)) {
                popUpTo(controller.graph.id) { saveState = true }
                restoreState = true
                launchSingleTop = true
            }
        }
    }
    NavHost(controller, defaultPrimary(initialWorkspace), modifier) {
        lotterySetupGraph(controller, lotterySetupFactory, lotteryDetailFactory)
        lotteryListGraph(
            controller,
            state,
            onSelectWorkspace,
            ownerItems,
            selectPrimary,
            lotteryListFactory,
        )
        for (workspace in Role.entries) {
            profileGraph(
                controller,
                workspace,
                state,
                onSelectWorkspace,
                profileFactory,
                when (workspace) {
                    Role.OWNER -> ownerItems
                    Role.ENTRANT -> entrantItems
                    Role.ADMIN -> emptyList()
                },
                selectPrimary,
                { openWorkspace(Role.OWNER) },
            )
        }
        campsiteReadGraph(
            controller,
            Role.ADMIN,
            CampsiteDetailContext.INSPECT,
            campsiteDetailFactory,
        )
        discoveryGraph(
            controller,
            state,
            onSelectWorkspace,
            entrantItems,
            selectPrimary,
            exploreFactory,
            campsiteDetailFactory,
        )
        campsiteGraph(
            controller,
            state,
            onSelectWorkspace,
            ownerItems,
            selectPrimary,
            campsiteListFactory,
            campsiteDetailFactory,
            campsiteSetupFactory,
            availabilityFactory,
        )
    }
    LaunchedEffect(state.workspace, state.isSelectingWorkspace) {
        if (state.isSelectingWorkspace) return@LaunchedEffect
        // The introduction consumes its request and removes itself before saving the old stack.
        val hostingIntroduction =
            Role.entries.any {
                controller.currentDestination?.route ==
                    AppDestination.ownerIntroduction(it)
            }
        if (state.workspace == Role.OWNER && hostingIntroduction) return@LaunchedEffect
        openWorkspace(state.workspace)
    }
}

/** Selects a real default only when the workspace has no saved primary selection.
 * @param workspace Confirmed selected workspace.
 */
private fun defaultPrimary(workspace: Role): String =
    when (workspace) {
        Role.OWNER -> AppDestination.ownerCampsites()
        Role.ENTRANT -> AppDestination.EXPLORE
        Role.ADMIN -> AppDestination.profileGraph(workspace)
    }
