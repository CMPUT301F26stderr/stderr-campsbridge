package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Direct supplied-input inspection for schedule validation, without lottery publication.
 * @param bindings Actual validator reference installed by the handoff.
 * @param onBack Returns to the catalogue.
 */
@Composable
internal fun LotteryWindowTaskExample(
    bindings: TaskExampleBindings,
    onBack: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val labels =
        listOf(
            R.string.task_window_valid,
            R.string.task_window_registration,
            R.string.task_window_overlap,
            R.string.task_window_booking,
        )
    val base = TaskExampleData.now
    val inputs =
        listOf(
            listOf(base, base.plusSeconds(3600), base.plusSeconds(3600), base.plusSeconds(7200)),
            listOf(base.plusSeconds(3600), base, base.plusSeconds(3600), base.plusSeconds(7200)),
            listOf(base, base.plusSeconds(3600), base, base.plusSeconds(7200)),
            listOf(base, base.plusSeconds(3600), base.plusSeconds(7200), base.plusSeconds(3600)),
        )[selected]
    val validator = bindings.validateWindows
    val result =
        remember(
            selected,
            validator,
        ) { validator?.invoke(inputs[0], inputs[1], inputs[2], inputs[3]) }
    val output =
        when (result) {
            LotteryWindowInput.Valid -> "Valid"
            LotteryWindowInput.InvalidRegistration -> "InvalidRegistration"
            LotteryWindowInput.BookingBeforeRegistrationClose -> "BookingBeforeRegistrationClose"
            LotteryWindowInput.InvalidBooking -> "InvalidBooking"
            null -> null
        }
    val expected =
        listOf(
            "Valid",
            "InvalidRegistration",
            "BookingBeforeRegistrationClose",
            "InvalidBooking",
        )[selected]
    AppScreen(stringResource(R.string.task_windows), onBack = onBack) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            labels.forEachIndexed { index, label ->
                TextButton(
                    { selected = index },
                    Modifier.fillMaxWidth(),
                ) { Text(stringResource(label)) }
            }
            Text(
                stringResource(
                    R.string.task_window_instants,
                    *inputs.map { it.toString() }.toTypedArray(),
                ),
            )
            Text(stringResource(R.string.task_window_expected, expected))
            if (output == null) {
                Text(stringResource(R.string.task_pending))
            } else {
                Text(stringResource(R.string.task_result, output))
            }
        }
    }
}
