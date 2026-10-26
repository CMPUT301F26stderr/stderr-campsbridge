package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Shared lottery summary with caller-derived state and deadline text.
 * @param lottery Supplied lottery record.
 * @param status Explicit lifecycle/outcome label supplied by its controller.
 * @param deadline Formatted relevant deadline, including timezone where needed.
 * @param onClick Parent-owned detail action.
 * @param modifier Caller layout.
 */
@Composable
fun LotteryCard(
    lottery: Lottery,
    status: String,
    deadline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick, modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(
                status,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(lottery.title, style = MaterialTheme.typography.titleMedium)
            Text(deadline, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
