/*
 * Binds saved profile inspection to the Browse reader and shared removal owner.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState

/** Binds saved profile inspection to the Browse reader and shared removal owner.
 * @param model The existing Browse graph owner, never a profile-specific ViewModel.
 * @param accountId Decoded saved account identity from navigation.
 * @param onBack Returns to the retained Browse destination.
 * @param onCampsite Opens a saved campsite in the admin inspection context.
 * @param onImage Opens this account's confirmed current photo attachment.
 * @param removal Browse-owned original-target removal controller.
 * @param session Current account capabilities.
 * @param onRequestRootRemoval Delegates self-removal to the root owner.
 */
@Composable
fun AdminProfileRoute(
    model: AdminBrowseViewModel,
    accountId: String,
    onBack: () -> Unit,
    onCampsite: (String) -> Unit,
    onImage: (ImageTarget) -> Unit,
    removal: AdminRemovalViewModel? = null,
    session: SessionState? = null,
    onRequestRootRemoval: (String) -> Unit = {},
) {
    val inspection by model.inspection.collectAsStateWithLifecycle()
    DisposableEffect(model, accountId) {
        model.openProfile(accountId)
        onDispose { model.closeSelection(AdminInspectionTarget.Profile(accountId)) }
    }
    val profile = (inspection as? AdminInspectionUiState.Profile)?.takeIf { it.id == accountId }
    val phase =
        removal
            ?.state
            ?.collectAsStateWithLifecycle()
            ?.value
            ?.phase
    val canLeave = phase !in setOf(AdminRemovalPhase.REMOVING, AdminRemovalPhase.CHECKING)
    val admin = session as? SessionState.Ready
    AdminProfileScreen(
        state = profile ?: AdminInspectionUiState.Profile(accountId),
        onBack = onBack,
        backEnabled = canLeave,
        removalAction =
            if (removal != null && admin?.account?.isActive == true && Role.ADMIN in admin.account.roles) {
                (profile?.account as? AdminRead.Content)?.value?.takeIf { it.isActive }?.let { account ->
                    { AdminRemovalAccountAction(removal, admin, account) }
                }
            } else {
                null
            },
        onRetryAccount = model::retryProfile,
        onRetryCampsites = model::retryProfileCampsites,
        onRetryPhoto = model::retryInspectionPhoto,
        onCampsite = onCampsite,
        onImage = {
            val current = model.inspection.value as? AdminInspectionUiState.Profile
            val image = current?.photo?.attachment?.target
            if (current?.id == accountId && current.account is AdminRead.Content &&
                current.photo.loadState == ImageLoadState.READY &&
                image != null &&
                image == ImageTarget(ImageTargetType.ACCOUNT, accountId)
            ) {
                onImage(image)
            }
        },
    )
    if (removal != null && session != null) {
        AdminRemovalHost(removal, session, onRequestRootRemoval)
    }
}
