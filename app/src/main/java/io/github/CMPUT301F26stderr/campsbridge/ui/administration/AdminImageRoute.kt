/*
 * Reads a target slot under the existing Browse owner, never a copied attachment or URL.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen

/** Reads a target slot under the existing Browse owner, never a copied attachment or URL.
 * @param model Shared Browse graph owner.
 * @param target Decoded target slot, or null for an invalid route.
 * @param onBack Returns to the actual caller.
 * @param onProfile Opens the confirmed account parent.
 * @param onCampsite Opens the confirmed campsite parent in read-only inspection.
 * @param removal Browse-parent removal controller, also retained across image selections.
 * @param session Actual account session used to gate removal.
 * @param onRequestRootRemoval Delegates self-removal to the root-owned controller.
 */
@Composable
fun AdminImageRoute(
    model: AdminBrowseViewModel,
    target: ImageTarget?,
    onBack: () -> Unit,
    onProfile: (String) -> Unit,
    onCampsite: (String) -> Unit,
    removal: AdminRemovalViewModel? = null,
    session: SessionState? = null,
    onRequestRootRemoval: (String) -> Unit = {},
) {
    val inspection by model.inspection.collectAsStateWithLifecycle()
    DisposableEffect(model, target) {
        if (target != null && target.id.isNotBlank()) model.openImage(target)
        onDispose { target?.let { model.closeSelection(AdminInspectionTarget.Image(it)) } }
    }
    if (target == null || target.id.isBlank()) {
        AppScreen(stringResource(R.string.admin_image_inspection), onBack = onBack) {
            EmptyContent(stringResource(R.string.admin_image_invalid_target))
        }
        return
    }
    val phase =
        removal
            ?.state
            ?.collectAsStateWithLifecycle()
            ?.value
            ?.phase
    val canLeave = phase !in setOf(AdminRemovalPhase.REMOVING, AdminRemovalPhase.CHECKING)
    val image = (inspection as? AdminInspectionUiState.Image)?.takeIf { it.target == target }
    AdminImageScreen(
        state = image ?: AdminInspectionUiState.Image(target),
        onBack = onBack,
        backEnabled = canLeave,
        onRemove =
            if (removal != null && session is SessionState.Ready &&
                session.account.isActive && Role.ADMIN in session.account.roles
            ) {
                image?.photo?.attachment?.takeIf { it.target == target }?.let { attachment ->
                    {
                        removal.updateSession(session)
                        removal.requestImage(attachment)
                    }
                }
            } else {
                null
            },
        onRetryPhoto = model::retryInspectionPhoto,
        onRetryParent = model::retryImageParent,
        onParent = parent@{
            if (removal?.state?.value?.phase in
                setOf(AdminRemovalPhase.REMOVING, AdminRemovalPhase.CHECKING)
            ) {
                return@parent
            }
            // Recheck the live reader at click time, including transitions to missing/failed data.
            val current = model.inspection.value as? AdminInspectionUiState.Image
            val parent = (current?.parent as? AdminRead.Content)?.value
            if (current?.target == target) {
                when (parent) {
                    is AdminImageParent.Profile -> {
                        if (target.type == ImageTargetType.ACCOUNT &&
                            parent.account.id == target.id
                        ) {
                            onProfile(parent.account.id)
                        }
                    }

                    is AdminImageParent.Site -> {
                        if (target.type == ImageTargetType.CAMPSITE &&
                            parent.campsite.id == target.id
                        ) {
                            onCampsite(parent.campsite.id)
                        }
                    }

                    null -> {}
                }
            }
        },
    )
    if (removal != null && session != null) {
        AdminRemovalHost(removal, session, onRequestRootRemoval)
    }
}
