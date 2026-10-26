package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.common.roleLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant

/**
 * Shows read-only account fields beneath the workspace's shared frame.
 * @param state Observed presentation state.
 * @param onRetry Explicit observation retry.
 * @param modifier Parent layout.
 * @param photoContent Shared photo controls supplied by the route; absent in read-only fixtures.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    photoContent: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        when (state) {
            ProfileUiState.Loading -> {
                LoadingContent(stringResource(R.string.profile_loading))
            }

            ProfileUiState.Failure -> {
                FailureContent(stringResource(R.string.profile_failure), onRetry)
            }

            ProfileUiState.Unavailable -> {
                EmptyContent(stringResource(R.string.account_unavailable_body))
            }

            is ProfileUiState.Content -> {
                val account = state.account
                Text(account.name, style = MaterialTheme.typography.headlineMedium)
                photoContent()
                ProfileField(stringResource(R.string.email), account.email)
                ProfileField(stringResource(R.string.phone), account.phone)
                Text(
                    stringResource(R.string.account_roles),
                    style = MaterialTheme.typography.titleMedium,
                )
                for (role in account.roles) Text(roleLabel(role))
            }
        }
    }
}

/**
 * Keeps a field label distinct from optional contact data.
 * @param label Localized field label.
 * @param value Exact stored contact, or null.
 */
@Composable
private fun ProfileField(
    label: String,
    value: String?,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value ?: stringResource(R.string.not_provided))
    }
}

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
    ProfilePreviewContent(ProfileUiState.Content(profilePreviewAccount()))
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
        ProfileUiState.Content(profilePreviewAccount().copy(name = "Alexandra Chen Montgomery")),
        AccountSettings(fontScale = 1.25, controlScale = 1.5),
    )
}

/** Supplies ordinary sample values without depending on debug repositories or test fixtures. */
private fun profilePreviewAccount(): Account =
    Account(
        "account-1",
        "Alex Chen",
        "alex@example.com",
        null,
        setOf(Role.ENTRANT),
        Preferences(),
        AccountSettings(),
        Instant.parse("2026-10-01T12:00:00Z"),
    )
