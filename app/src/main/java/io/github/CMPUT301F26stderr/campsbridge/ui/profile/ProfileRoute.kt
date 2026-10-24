package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Connects graph-owned profile state to content without constructing repositories.
 * @param factory Explicit feature dependencies.
 * @param modifier Navigation host layout.
 */
@Composable
fun ProfileRoute(
    factory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val model: ProfileViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    ProfileScreen(state, model::retry, modifier)
}
