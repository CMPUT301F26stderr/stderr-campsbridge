package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Connects one independent inventory editor to the actual caller's Back stack.
 * @param model Editor-entry owner; never borrowed from setup or detail.
 * @param onBack Pops only this editor after clean or confirmed abandonment.
 */
@Composable
fun AvailabilityRoute(
    model: AvailabilityViewModel,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val exit by model.exitRequested.collectAsStateWithLifecycle()
    val discard by model.discardPrompt.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(exit, lifecycle) {
        if (exit && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) onBack()
    }
    AvailabilityScreen(
        state,
        model::requestExit,
        model::retry,
        model::changeMonth,
        model::selectDates,
        model::setRequestedOffered,
        model::save,
        model::retryCalendar,
        model::dismissDiscard,
        model::confirmDiscard,
        discardPrompt = discard,
    )
}
