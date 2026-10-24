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
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.roleLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Shows read-only account fields beneath the workspace's shared frame.
 * @param state Observed presentation state.
 * @param onRetry Explicit observation retry.
 * @param modifier Parent layout.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
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
