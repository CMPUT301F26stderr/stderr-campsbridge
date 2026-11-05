package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Binds the saved route identity to the Browse parent's selected read-only profile.
 * @param model The existing Browse graph owner, never a profile-specific ViewModel.
 * @param accountId Decoded saved account identity from navigation.
 * @param onBack Returns to the retained Browse destination.
 * @param onCampsite Opens a saved campsite in the admin inspection context.
 */
@Composable
fun AdminProfileRoute(
    model: AdminBrowseViewModel,
    accountId: String,
    onBack: () -> Unit,
    onCampsite: (String) -> Unit,
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
    )
}
