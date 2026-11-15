package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageOperation
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Sections of the published editor; each child retains the same local draft. */
enum class CampsiteEditSection(
    @param:StringRes val title: Int,
) {
    BASICS(R.string.campsite_edit_basics),
    LOCATION(R.string.campsite_location),
    CAPACITY_PRICE(R.string.campsite_edit_capacity),
    OPTIONS(R.string.campsite_edit_options),
    RULES(R.string.campsite_edit_rules),
}

/** Section menu using the shared app frame and explicit details-only Save. */
@Composable
fun CampsiteEditScreen(
    state: CampsiteEditUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onSection: (CampsiteEditSection) -> Unit,
    onPhoto: () -> Unit,
    onAvailability: () -> Unit,
    snackbar: SnackbarHostState,
    backEnabled: Boolean = true,
    photo: ImageSlotState = ImageSlotState(loadState = ImageLoadState.READY),
) {
    val content = state as? CampsiteEditUiState.Content
    AppScreen(
        stringResource(R.string.campsite_edit_title),
        onBack = onBack,
        backEnabled = backEnabled && content?.isSaving != true,
        snackbarHostState = snackbar,
        bottomContent = {
            if (content != null) {
                ActionFooter {
                    AppActionButton(
                        stringResource(R.string.campsite_edit_save),
                        onSave,
                        Modifier.fillMaxWidth(),
                        enabled = content.isDirty && !content.isSaving,
                        isSubmitting = content.isSaving,
                        progressDescription = stringResource(R.string.campsite_saving_details),
                    )
                }
            }
        },
    ) {
        CampsiteEditBody(state, onRetry) {
            val current = checkNotNull(content)
            Text(current.draft.name, style = MaterialTheme.typography.headlineSmall)
            if (current.saveFailed) FailureContent(stringResource(R.string.campsite_edit_failed), onSave)
            if (current.errors != CampsiteDetailsInput.Errors()) {
                Text(stringResource(R.string.campsite_edit_invalid), color = MaterialTheme.colorScheme.error)
            }
            for (section in CampsiteEditSection.entries) {
                NavigationRow(
                    stringResource(section.title),
                    { onSection(section) },
                    supportingText =
                        when (section) {
                            CampsiteEditSection.BASICS -> {
                                current.draft.description
                                    .lineSequence()
                                    .firstOrNull()
                            }

                            CampsiteEditSection.LOCATION -> {
                                current.draft.locationLabel
                            }

                            CampsiteEditSection.CAPACITY_PRICE -> {
                                stringResource(
                                    R.string.campsite_edit_capacity_summary,
                                    current.draft.maxGuestsText,
                                    current.draft.priceText,
                                )
                            }

                            else -> {
                                null
                            }
                        },
                    icon = Icons.Default.Edit,
                    enabled = !current.isSaving,
                )
            }
            NavigationRow(
                stringResource(R.string.campsite_edit_photo),
                onPhoto,
                enabled = !current.isSaving,
                supportingText =
                    when {
                        photo.unconfirmedBlobId != null -> stringResource(R.string.photo_unconfirmed)
                        photo.operation != ImageOperation.IDLE -> stringResource(R.string.photo_checking)
                        photo.failure != null -> stringResource(R.string.campsite_edit_photo_attention)
                        photo.candidate != null -> stringResource(R.string.campsite_photo_selected)
                        photo.loadState == ImageLoadState.FAILED -> stringResource(R.string.photo_load_failure)
                        else -> null
                    },
            )
            NavigationRow(stringResource(R.string.availability_title), onAvailability, enabled = !current.isSaving)
        }
    }
}

/** Hosts reused setup fields without setup progress or creation actions. */
@Composable
internal fun CampsiteEditFieldsScreen(
    title: String,
    state: CampsiteEditUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    AppScreen(title, onBack = onBack) { CampsiteEditBody(state, onRetry, fields) }
}

@Composable
private fun CampsiteEditBody(
    state: CampsiteEditUiState,
    onRetry: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    when (state) {
        CampsiteEditUiState.Loading -> {
            LoadingContent()
        }

        CampsiteEditUiState.Failure -> {
            FailureContent(stringResource(R.string.campsite_detail_failure), onRetry)
        }

        CampsiteEditUiState.Unavailable -> {
            Text(stringResource(R.string.campsite_unavailable))
        }

        is CampsiteEditUiState.Content -> {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                content = fields,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun CampsiteEditPreview() {
    CampsbridgeTheme {
        CampsiteEditScreen(
            CampsiteEditUiState.Content(previewCampsite().toDraft(), isDirty = true),
            {},
            {},
            {},
            {},
            {},
            {},
            remember { SnackbarHostState() },
        )
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun CampsiteEditFailurePreview() {
    CampsbridgeTheme {
        CampsiteEditScreen(CampsiteEditUiState.Failure, {}, {}, {}, {}, {}, {}, remember { SnackbarHostState() })
    }
}
