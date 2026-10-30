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

/** Registers one ID-scoped winner parent with four steps and its packaged-policy child.
 * @param controller Current account navigation host.
 * @param factory Explicit dependencies keyed by actual lottery and campsite identities.
 */
internal fun NavGraphBuilder.bookingGraph(
    controller: NavHostController,
    factory: (String, String) -> ViewModelProvider.Factory,
) {
    val graph = AppDestination.BOOKING
    navigation(
        startDestination = "$graph/steps",
        route = graph,
        arguments = listOf(navArgument("lotteryId") {}, navArgument("campsiteId") {}),
    ) {
        for (page in listOf("steps", "policy")) {
            composable("$graph/$page") {
                AppScreen(stringResource(R.string.feature_pending), onBack = {
                    if (page ==
                        "steps"
                    ) {
                        controller.popBackStack(graph, true)
                    } else {
                        controller.popBackStack()
                    }
                }) { EmptyContent(stringResource(R.string.feature_pending)) }
            }
        }
    }
}
