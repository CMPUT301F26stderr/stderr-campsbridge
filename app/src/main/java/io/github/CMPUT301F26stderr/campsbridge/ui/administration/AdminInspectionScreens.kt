package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.roleLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AttachmentImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant

/** Read-only profile facts and three independently retryable source sections.
 * @param state Current selected profile, including retained removal facts.
 * @param onBack Returns to the actual caller.
 * @param onRetryAccount Retries only the failed account source.
 * @param onRetryCampsites Retries only the failed owner-scoped campsite source.
 * @param onRetryPhoto Retries only the attachment or transient display URL.
 * @param onCampsite Opens a saved listing through read-only admin inspection.
 * @param modifier Caller layout.
 */
@Composable
fun AdminProfileScreen(
    state: AdminInspectionUiState.Profile,
    onBack: () -> Unit,
    onRetryAccount: () -> Unit,
    onRetryCampsites: () -> Unit,
    onRetryPhoto: () -> Unit,
    onCampsite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(stringResource(R.string.admin_profile_inspection), modifier, onBack = onBack) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            Text(stringResource(R.string.admin_inspection_read_only))
            InspectionField(stringResource(R.string.admin_account_id), state.id)
            Column(
                Modifier.fillMaxWidth().testTag("admin_profile_account"),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    stringResource(R.string.personal_details),
                    style = MaterialTheme.typography.titleLarge,
                )
                when (val account = state.account) {
                    AdminRead.Loading -> {
                        LoadingContent(stringResource(R.string.profile_loading))
                    }

                    AdminRead.Failure -> {
                        FailureContent(
                            stringResource(R.string.profile_failure),
                            onRetryAccount,
                        )
                    }

                    AdminRead.Unavailable -> {
                        EmptyContent(
                            stringResource(R.string.admin_profile_unavailable),
                        )
                    }

                    is AdminRead.Content -> {
                        AdminAccountFacts(account.value)
                    }
                }
            }
            HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().testTag("admin_profile_photo"),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    stringResource(R.string.profile_photo),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (state.account is AdminRead.Content) {
                    AttachmentImage(
                        state.photo,
                        stringResource(R.string.profile_photo),
                        onRetryPhoto,
                        Modifier.size(128.dp),
                    )
                    if (state.photo.loadState == ImageLoadState.READY &&
                        state.photo.attachment == null
                    ) {
                        EmptyContent(stringResource(R.string.admin_no_profile_photo))
                    }
                } else {
                    Text(stringResource(R.string.admin_profile_dependents_unavailable))
                }
            }
            HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().testTag("admin_profile_campsites"),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    stringResource(R.string.admin_owned_campsites),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (state.account !is AdminRead.Content) {
                    Text(stringResource(R.string.admin_profile_dependents_unavailable))
                } else {
                    when (val sites = state.campsites) {
                        AdminRead.Loading -> {
                            LoadingContent(stringResource(R.string.admin_owned_campsites_loading))
                        }

                        AdminRead.Failure -> {
                            FailureContent(
                                stringResource(R.string.admin_owned_campsites_failure),
                                onRetryCampsites,
                            )
                        }

                        AdminRead.Unavailable -> {
                            EmptyContent(stringResource(R.string.admin_owned_campsites_unavailable))
                        }

                        is AdminRead.Content -> {
                            if (sites.value.isEmpty()) {
                                EmptyContent(
                                    stringResource(R.string.admin_no_owned_campsites),
                                )
                            }
                            sites.value.forEach { site -> AdminOwnedCampsite(site, onCampsite) }
                        }
                    }
                }
            }
        }
    }
}

/** Actual saved identity, capabilities and optional contact/removal facts. */
@Composable
private fun AdminAccountFacts(account: Account) {
    InspectionField(stringResource(R.string.admin_display_name), account.name)
    InspectionField(stringResource(R.string.email), account.email)
    InspectionField(stringResource(R.string.phone), account.phone)
    InspectionField(
        stringResource(R.string.admin_profile_roles),
        account.roles
            .map {
                roleLabel(it)
            }.joinToString(),
    )
    InspectionField(stringResource(R.string.admin_created_at), account.createdAt.toString())
    Text(
        stringResource(
            if (account.isActive) R.string.admin_account_active else R.string.admin_account_removed,
        ),
    )
    account.removal?.let { removal ->
        InspectionField(stringResource(R.string.admin_removed_at), removal.at.toString())
        InspectionField(stringResource(R.string.admin_removed_by), removal.actorId)
        InspectionField(stringResource(R.string.admin_removal_reason), removal.reason)
    }
}

/** Missing values stay visible instead of disappearing from a retained profile. */
@Composable
private fun InspectionField(
    label: String,
    value: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_provided))
    }
}

/** Draft and retained rows remain navigable, with no owner edit or participation actions. */
@Composable
private fun AdminOwnedCampsite(
    site: Campsite,
    onCampsite: (String) -> Unit,
) {
    val status =
        stringResource(
            when {
                site.removal != null -> R.string.campsite_removed
                site.isPublished -> R.string.campsite_published
                else -> R.string.campsite_draft
            },
        )
    val location =
        site.details.locationLabel.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.not_provided)
    NavigationRow(
        title = site.details.name,
        supportingText = listOf(site.id, location, status).joinToString(" | "),
        onClick = { onCampsite(site.id) },
    )
}

