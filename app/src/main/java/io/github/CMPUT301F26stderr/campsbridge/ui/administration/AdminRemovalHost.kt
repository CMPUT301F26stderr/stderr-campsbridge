package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton

/** Connects the retained operation to confirmation and cleanup feedback.
 * @param model Browse-owned original-target controller.
 * @param session Current account capabilities.
 * @param onRequestRootRemoval Delegates current-account removal to the root controller.
 */
@Composable
fun AdminRemovalHost(
    model: AdminRemovalViewModel,
    session: SessionState,
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
    AdminRemovalContent(state, model::confirm, model::retry, model::dismiss)
}

/** Requests confirmation for an active inspected account.
 * @param model Retained operation owner.
 * @param session Current administrator session.
 * @param account Inspected account captured on click.
 */
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

/** Requests confirmation from campsite inspection.
 * @param model Retained operation owner.
 * @param session Current administrator session.
 * @param campsite Inspected listing captured on click.
 */
@Composable
fun AdminRemovalCampsiteAction(
    model: AdminRemovalViewModel,
    session: SessionState,
    campsite: Campsite,
) {
    AdminRemovalAction(model, session, campsite.removal == null, R.string.admin_remove_campsite) {
        model.requestCampsite(campsite)
    }
}

/** Requests confirmation even when the attachment preview fails.
 * @param model Retained operation owner.
 * @param session Current administrator session.
 * @param attachment Original metadata captured on click.
 */
@Composable
fun AdminRemovalImageAction(
    model: AdminRemovalViewModel,
    session: SessionState,
    attachment: ImageAttachment,
) {
    AdminRemovalAction(model, session, true, R.string.admin_remove_image) { model.requestImage(attachment) }
}

/** Offers an explicitly confirmed action only to the active administrator.
 * @param model Original-target operation owner.
 * @param session Current capabilities.
 * @param available Whether the inspected record permits removal.
 * @param label Localized action label.
 * @param request Captures the inspected target without writing.
 */
@Composable
private fun AdminRemovalAction(
    model: AdminRemovalViewModel,
    session: SessionState,
    available: Boolean,
    label: Int,
    request: () -> Unit,
) {
    val ready = session as? SessionState.Ready ?: return
    if (!ready.account.isActive || Role.ADMIN !in ready.account.roles || !available) return
    val state by model.state.collectAsStateWithLifecycle()
    AppActionButton(
        label = stringResource(label),
        modifier = Modifier.fillMaxWidth(),
        enabled = state.phase == AdminRemovalPhase.IDLE,
        style = ActionStyle.DESTRUCTIVE_OUTLINED,
        onClick = {
            model.updateSession(session)
            request()
        },
    )
}
