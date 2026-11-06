package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.NotificationLogAudienceRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.NotificationLogDetailRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.NotificationLogRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.NotificationLogViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen

/** Registers the read-only event list and ID-only children beneath one independent log parent.
 * @param controller Current account navigation host.
 * @param state Confirmed session used by the shared workspace frame.
 * @param onSelectWorkspace Root-owned capability selection.
 * @param items Real admin primary peers.
 * @param onSelectDestination Retained primary selection.
 * @param factory Explicit log/content repository dependencies.
 */
internal fun NavGraphBuilder.notificationLogGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    items: List<WorkspaceNavigationItem>,
    onSelectDestination: (String) -> Unit,
    factory: ViewModelProvider.Factory,
) {
    navigation(
        startDestination = "${AppDestination.ADMIN_LOGS}/list",
        route = AppDestination.ADMIN_LOGS,
    ) {
        composable("${AppDestination.ADMIN_LOGS}/list") { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(AppDestination.ADMIN_LOGS) }
            val model: NotificationLogViewModel = viewModel(parent, factory = factory)
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                stringResource(R.string.admin_logs),
                items,
                AppDestination.ADMIN_LOGS,
                onSelectDestination,
            ) {
                NotificationLogRoute(model) { id ->
                    controller.navigate(AppDestination.adminNotification(id)) { launchSingleTop = true }
                }
            }
        }
        composable(
            AppDestination.ADMIN_NOTIFICATION,
            arguments =
                listOf(
                    navArgument(
                        "notificationId",
                    ) {
                    },
                ),
        ) { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(AppDestination.ADMIN_LOGS) }
            val model: NotificationLogViewModel = viewModel(parent, factory = factory)
            val id = requireNotNull(entry.arguments?.getString("notificationId"))
            AppScreen(stringResource(R.string.log_event_details), onBack = { controller.popBackStack() }) {
                NotificationLogDetailRoute(model, id) {
                    controller.navigate(AppDestination.notificationAudience(id)) { launchSingleTop = true }
                }
            }
        }
        composable(
            AppDestination.ADMIN_NOTIFICATION_AUDIENCE,
            arguments =
                listOf(
                    navArgument(
                        "notificationId",
                    ) {
                    },
                ),
        ) { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(AppDestination.ADMIN_LOGS) }
            val model: NotificationLogViewModel = viewModel(parent, factory = factory)
            val id = requireNotNull(entry.arguments?.getString("notificationId"))
            AppScreen(stringResource(R.string.log_recipients_title), onBack = { controller.popBackStack() }) {
                NotificationLogAudienceRoute(model, id)
            }
        }
    }
}

/** Binds selection to actual navigation identity across detail/audience content lifetimes.
 * Same-ID siblings retain the event; leaving that identity retires event and audience work.
 * @param controller Current account navigation host.
 * @param factory Existing log-parent repository dependencies.
 */
@Composable
internal fun NotificationLogSelectionBinding(
    controller: NavHostController,
    factory: ViewModelProvider.Factory,
) {
    val entry by controller.currentBackStackEntryAsState()
    val current = entry
    if (current?.destination?.hierarchy?.any { it.route == AppDestination.ADMIN_LOGS } == true) {
        val parent = remember(current) { controller.getBackStackEntry(AppDestination.ADMIN_LOGS) }
        val model: NotificationLogViewModel = viewModel(parent, factory = factory)
        DisposableEffect(model, controller) {
            val listener =
                NavController.OnDestinationChangedListener { _, destination, arguments ->
                    val route = destination.route
                    val id =
                        if (route == AppDestination.ADMIN_NOTIFICATION ||
                            route == AppDestination.ADMIN_NOTIFICATION_AUDIENCE
                        ) {
                            arguments?.getString("notificationId")
                        } else {
                            null
                        }
                    model.selectEvent(id)
                    model.setAudienceVisible(route == AppDestination.ADMIN_NOTIFICATION_AUDIENCE)
                }
            controller.addOnDestinationChangedListener(listener)
            onDispose {
                controller.removeOnDestinationChangedListener(listener)
                model.setAudienceVisible(false)
            }
        }
    }
}
