package io.github.CMPUT301F26stderr.campsbridge.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/**
 * Frames pure profile content without acquiring a ViewModel or SDK dependency.
 * @param state Fixture presentation state.
 * @param settings Accessibility preferences used by the preview.
 */
@Composable
private fun ProfilePreviewContent(
    state: ProfileUiState,
    settings: AccountSettings = AccountSettings(),
) {
    CampsbridgeTheme(settings) {
        AppScreen(stringResource(R.string.profile_title)) { ProfileScreen(state, {}) }
    }
}

/** Previews an existing profile and optional contacts. */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ExistingProfilePreview() {
    ProfilePreviewContent(ProfileUiState.Content(sampleAccount()))
}

/** Previews unresolved profile data. */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun LoadingProfilePreview() {
    ProfilePreviewContent(ProfileUiState.Loading)
}

/** Previews retry feedback. */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun FailedProfilePreview() {
    ProfilePreviewContent(ProfileUiState.Failure)
}

/** Previews missing/removed profile feedback. */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun UnavailableProfilePreview() {
    ProfilePreviewContent(ProfileUiState.Unavailable)
}

/** Checks large application and system text against a long name. */
@Preview(showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun EnlargedProfilePreview() {
    ProfilePreviewContent(
        ProfileUiState.Content(sampleAccount().copy(name = "Alexandra Chen Montgomery")),
        AccountSettings(fontScale = 1.25, controlScale = 1.5),
    )
}
