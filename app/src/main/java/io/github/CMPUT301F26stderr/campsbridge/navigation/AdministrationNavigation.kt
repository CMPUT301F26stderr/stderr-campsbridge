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

/** Registers Browse and its read-only profile/image children beneath one shared parent owner.
 * @param controller Current account navigation host.
 * @param state Confirmed session used by the shared workspace frame.
 * @param onSelectWorkspace Root-owned capability selection.
 * @param items Implemented admin primary peers only.
 * @param onSelectDestination Retained primary navigation.
 * @param factory Explicit repository/predicate dependencies for the Browse owner.
 */
internal fun NavGraphBuilder.administrationGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    items: List<WorkspaceNavigationItem>,
    onSelectDestination: (String) -> Unit,
    factory: ViewModelProvider.Factory,
) {
    navigation(
        startDestination = "${AppDestination.ADMIN_BROWSE}/list",
        route = AppDestination.ADMIN_BROWSE,
    ) {
        composable("${AppDestination.ADMIN_BROWSE}/list") {
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                stringResource(R.string.admin_browse),
                items,
                AppDestination.ADMIN_BROWSE,
                onSelectDestination,
            ) { EmptyContent(stringResource(R.string.feature_pending)) }
        }
        composable(AppDestination.ADMIN_PROFILE, arguments = listOf(navArgument("accountId") {})) {
            AppScreen(stringResource(R.string.admin_browse), onBack = {
                controller.popBackStack()
            }) { EmptyContent(stringResource(R.string.feature_pending)) }
        }
        composable(
            AppDestination.ADMIN_IMAGE,
            arguments =
                listOf(
                    navArgument("targetType") {
                    },
                    navArgument("targetId") {},
                ),
        ) {
            AppScreen(stringResource(R.string.admin_browse), onBack = {
                controller.popBackStack()
            }) { EmptyContent(stringResource(R.string.feature_pending)) }
        }
    }
}
