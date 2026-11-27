/*
 * Connects one saved picker to explicit actions, deadline checks and a consumed return.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import kotlinx.coroutines.delay

/** Connects one saved picker to explicit actions, deadline checks and a consumed return.
 * @param model Back-stack-owned local selection and required saved sources.
 * @param onExit Returns to the same saved lottery reader.
 */
@Composable
fun CollaboratingOwnersRoute(
    model: CollaboratingOwnersViewModel,
    onExit: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val completion by model.completion.collectAsStateWithLifecycle()
    val exit by model.exitRequested.collectAsStateWithLifecycle()
    val discard by model.discardPrompt.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(model, lifecycle, state.lottery?.publishedAt, state.lottery?.configuration?.registrationOpensAt) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            model.refreshTime()
            model.millisUntilFreeze()?.let {
                delay(it)
                model.refreshTime()
            }
        }
    }
    LaunchedEffect(lifecycle, completion, exit) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            if (completion != null) {
                model.acknowledgeCompletion()
                onExit()
            } else if (exit) {
                model.acknowledgeExit()
                onExit()
            }
        }
    }
    CollaboratingOwnersScreen(
        state,
        model::requestExit,
        model::setQuery,
        model::setOwnerSelected,
        model::save,
        model::retry,
    )
    if (discard) {
        ConfirmationDialog(
            title = stringResource(R.string.lottery_owners_discard),
            description = stringResource(R.string.lottery_owners_discard_body),
            onConfirm = model::confirmDiscard,
            onCancel = model::dismissDiscard,
            onDismiss = model::dismissDiscard,
            confirmLabel = stringResource(R.string.campsite_discard_confirm),
            destructive = true,
        )
    }
}
