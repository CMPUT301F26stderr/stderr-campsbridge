package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Connects the retained winning reader to campsite selection without another data owner.
 * @param model Same saved lottery parent used by the overview.
 * @param onBack Returns to that overview through shared Back input.
 * @param onChoose Opens the existing booking graph with actual saved IDs.
 */
@Composable
fun LotteryCampsiteChoiceRoute(
    model: LotteryDetailViewModel,
    onBack: () -> Unit,
    onChoose: (String, String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val decline by model.decline.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED) model.refreshTime()
    }
    LotteryCampsiteChoiceScreen(
        state,
        decline,
        onBack,
        model::retry,
        model::retryOffers,
        model::retryOpportunity,
        onChoose,
    )
}
