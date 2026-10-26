package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ContactDetailsScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileDetailsScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileUiState

/** Shows implemented task content; pending baselines are handled by the catalogue host.
 * @param session Current local fixture graph.
 * @param onBack Returns to the task catalogue.
 */
@Composable
internal fun TaskExampleFeature(
    session: TaskExampleSession,
    onBack: () -> Unit,
) {
    when (session.example) {
        TaskExample.DISPLAY_NAME, TaskExample.CONTACT -> ProfileTaskExample(session, onBack)
        else -> Unit
    }
}

/** Uses the actual local profile writer and displays its confirmed local outcome.
 * @param session Current task graph.
 * @param onBack Returns to the catalogue.
 */
@Composable
private fun ProfileTaskExample(
    session: TaskExampleSession,
    onBack: () -> Unit,
) {
    val model = session.profile()
    val state by model.state.collectAsStateWithLifecycle()
    val completion by model.completion.collectAsStateWithLifecycle()
    var saved by rememberSaveable { mutableStateOf(false) }
    val contact = session.example == TaskExample.CONTACT
    LaunchedEffect(state is ProfileUiState.Content, completion) {
        completion?.let {
            model.acknowledgeCompletion(it)
            saved = true
        }
        if (state is ProfileUiState.Content) {
            if (contact) model.beginContactEdit() else model.beginPersonalEdit()
        }
    }
    Column {
        if (saved) Text(stringResource(R.string.task_saved))
        if (contact) {
            ContactDetailsScreen(
                state,
                model::editEmail,
                model::editPhone,
                model::saveContacts,
                onBack,
                model::retry,
            )
        } else {
            ProfileDetailsScreen(state, model::editName, model::saveName, onBack, model::retry)
        }
    }
}
