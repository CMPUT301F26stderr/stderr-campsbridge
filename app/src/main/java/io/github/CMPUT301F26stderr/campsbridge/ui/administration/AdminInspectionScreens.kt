/*
 * Read-only profile facts and three independently retryable source sections.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
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
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsitePhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
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
import java.time.ZoneId

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
                        Modifier.size(128.dp).clip(CircleShape),
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
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_provided),
            style = MaterialTheme.typography.bodyMedium,
        )
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

/** Supplies a stable owner profile for admin inspection previews. */
private fun inspectionPreviewAccount() =
    Account(
        "preview-owner",
        "Jordan Lee",
        "jordan@example.ca",
        null,
        setOf(Role.ENTRANT, Role.OWNER),
        Preferences(),
        AccountSettings(),
        Instant.EPOCH,
    )

/** Renders one admin profile state with the shared theme.
 * @param state Profile data and independent loading outcomes.
 */
@Composable
private fun ProfileInspectionPreview(state: AdminInspectionUiState.Profile) {
    CampsbridgeTheme { AdminProfileScreen(state, {}, {}, {}, {}, {}) }
}

/** Shows a loaded profile, owned campsite, and photo slot. */
@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun AdminProfileContentPreview() {
    ProfileInspectionPreview(
        AdminInspectionUiState.Profile(
            "preview-owner",
            AdminRead.Content(inspectionPreviewAccount()),
            AdminRead.Content(listOf(previewCampsite())),
            ImageSlotState(loadState = ImageLoadState.READY),
        ),
    )
}

/** Shows the retained fields of a removed profile at enlarged text. */
@Preview(showBackground = true, widthDp = 390, heightDp = 900, fontScale = 1.5f)
@Composable
private fun AdminProfileRemovedPreview() {
    ProfileInspectionPreview(
        AdminInspectionUiState.Profile(
            "preview-owner",
            AdminRead.Content(
                inspectionPreviewAccount().copy(
                    name = "",
                    email = null,
                    roles = emptySet(),
                    removal = Removal(Instant.EPOCH, "admin", null),
                ),
            ),
            AdminRead.Content(emptyList()),
            ImageSlotState(loadState = ImageLoadState.READY),
        ),
    )
}

/** Shows loading while the profile and dependent sections resolve. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun AdminProfileLoadingPreview() {
    ProfileInspectionPreview(AdminInspectionUiState.Profile("preview-owner"))
}

/** Shows a profile read failure with retry feedback. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun AdminProfileFailurePreview() {
    ProfileInspectionPreview(
        AdminInspectionUiState.Profile("preview-owner", account = AdminRead.Failure),
    )
}

/** Shows the unavailable state for an absent profile. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun AdminProfileUnavailablePreview() {
    ProfileInspectionPreview(
        AdminInspectionUiState.Profile("missing-owner", account = AdminRead.Unavailable),
    )
}

/** Shows independent campsite and image failures below a loaded profile. */
@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun AdminProfileSectionFailuresPreview() {
    ProfileInspectionPreview(
        AdminInspectionUiState.Profile(
            "preview-owner",
            AdminRead.Content(inspectionPreviewAccount()),
            AdminRead.Failure,
            ImageSlotState(loadState = ImageLoadState.FAILED),
        ),
    )
}

/** Slot display remains independent of its parent metadata, including orphaned attachments.
 * @param state Selected target and the independent display/metadata outcomes.
 * @param onBack Returns to the retained caller.
 * @param onRetryPhoto Retries only attachment or display reads.
 * @param onRetryParent Retries only failed parent metadata.
 * @param onParent Opens the confirmed parent identity; never enabled for inferred facts.
 * @param modifier Caller layout.
 */
@Composable
fun AdminImageScreen(
    state: AdminInspectionUiState.Image,
    onBack: () -> Unit,
    onRetryPhoto: () -> Unit,
    onRetryParent: () -> Unit,
    onParent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val parent = (state.parent as? AdminRead.Content)?.value
    val confirmed =
        when (parent) {
            is AdminImageParent.Profile -> {
                state.target.type == ImageTargetType.ACCOUNT &&
                    parent.account.id == state.target.id
            }

            is AdminImageParent.Site -> {
                state.target.type == ImageTargetType.CAMPSITE &&
                    parent.campsite.id == state.target.id
            }

            null -> {
                false
            }
        }
    AppScreen(stringResource(R.string.admin_image_inspection), modifier, onBack = onBack) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            Text(stringResource(R.string.admin_inspection_read_only))
            InspectionField(stringResource(R.string.admin_image_slot), state.target.id)
            Text(
                stringResource(
                    if (state.target.type ==
                        ImageTargetType.ACCOUNT
                    ) {
                        R.string.admin_target_account
                    } else {
                        R.string.admin_target_campsite
                    },
                ),
            )
            Column(
                Modifier.testTag("admin_image_photo"),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                AttachmentImage(
                    state.photo,
                    stringResource(R.string.admin_image_inspection),
                    onRetryPhoto,
                    Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.medium),
                )
                if (state.photo.loadState == ImageLoadState.READY &&
                    state.photo.attachment == null
                ) {
                    EmptyContent(stringResource(R.string.admin_image_no_attachment))
                }
                state.photo.attachment?.let { attachment ->
                    InspectionField(
                        stringResource(R.string.admin_image_uploaded_at),
                        formatDateTime(attachment.uploadedAt, ZoneId.systemDefault(), locale),
                    )
                }
            }
            HorizontalDivider()
            Column(
                Modifier.testTag("admin_image_parent"),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    stringResource(R.string.admin_image_parent),
                    style = MaterialTheme.typography.titleLarge,
                )
                when (state.parent) {
                    AdminRead.Loading -> {
                        LoadingContent(stringResource(R.string.admin_image_parent_loading))
                    }

                    AdminRead.Failure -> {
                        FailureContent(
                            stringResource(R.string.admin_image_parent_failure),
                            onRetryParent,
                        )
                    }

                    AdminRead.Unavailable -> {
                        EmptyContent(stringResource(R.string.admin_image_parent_unavailable))
                    }

                    is AdminRead.Content -> {
                        if (confirmed) {
                            when (parent) {
                                is AdminImageParent.Profile -> {
                                    InspectionField(
                                        stringResource(R.string.admin_display_name),
                                        parent.account.name,
                                    )
                                    Text(
                                        stringResource(
                                            if (parent.account.isActive) {
                                                R.string.admin_account_active
                                            } else {
                                                R.string.admin_account_removed
                                            },
                                        ),
                                    )
                                }

                                is AdminImageParent.Site -> {
                                    InspectionField(
                                        stringResource(R.string.admin_display_name),
                                        parent.campsite.details.name,
                                    )
                                    Text(
                                        stringResource(
                                            when {
                                                parent.campsite.removal != null -> {
                                                    R.string.campsite_removed
                                                }

                                                parent.campsite.isPublished -> {
                                                    R.string.campsite_published
                                                }

                                                else -> {
                                                    R.string.campsite_draft
                                                }
                                            },
                                        ),
                                    )
                                }

                                null -> {
                                    Unit
                                }
                            }
                        } else {
                            EmptyContent(stringResource(R.string.admin_image_parent_unavailable))
                        }
                    }
                }
                NavigationRow(
                    title =
                        stringResource(
                            if (state.target.type ==
                                ImageTargetType.ACCOUNT
                            ) {
                                R.string.admin_open_profile
                            } else {
                                R.string.admin_open_campsite
                            },
                        ),
                    onClick = onParent,
                    enabled = confirmed,
                )
            }
        }
    }
}

/** Previews a missing image and parent record with the parent link disabled. */
@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun AdminImageMissingParentPreview() {
    CampsbridgeTheme {
        AdminImageScreen(
            AdminInspectionUiState.Image(
                ImageTarget(ImageTargetType.ACCOUNT, "missing-owner"),
                AdminRead.Unavailable,
                ImageSlotState(loadState = ImageLoadState.READY),
            ),
            {},
            {},
            {},
            {},
        )
    }
}

/** Previews a failed photo with a confirmed profile parent at enlarged text size. */
@Preview(showBackground = true, widthDp = 390, heightDp = 850, fontScale = 1.5f)
@Composable
private fun AdminImageFailedPhotoPreview() {
    CampsbridgeTheme {
        AdminImageScreen(
            AdminInspectionUiState.Image(
                ImageTarget(ImageTargetType.ACCOUNT, "preview-owner"),
                AdminRead.Content(AdminImageParent.Profile(inspectionPreviewAccount())),
                ImageSlotState(loadState = ImageLoadState.FAILED),
            ),
            {},
            {},
            {},
            {},
        )
    }
}

/** Previews a loaded campsite image with independently retryable parent metadata. */
@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun AdminImageParentFailurePreview() {
    CampsbridgeTheme {
        AdminImageScreen(
            AdminInspectionUiState.Image(
                ImageTarget(ImageTargetType.CAMPSITE, "preview-campsite"),
                AdminRead.Failure,
                io.github.CMPUT301F26stderr.campsbridge.ui.campsites
                    .previewCampsitePhoto(),
            ),
            {},
            {},
            {},
            {},
        )
    }
}
