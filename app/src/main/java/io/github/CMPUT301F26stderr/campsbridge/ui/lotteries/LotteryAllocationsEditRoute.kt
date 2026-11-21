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

/** Connects retained owned offers to explicit actions and one return to the saved reader.
 * @param model Graph-owned saved editor.
 * @param onExit Removes this editor graph after save or confirmed discard.
 * @param onAdd Opens the owned campsite picker.
 * @param onCalendar Opens the already-initialized working calendar.
 */
@Composable
fun LotteryAllocationsEditRoute(
    model: LotteryAllocationsEditViewModel,
    onExit: () -> Unit,
    onAdd: () -> Unit,
    onCalendar: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val saving by model.isSaving.collectAsStateWithLifecycle()
    val completion by model.completion.collectAsStateWithLifecycle()
    val exit by model.exitRequested.collectAsStateWithLifecycle()
    val discard by model.discardPrompt.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    AllocationEditLifecycle(model, state)
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
    LotteryAllocationsEditScreen(
        state,
        saving,
        model::requestExit,
        model::retry,
        model::save,
        onAdd,
        { if (model.beginAllocation(it)) onCalendar() },
        model::removeAllocation,
    )
    if (discard) {
        ConfirmationDialog(
            title = stringResource(R.string.lottery_edit_discard_title),
            description = stringResource(R.string.lottery_allocations_discard_body),
            onConfirm = model::confirmDiscard,
            onCancel = model::dismissDiscard,
            onDismiss = model::dismissDiscard,
            confirmLabel = stringResource(R.string.campsite_discard_confirm),
            destructive = true,
        )
    }
}

/** Connects the owned listing picker without applying or saving the selected row.
 * @param model Same graph owner as the applied set and calendar.
 * @param onBack Returns to applied offers unchanged.
 * @param onCalendar Replaces this picker with its initialized calendar child.
 */
@Composable
fun LotteryAllocationChoiceRoute(
    model: LotteryAllocationsEditViewModel,
    onBack: () -> Unit,
    onCalendar: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    AllocationEditLifecycle(model, state)
    LotteryAllocationChoiceScreen(state, onBack, model::retry) { if (model.beginAllocation(it)) onCalendar() }
}

/** Reuses creation's actual calendar content with this saved parent's temporary selection.
 * @param model Same graph owner as the applied set.
 * @param onBack Pops only the working child after Apply or Cancel.
 */
@Composable
fun LotteryAllocationsCalendarRoute(
    model: LotteryAllocationsEditViewModel,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    AllocationEditLifecycle(model, state)
    val content = state as? LotteryAllocationsEditUiState.Content
    val choices =
        when (state) {
            LotteryAllocationsEditUiState.Loading -> LotteryCampsiteChoices.Loading
            LotteryAllocationsEditUiState.Failure -> LotteryCampsiteChoices.Failure
            else -> LotteryCampsiteChoices.Content(content?.campsites.orEmpty().filter { it.isPublished })
        }
    LotteryAllocationScreen(
        choices,
        content?.selectedCampsiteId,
        content?.allocation,
        {
            model.cancelAllocation()
            onBack()
        },
        { if (model.applyAllocation()) onBack() },
        model::changeAllocationMonth,
        model::selectAllocationDates,
        model::retryInventory,
        model::retry,
        enabled = content?.isEditable == true,
    )
}

/** Rechecks membership deadlines on resume and at the published registration opening.
 * @param model Owner of editing rules and the injected clock.
 * @param state Latest saved context controlling the next boundary.
 */
@Composable
private fun AllocationEditLifecycle(
    model: LotteryAllocationsEditViewModel,
    state: LotteryAllocationsEditUiState,
) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val lottery = (state as? LotteryAllocationsEditUiState.Content)?.lottery
    LaunchedEffect(model, lifecycle, lottery?.publishedAt, lottery?.configuration?.registrationOpensAt) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            model.refreshTime()
            model.millisUntilFreeze()?.let {
                delay(it)
                model.refreshTime()
            }
        }
    }
}
