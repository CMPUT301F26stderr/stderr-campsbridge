package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AccountAvatar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant

/**
 * Shows the profile menu beneath the workspace's shared frame.
 * @param state Observed presentation state.
 * @param onRetry Explicit observation retry.
 * @param modifier Parent layout.
 * @param onPersonalDetails Opens the independent name/photo editor.
 * @param onContactDetails Opens optional contacts.
 * @param onSettings Opens settings when that destination is registered.
 * @param photoReference Last confirmed image display reference, never an unsaved candidate.
 * @param workspaceContent Shared workspace picker supplied by navigation.
 * @param onStartHosting Opens the registered introduction only without owner capability.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onPersonalDetails: () -> Unit = {},
    onContactDetails: () -> Unit = {},
    onSettings: (() -> Unit)? = null,
    photoReference: String? = null,
    workspaceContent: @Composable () -> Unit = {},
    onStartHosting: (() -> Unit)? = null,
) {
    ProfileStateContent(state, onRetry, modifier) { content ->
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            AccountAvatar(photoReference, stringResource(R.string.profile_photo))
            Text(content.account.name, style = MaterialTheme.typography.headlineMedium)
        }
        NavigationRow(
            stringResource(R.string.personal_details),
            onPersonalDetails,
            supportingText = stringResource(R.string.personal_details_summary),
            icon = Icons.Default.Person,
        )
        NavigationRow(
            stringResource(R.string.contact_details),
            onContactDetails,
            supportingText = stringResource(R.string.contact_details_summary),
            icon = Icons.Default.Email,
        )
        workspaceContent()
        if (Role.OWNER !in content.account.roles && onStartHosting != null) {
            NavigationRow(stringResource(R.string.onboarding_host_label), onStartHosting)
        }
        if (onSettings !=
            null
        ) {
            NavigationRow(
                stringResource(R.string.settings_title),
                onSettings,
                icon = Icons.Default.Settings,
            )
        }
    }
}

/** Shares profile read outcomes without adding a frame or acquiring a controller.
 * @param state Current account/editor read outcome.
 * @param onRetry Explicit account observation retry.
 * @param modifier Parent layout.
 * @param content Loaded profile content.
 */
@Composable
internal fun ProfileStateContent(
    state: ProfileUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(ProfileUiState.Content) -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState(),
            ).padding(vertical = Spacing.medium),
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
                content(state)
            }
        }
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
internal fun profilePreviewAccount(): Account =
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
