package io.github.CMPUT301F26stderr.campsbridge.ui.components.layout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
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
 * @param secondaryLabel Optional review action text, supplied with onSecondaryAction.
 * @param onSecondaryAction Explicit alternative to the primary action, such as saving a draft.
 * @param secondaryEnabled Whether the secondary action's own prerequisites permit submission.
 * @param submittingAction Action whose progress is shown while the shared submitting guard is set.
 * @param exitEnabled Whether leaving the step is safe while an idle action awaits recovery.
 * @param stepLabel Optional short step name beside the progress count.
 * @param contentArrangement Places fields at the top or centers short introduction content.
 * @throws IllegalArgumentException If actions are incomplete, incompatible or unavailable.
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
    secondaryLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryEnabled: Boolean = true,
    submittingAction: StepAction = StepAction.PRIMARY,
    exitEnabled: Boolean = true,
    stepLabel: String? = null,
    contentArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.medium),
    content: @Composable ColumnScope.() -> Unit,
) {
    require((secondaryLabel == null) == (onSecondaryAction == null)) {
        "A secondary action needs both a label and a callback"
    }
    require(onSecondaryAction == null || onSkip == null) {
        "A step uses either Skip or a secondary review action"
    }
    require(
        !isSubmitting || submittingAction != StepAction.SECONDARY || onSecondaryAction != null,
    ) {
        "Secondary submission needs a secondary action"
    }
    BackHandler(enabled = isSubmitting || !exitEnabled) {}
    AppScreen(
        title = title,
        modifier = modifier,
        onBack = onBack,
        backEnabled = !isSubmitting && exitEnabled,
        actions = {
            if (onCancel != null) {
                TextButton(onClick = onCancel, enabled = !isSubmitting && exitEnabled) {
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
                    enabled = nextEnabled && !isSubmitting,
                    isSubmitting = isSubmitting && submittingAction == StepAction.PRIMARY,
                )
                if (secondaryLabel != null && onSecondaryAction != null) {
                    Spacer(Modifier.height(Spacing.small))
                    AppActionButton(
                        secondaryLabel,
                        onSecondaryAction,
                        Modifier.fillMaxWidth(),
                        enabled = secondaryEnabled && !isSubmitting,
                        isSubmitting = isSubmitting && submittingAction == StepAction.SECONDARY,
                        style = ActionStyle.SECONDARY,
                    )
                }
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
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        progress,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    stepLabel?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                steps?.let { StepIndicator(it) }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(bottom = Spacing.large),
                    verticalArrangement = contentArrangement,
                    content = content,
                )
            }
        }
    }
}

/** Identifies the initiating footer action without creating independent concurrent operations. */
enum class StepAction { PRIMARY, SECONDARY }

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

/** Shows the shared review hierarchy without a ViewModel, repository or simulated save. */
@Preview(name = "Review", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(
    name = "Review large text",
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
    fontScale = 2f,
)
@Composable
private fun StepReviewPreview() {
    CampsbridgeTheme {
        StepScaffold(
            stringResource(R.string.review),
            stringResource(R.string.onboarding_step, 8, 8),
            {},
            {},
            nextLabel = stringResource(R.string.publish),
            steps = StepProgress(8, 8),
            secondaryLabel = stringResource(R.string.save_draft),
            onSecondaryAction = {},
        ) {
            Text(stringResource(R.string.campsite))
        }
    }
}
