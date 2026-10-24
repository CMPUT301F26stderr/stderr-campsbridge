package io.github.CMPUT301F26stderr.campsbridge.ui.components.layout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Presents one focused wizard step with scrolling content and a reachable bottom action.
 * @param title Localized step title.
 * @param progress Localized position, such as Step 1 of 3.
 * @param onBack Return action.
 * @param onNext Explicit continuation action.
 * @param modifier Caller layout.
 * @param nextEnabled Whether input permits continuation.
 * @param isSubmitting Whether continuation is pending.
 * @param content Step fields/content without application wiring.
 * @param nextLabel Continuation text, including final save/publish actions.
 * @param steps Optional segmented progress matching the flow's actual step count.
 * @param onSkip Optional explicit skip action for optional input.
 * @param onCancel Optional cancellation action in the app bar.
 */
@Composable
fun StepScaffold(
    title: String,
    progress: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    nextEnabled: Boolean = true,
    isSubmitting: Boolean = false,
    nextLabel: String = stringResource(R.string.next),
    steps: StepProgress? = null,
    onSkip: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = isSubmitting) {}
    AppScreen(
        title = title,
        modifier = modifier,
        onBack = onBack,
        backEnabled = !isSubmitting,
        actions = {
            if (onCancel != null) {
                TextButton(onClick = onCancel, enabled = !isSubmitting) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        bottomContent = {
            Column(Modifier.fillMaxWidth().padding(Spacing.screen)) {
                AppActionButton(
                    nextLabel,
                    onNext,
                    Modifier.fillMaxWidth(),
                    nextEnabled,
                    isSubmitting,
                )
                if (onSkip !=
                    null
                ) {
                    TextButton(onSkip, Modifier.fillMaxWidth(), enabled = !isSubmitting) {
                        Text(stringResource(R.string.skip_for_now))
                    }
                }
            }
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            Text(progress)
            steps?.let { StepIndicator(it) }
            content()
            Spacer(Modifier.height(Spacing.large))
        }
    }
}

/** Current wizard position, independent of feature navigation.
 * @param current One-based current step.
 * @param total Actual number of steps in this branch.
 */
data class StepProgress(
    val current: Int,
    val total: Int,
) {
    init {
        require(total > 0 && current in 1..total)
    }
}

/** Segments composed from Material surfaces with one accessibility progress value.
 * @param progress Current and total step counts.
 */
@Composable
private fun StepIndicator(progress: StepProgress) {
    Row(
        Modifier.fillMaxWidth().semantics {
            progressBarRangeInfo =
                ProgressBarRangeInfo(progress.current.toFloat(), 0f..progress.total.toFloat())
        },
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        repeat(progress.total) { index ->
            Surface(
                Modifier.weight(1f).height(4.dp),
                shape = MaterialTheme.shapes.small,
                color =
                    if (index < progress.current) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
            ) {}
        }
    }
}
