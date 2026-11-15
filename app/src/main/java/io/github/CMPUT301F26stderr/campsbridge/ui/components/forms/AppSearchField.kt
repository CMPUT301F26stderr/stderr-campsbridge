/*
 * Shared rounded search input with an optional explicit submit action alongside the query.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.forms

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale

/** Shared rounded search input with an optional explicit submit action alongside the query.
 * @param value Caller-owned query text.
 * @param onValueChange Reports raw query edits without submitting or persisting.
 * @param label Persistent localized input label, retained while text is entered.
 * @param modifier Caller layout and optional semantic tag.
 * @param onSearch Optional explicit button/IME submission; absent for live filtering.
 * @param enabled Whether query edits and submission are available.
 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit: () -> Unit = {
        if (enabled) {
            onSearch?.invoke()
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    OutlinedTextField(
        value,
        onValueChange,
        modifier.fillMaxWidth().heightIn(min = 56.dp * LocalControlScale.current).focusRequester(focusRequester),
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            Row {
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onValueChange("")
                            focusRequester.requestFocus()
                            keyboard?.show()
                        },
                        enabled = enabled,
                    ) {
                        Icon(Icons.Default.Close, stringResource(R.string.clear_search))
                    }
                }
                if (onSearch != null) {
                    TextButton(submit, enabled = enabled) {
                        Text(stringResource(R.string.discovery_search))
                    }
                }
            }
        },
        shape = CircleShape,
        singleLine = true,
        enabled = enabled,
        keyboardOptions =
            KeyboardOptions(
                imeAction =
                    if (onSearch ==
                        null
                    ) {
                        ImeAction.Done
                    } else {
                        ImeAction.Search
                    },
            ),
        keyboardActions = KeyboardActions(onSearch = { submit() }, onDone = { submit() }),
    )
}
