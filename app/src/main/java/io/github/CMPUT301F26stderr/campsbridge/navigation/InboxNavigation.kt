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

/** Registers two workspace entries sharing one lazily acquired account-root receipt owner.
 * @param controller Current account's navigation host.
 * @param state Actual ready identity and workspace state.
 * @param onSelectWorkspace Explicit root capability selection.
 * @param ownerItems Registered owner peers, including this primary.
 * @param entrantItems Registered entrant peers, including this primary.
 * @param onSelectDestination Root-owned retained primary navigation.
 * @param factory Current account's receipt/content dependencies.
 */
internal fun NavGraphBuilder.inboxGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    ownerItems: List<WorkspaceNavigationItem>,
    entrantItems: List<WorkspaceNavigationItem>,
    onSelectDestination: (String) -> Unit,
    factory: ViewModelProvider.Factory,
) {
    for (role in listOf(Role.ENTRANT, Role.OWNER)) {
        val route = AppDestination.inbox(role)
        composable(route) {
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                stringResource(R.string.inbox_title),
                if (role ==
                    Role.OWNER
                ) {
                    ownerItems
                } else {
                    entrantItems
                },
                route,
                onSelectDestination,
            ) { EmptyContent(stringResource(R.string.feature_pending)) }
        }
    }
}
