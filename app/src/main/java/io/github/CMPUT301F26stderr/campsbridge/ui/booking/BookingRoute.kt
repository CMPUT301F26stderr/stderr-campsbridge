package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Binds one actual graph owner; only a live resumed route delivers its current handoff.
 * @param model Same winner/draft owner retained through step and policy navigation.
 * @param onExit Removes the abandoned wizard and returns to its real caller.
 * @param onPolicy Opens the registered policy child without changing the draft.
 * @param onSaved Removes the wizard and opens only the actual consuming saved identity.
 */
@Composable
fun BookingRoute(
    model: BookingViewModel,
    onExit: () -> Unit,
    onPolicy: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val photo by model.photoState.collectAsStateWithLifecycle()
    val discard by model.discardPrompt.collectAsStateWithLifecycle()
    val exit by model.exitRequested.collectAsStateWithLifecycle()
    val completion by model.completion.collectAsStateWithLifecycle()
    val submission by model.submission.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val lifecycle by owner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    DisposableEffect(model) {
        model.setDatesVisible(true)
        onDispose { model.setDatesVisible(false) }
    }
    LaunchedEffect(lifecycle) {
        if (owner.lifecycle.currentState == Lifecycle.State.RESUMED) model.refreshTime()
    }
    LaunchedEffect(completion, exit, lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED &&
            owner.lifecycle.currentState == Lifecycle.State.RESUMED
        ) {
            val savedId = completion
            if (savedId != null && model.completion.value == savedId) {
                onSaved(savedId)
                model.acknowledgeCompletion()
            } else if (exit && model.exitRequested.value) {
                onExit()
                model.acknowledgeExit()
            }
        }
    }
    BookingScreen(
        state,
        discard,
        exit,
        model::back,
        model::next,
        model::requestExit,
        model::dismissDiscard,
        model::confirmDiscard,
        model::changeMonth,
        model::selectDate,
        model::clearDates,
        model::editGuests,
        model::acceptTerms,
        model::editStep,
        model::acknowledgeQuote,
        {
            if (owner.lifecycle.currentState == Lifecycle.State.RESUMED && model.canReadPolicy()) {
                onPolicy()
            }
        },
        model::retry,
        model::retryVisibleInventory,
        model::retrySelectedInventory,
        model::requestReservationOpen,
        submission,
        model::confirm,
        model::retryRecovery,
        photo,
        model::retryPhoto,
    )
}
