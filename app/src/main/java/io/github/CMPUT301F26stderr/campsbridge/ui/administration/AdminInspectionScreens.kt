/*
 * Profile inspection with independently retryable facts and optional moderation.
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
import io.github.CMPUT301F26stderr.campsbridge.ui.common.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.roleLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AccountAvatar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AttachmentImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant

/** Profile inspection with independently retryable facts and optional moderation.
 * @param state Current selected profile, including retained removal facts.
 * @param onBack Returns to the actual caller.
 * @param onRetryAccount Retries only the failed account source.
 * @param onRetryCampsites Retries only the failed owner-scoped campsite source.
 * @param onRetryPhoto Retries only the attachment or transient display URL.
 * @param onCampsite Opens a saved listing through read-only admin inspection.
 * @param onImage Opens this account's current photo for inspection.
 * @param modifier Caller layout.
 * @param removalAction Optional administrator action for the confirmed profile.
 * @param backEnabled Whether the retained removal permits leaving inspection.
 */
@Composable
fun AdminProfileScreen(
    state: AdminInspectionUiState.Profile,
    onBack: () -> Unit,
    onRetryAccount: () -> Unit,
    onRetryCampsites: () -> Unit,
    onRetryPhoto: () -> Unit,
    onCampsite: (String) -> Unit,
    onImage: () -> Unit,
    modifier: Modifier = Modifier,
    removalAction: (@Composable () -> Unit)? = null,
    backEnabled: Boolean = true,
) {
    AppScreen(stringResource(R.string.admin_profile_inspection), modifier, onBack = onBack, backEnabled = backEnabled) {
        val loaded = (state.account as? AdminRead.Content)?.value
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            AdminRecordHeader(
                title =
                    when {
                        loaded == null -> stringResource(R.string.admin_profile_inspection)
                        loaded.name.isBlank() -> stringResource(R.string.admin_profile_unnamed)
                        else -> loaded.name
                    },
                id = state.id,
                leading = {
                    AccountAvatar(
                        state.photo.displayUrl.takeIf { loaded != null },
                        loaded?.name ?: state.id,
                        size = 56.dp,
                    )
                },
                status =
                    loaded?.let { account ->
                        {
                            StatusLabel(
                                stringResource(
                                    if (account.isActive) {
                                        R.string.admin_account_active
                                    } else {
                                        R.string.admin_account_removed
                                    },
                                ),
                                alert = !account.isActive,
                            )
                        }
                    },
            )
            if (removalAction == null) {
                Text(
                    stringResource(R.string.admin_inspection_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            removalAction?.invoke()
            SummarySection(
                stringResource(R.string.personal_details),
                Modifier.testTag("admin_profile_account"),
            ) {
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
            SummarySection(
                stringResource(R.string.profile_photo),
                Modifier.testTag("admin_profile_photo"),
            ) {
                if (state.account is AdminRead.Content) {
                    val missing = state.photo.loadState == ImageLoadState.READY && state.photo.attachment == null
                    if (missing) {
                        EmptyContent(stringResource(R.string.admin_no_profile_photo))
                    } else {
                        AttachmentImage(
                            state.photo,
                            stringResource(R.string.profile_photo),
                            onRetryPhoto,
                            Modifier.size(128.dp).clip(CircleShape),
                        )
                    }
                    if (state.photo.loadState == ImageLoadState.READY &&
                        state.photo.attachment != null
                    ) {
                        NavigationRow(stringResource(R.string.admin_inspect_profile_photo), onImage)
                    }
                } else {
                    Text(stringResource(R.string.admin_profile_dependents_unavailable))
                }
            }
            SummarySection(
                stringResource(R.string.admin_owned_campsites),
                Modifier.testTag("admin_profile_campsites"),
            ) {
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

/** Shows saved contact details, capabilities and any retained removal facts; name and state live in the header.
 * @param account Actual inspected account, including retained removal facts.
 */
@Composable
private fun AdminAccountFacts(account: Account) {
    val locale = LocalConfiguration.current.locales[0]
    val notProvided = stringResource(R.string.not_provided)
    DetailRow(stringResource(R.string.email), account.email?.takeIf { it.isNotBlank() } ?: notProvided)
    DetailRow(stringResource(R.string.phone), account.phone?.takeIf { it.isNotBlank() } ?: notProvided)
    DetailRow(
        stringResource(R.string.admin_profile_roles),
        account.roles
            .map { roleLabel(it) }
            .joinToString()
            .ifBlank { notProvided },
    )
    DetailRow(stringResource(R.string.admin_created_at), formatDateTime(account.createdAt, locale))
    account.removal?.let { removal ->
        DetailRow(stringResource(R.string.admin_removed_at), formatDateTime(removal.at, locale))
        DetailRow(stringResource(R.string.admin_removed_by), removal.actorId)
        DetailRow(
            stringResource(R.string.admin_removal_reason),
            removal.reason?.takeIf { it.isNotBlank() } ?: notProvided,
        )
    }
}

/** Keeps draft and retained rows navigable without owner editing or participation actions.
 * @param site Actual related campsite.
 * @param onCampsite Opens the selected saved identity.
 */
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
        supportingText = listOf(location, status, site.id).joinToString(" · "),
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
    CampsbridgeTheme { AdminProfileScreen(state, {}, {}, {}, {}, {}, {}) }
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
 * @param onRemove Requests confirmation for the current saved attachment.
 * @param backEnabled Whether the operation permits shared Back input.
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
    onRemove: (() -> Unit)? = null,
    backEnabled: Boolean = true,
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
    AppScreen(stringResource(R.string.admin_image_inspection), modifier, onBack = onBack, backEnabled = backEnabled) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            if (onRemove == null) {
                Text(
                    stringResource(R.string.admin_inspection_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            onRemove?.let {
                AppActionButton(
                    stringResource(R.string.admin_remove_image),
                    it,
                    Modifier.fillMaxWidth(),
                    style = ActionStyle.DESTRUCTIVE_OUTLINED,
                )
            }
            Column(
                Modifier.testTag("admin_image_photo"),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                if (state.photo.loadState == ImageLoadState.READY && state.photo.attachment == null) {
                    EmptyContent(stringResource(R.string.admin_image_no_attachment))
                } else {
                    AttachmentImage(
                        state.photo,
                        stringResource(R.string.admin_image_inspection),
                        onRetryPhoto,
                        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.medium),
                    )
                }
                SummarySection(stringResource(R.string.admin_image_details)) {
                    DetailRow(
                        stringResource(R.string.admin_image_attached_to),
                        stringResource(
                            if (state.target.type == ImageTargetType.ACCOUNT) {
                                R.string.admin_target_account
                            } else {
                                R.string.admin_target_campsite
                            },
                        ),
                    )
                    DetailRow(stringResource(R.string.admin_image_slot), state.target.id)
                    state.photo.attachment?.takeIf { it.target == state.target }?.let { attachment ->
                        DetailRow(
                            stringResource(R.string.admin_image_uploaded_at),
                            formatDateTime(attachment.uploadedAt, locale),
                        )
                    }
                }
            }
            SummarySection(
                stringResource(R.string.admin_image_parent),
                Modifier.testTag("admin_image_parent"),
            ) {
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
                                    DetailRow(
                                        stringResource(R.string.admin_display_name),
                                        parent.account.name,
                                    )
                                    StatusLabel(
                                        stringResource(
                                            if (parent.account.isActive) {
                                                R.string.admin_account_active
                                            } else {
                                                R.string.admin_account_removed
                                            },
                                        ),
                                        alert = !parent.account.isActive,
                                    )
                                }

                                is AdminImageParent.Site -> {
                                    DetailRow(
                                        stringResource(R.string.admin_display_name),
                                        parent.campsite.details.name,
                                    )
                                    StatusLabel(
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

/** Shows the saved image inspector with its destructive action above the image details. */
@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun AdminImageRemovalPreview() {
    val site = previewCampsite()
    val photo = previewCampsitePhoto()
    CampsbridgeTheme {
        AdminImageScreen(
            AdminInspectionUiState.Image(
                requireNotNull(photo.attachment).target,
                AdminRead.Content(AdminImageParent.Site(site)),
                photo,
            ),
            {},
            {},
            {},
            {},
            onRemove = {},
        )
    }
}
