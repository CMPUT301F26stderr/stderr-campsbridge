package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState

/** Coordinates the confirmed capability with root-owned workspace selection and actual navigation.
 * @param model Parent profile owner, shared with its menu/editors.
 * @param session Root-confirmed account/workspace, independent of activation.
 * @param onSelectOwnerWorkspace Requests selection without granting a capability again.
 * @param onOwnerWorkspaceReady Removes this introduction, then opens the registered owner primary.
 * @param onBack Pops the introduction when no active mutation is being abandoned.
 */
@Composable
fun OwnerIntroductionRoute(
    model: ProfileViewModel,
    session: SessionState.Ready,
    onSelectOwnerWorkspace: () -> Unit,
    onOwnerWorkspaceReady: () -> Unit,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val requested by model.ownerWorkspaceRequested.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    var attemptedSelection by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(
        requested,
        session.workspace,
        session.account.roles,
        session.isSelectingWorkspace,
        lifecycle,
    ) {
        if (!lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        if (session.workspace == Role.OWNER && !session.isSelectingWorkspace) {
            model.acknowledgeOwnerWorkspaceRequest()
            onOwnerWorkspaceReady()
        } else if (requested && Role.OWNER in session.account.roles && !attemptedSelection) {
            attemptedSelection = true
            onSelectOwnerWorkspace()
        }
    }
    OwnerIntroductionScreen(
        state,
        requested,
        session.isSelectingWorkspace,
        session.selectionFailed,
        onContinue = {
            if (requested) {
                if (Role.OWNER in session.account.roles && !session.isSelectingWorkspace) {
                    attemptedSelection = true
                    onSelectOwnerWorkspace()
                }
            } else {
                model.enableOwner()
            }
        },
        onRetry = model::retry,
        onBack = {
            if (!session.isSelectingWorkspace && model.cancelOwnerActivation()) onBack()
        },
        ownerWorkspaceAvailable = Role.OWNER in session.account.roles,
    )
}
