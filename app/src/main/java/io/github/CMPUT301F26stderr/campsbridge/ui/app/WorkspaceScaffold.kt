package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.common.roleLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.AppScreen

/**
 * Supplies one shared feature frame and a capability-limited workspace switcher.
 * @param state Confirmed ready session.
 * @param onSelectWorkspace Explicit selection callback.
 * @param content Implemented navigation content within the shared insets.
 */
@Composable
fun WorkspaceScaffold(
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val switchDescription = stringResource(R.string.switch_workspace)
    AppScreen(
        title = stringResource(R.string.profile_title),
        actions = {
            if (state.account.roles.size > 1) {
                Box {
                    TextButton(
                        onClick = { expanded = true },
                        enabled = !state.isSelectingWorkspace,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    switchDescription
                            },
                    ) {
                        Text(roleLabel(state.workspace))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        for (role in Role.entries.filter { it in state.account.roles }) {
                            DropdownMenuItem(
                                text = { Text(roleLabel(role)) },
                                onClick = {
                                    expanded = false
                                    onSelectWorkspace(role)
                                },
                                enabled = !state.isSelectingWorkspace,
                            )
                        }
                    }
                }
            }
        },
    ) {
        if (state.selectionFailed) Text(stringResource(R.string.workspace_selection_failure))
        content()
    }
}
