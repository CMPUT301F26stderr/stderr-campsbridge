package io.github.CMPUT301F26stderr.campsbridge.ui.components.forms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale

/**
 * Presents caller-owned input with a visible label/error and standard IME behavior.
 * @param value Current text.
 * @param onValueChange Edit callback; does not save to a repository.
 * @param label Localized persistent field label.
 * @param modifier Caller layout.
 * @param errorMessage Localized validation feedback, or null.
 * @param enabled Whether editing is permitted.
 * @param supportingText Normal helper text, replaced by an error when present.
 * @param singleLine Whether input stays on one line.
 * @param minLines Minimum visible lines for multiline input.
 * @param maxLines Maximum visible lines before internal scrolling.
 * @param prefix Optional unit or currency prefix.
 * @param suffix Optional unit suffix.
 * @param keyboardOptions Input/IME configuration.
 * @param keyboardActions Explicit IME callbacks.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    enabled: Boolean = true,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 5,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value,
        onValueChange,
        modifier.fillMaxWidth().heightIn(min = 56.dp * LocalControlScale.current),
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        prefix = prefix,
        suffix = suffix,
        isError = errorMessage != null,
        supportingText = (errorMessage ?: supportingText)?.let { message -> { Text(message) } },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.medium,
    )
}
