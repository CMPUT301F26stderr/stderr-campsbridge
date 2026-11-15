/*
 * Binds the saved route identity to the Browse parent's selected read-only profile.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState

/** Binds the saved route identity to the Browse parent's selected read-only profile.
 * @param model The existing Browse graph owner, never a profile-specific ViewModel.
 * @param accountId Decoded saved account identity from navigation.
 * @param onBack Returns to the retained Browse destination.
 * @param onCampsite Opens a saved campsite in the admin inspection context.
 * @param onImage Opens this account's confirmed current photo attachment.
 */
@Composable
fun AdminProfileRoute(
    model: AdminBrowseViewModel,
    accountId: String,
    onBack: () -> Unit,
    onCampsite: (String) -> Unit,
    onImage: (ImageTarget) -> Unit,
) {
    val inspection by model.inspection.collectAsStateWithLifecycle()
    DisposableEffect(model, accountId) {
        model.openProfile(accountId)
        onDispose { model.closeSelection(AdminInspectionTarget.Profile(accountId)) }
    }
    val profile = (inspection as? AdminInspectionUiState.Profile)?.takeIf { it.id == accountId }
    AdminProfileScreen(
        state = profile ?: AdminInspectionUiState.Profile(accountId),
        onBack = onBack,
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
}
