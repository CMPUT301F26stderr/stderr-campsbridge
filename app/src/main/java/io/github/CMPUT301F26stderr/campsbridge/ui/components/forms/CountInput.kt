/*
 * One editable count with integrated bounded controls; invalid raw input remains editable.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.forms

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionTooltip

/** One editable count with integrated bounded controls; invalid raw input remains editable.
 * @param input Caller-owned text, including invalid intermediate input.
 * @param count Parsed value supplied by the caller, or null when input is not a count.
 * @param onInput Requests raw input changes without parsing or saving.
 * @param label Persistent field label.
 * @param actionLabel Name used by the increase/decrease accessibility actions.
 * @param minimum Inclusive permitted lower bound.
 * @param maximum Inclusive permitted upper bound.
 * @param modifier Caller layout and input test semantics.
 * @param enabled Whether editing and bounded changes are permitted.
 * @param supportingText Current capacity or other field guidance.
 */
@Composable
fun CountInput(
    input: String,
    count: Long?,
    onInput: (String) -> Unit,
    label: String,
    actionLabel: String,
    minimum: Long,
    maximum: Long,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    require(minimum <= maximum)
    val bounded = count?.takeIf { it in minimum..maximum }
    val decrease = stringResource(R.string.decrease_count, actionLabel)
    val increase = stringResource(R.string.increase_count, actionLabel)
    AppTextField(
        input,
        onInput,
        label,
        modifier,
        enabled = enabled,
        supportingText = supportingText,
        prefix = {
            ActionTooltip(decrease) {
                IconButton(
                    onClick = { bounded?.let { onInput((it - 1).toString()) } },
                    enabled = enabled && bounded != null && bounded > minimum,
                ) {
                    Icon(painterResource(R.drawable.ic_remove), decrease)
                }
            }
        },
        suffix = {
            ActionTooltip(increase) {
                IconButton(
                    onClick = { bounded?.let { onInput((it + 1).toString()) } },
                    enabled = enabled && bounded != null && bounded < maximum,
                ) {
                    Icon(Icons.Default.Add, increase)
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
