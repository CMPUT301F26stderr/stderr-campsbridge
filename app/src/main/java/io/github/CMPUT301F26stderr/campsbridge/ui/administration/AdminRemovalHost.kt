package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Required owner-approved effects/retention wording; there is no unreviewed default copy. */
data class AdminRemovalWarnings(
    val account: String,
    val campsite: String,
    val image: String,
) {
    init {
        require(account.isNotBlank() && campsite.isNotBlank() && image.isNotBlank())
    }
}

/** One host for account, campsite and image removal. Mount beside the inspection action slots.
 * The caller must supply approved warning copy and delegate self-removal to the existing
 * root AccountRemovalViewModel.request, with AccountRemovalHost still mounted at AppRoot.
 * This host never creates a second root-removal controller or confirms its operation itself.
 */
@Composable
fun AdminRemovalHost(
    model: AdminRemovalViewModel,
    session: SessionState,
    warnings: AdminRemovalWarnings,
    onRequestRootRemoval: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(session) { model.updateSession(session) }
    LaunchedEffect(state.phase, state.request) {
        if (state.phase == AdminRemovalPhase.ROOT_HANDOFF) {
            state.request?.let { request ->
                onRequestRootRemoval(request.actorId)
                model.acknowledgeRootHandoff()
            }
        }
    }
    BackHandler(enabled = state.blocksNavigation) {}
    AdminRemovalContent(state, warnings, model::confirm, model::retry, model::dismiss)
}

/** Slot for a confirmed active inspected account; a supplied root session gates the action. */
@Composable
fun AdminRemovalAccountAction(
    model: AdminRemovalViewModel,
    session: SessionState,
    account: Account,
) {
    AdminRemovalAction(
        model,
        session,
        account.isActive,
        R.string.admin_remove_account,
    ) { model.requestAccount(account) }
}

