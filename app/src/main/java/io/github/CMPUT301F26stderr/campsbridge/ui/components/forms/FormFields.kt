/*
 * Presents caller-owned input with a visible label/error and standard IME behavior.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.forms

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale
import kotlinx.coroutines.launch

/** Changes only when a form explicitly submits, so an unchanged error can be revealed again. */
internal val LocalValidationAttempt = staticCompositionLocalOf { 0 }

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
 * @param reserveSupportingSpace Keeps one helper line available before validation.
 * @param revealError Brings this field into view when its validation error appears; select the first invalid field.
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
    reserveSupportingSpace: Boolean = false,
    revealError: Boolean = false,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val validationAttempt = LocalValidationAttempt.current
    LaunchedEffect(revealError, errorMessage, validationAttempt) {
        if (revealError && errorMessage != null) {
            withFrameNanos { }
            bringIntoView.bringIntoView()
        }
    }
    val message = errorMessage ?: supportingText
    OutlinedTextField(
        value,
        onValueChange,
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp * LocalControlScale.current)
            .bringIntoViewRequester(bringIntoView)
            .semantics { errorMessage?.let { error(it) } },
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        prefix = prefix,
        suffix = suffix,
        isError = errorMessage != null,
        supportingText =
            if (message != null || reserveSupportingSpace) {
                {
                    Text(
                        message.orEmpty(),
                        Modifier
                            .animateContentSize(finishedListener = { _, _ ->
                                if (revealError && errorMessage != null) {
                                    scope.launch { bringIntoView.bringIntoView() }
                                }
                            })
                            .then(
                                if (message == null) {
                                    Modifier.clearAndSetSemantics { }
                                } else {
                                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                },
                            ),
                    )
                }
            } else {
                null
            },
        keyboardOptions =
            if (singleLine && keyboardOptions.imeAction == ImeAction.Default) {
                keyboardOptions.copy(imeAction = ImeAction.Done)
            } else {
                keyboardOptions
            },
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.extraSmall,
    )
}
