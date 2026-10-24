package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileRoute

/**
 * Hosts implemented destinations. The root keys this graph by account identity.
 * @param profileFactory Dependencies for the existing-profile destination.
 * @param modifier Workspace layout and insets.
 */
@Composable
fun AppNavigation(
    profileFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val controller = rememberNavController()
    NavHost(controller, AppDestination.PROFILE, modifier) {
        composable(AppDestination.PROFILE) { ProfileRoute(profileFactory) }
    }
}
