package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Connects local parent input to ordinary content and one explicit allocation child.
 * @param model Graph-owned setup model.
 * @param onExit Removes this abandoned parent and returns to its actual caller.
 * @param onSaved Removes this setup parent and opens the actual saved target.
 * @param onOpenAllocation Opens the registered child after working state is initialized.
 */
@Composable
fun LotterySetupRoute(
    model: LotterySetupViewModel,
    onExit: () -> Unit,
    onOpenAllocation: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val completion by model.completion.collectAsStateWithLifecycle()
    val exit by model.exitRequested.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(exit, completion, lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            if (completion != null) {
                onSaved(checkNotNull(completion))
                model.acknowledgeCompletion()
            } else if (exit) {
                onExit()
            }
        }
    }
    LotterySetupScreen(
        state,
        model.currentScheduleMonth(),
        model::back,
        model::next,
        model::requestExit,
        model::dismissDiscard,
        model::confirmDiscard,
        model::editTitle,
        model::editDescription,
        model::chooseCampsite,
        { if (model.beginAllocation()) onOpenAllocation() },
        model::retryChoices,
        model::editScheduleDate,
        model::editScheduleTime,
        model::editWinnerCount,
        model::editStep,
        model::confirmScheduleZone,
        onSaveDraft = model::saveDraft,
        onPublish = model::publishLottery,
        onRetrySave = model::retrySave,
        onRetryLoad = model::retryLoad,
    )
}

/** Applies or discards working input, then lets one lifecycle-aware effect return to the parent.
 * @param model Same setup owner used by the steps destination.
 * @param onBack Pops this child after its working allocation is closed.
 */
@Composable
fun LotteryAllocationRoute(
    model: LotterySetupViewModel,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(state.allocation == null, lifecycle) {
        if (state.allocation == null && lifecycle == Lifecycle.State.RESUMED) onBack()
    }
    BackHandler { model.cancelAllocation() }
    LotteryAllocationScreen(
        state,
        model::cancelAllocation,
        { model.applyAllocation() },
        model::changeAllocationMonth,
        model::toggleAllocationDate,
        model::retryAllocation,
        model::retryChoices,
    )
}
