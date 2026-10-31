package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceNavigationItem
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

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
        composable("${AppDestination.ADMIN_LOGS}/list") {
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                stringResource(R.string.admin_logs),
                items,
                AppDestination.ADMIN_LOGS,
                onSelectDestination,
            ) { EmptyContent(stringResource(R.string.feature_pending)) }
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
        ) {
            AppScreen(stringResource(R.string.admin_logs), onBack = {
                controller.popBackStack()
            }) { EmptyContent(stringResource(R.string.feature_pending)) }
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
        ) {
            AppScreen(stringResource(R.string.admin_logs), onBack = {
                controller.popBackStack()
            }) { EmptyContent(stringResource(R.string.feature_pending)) }
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
    // Selection begins when the log controller is implemented.
}
