/*
 * Saved details sections reuse the corresponding creation fields.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.NavigationGroup
import io.github.CMPUT301F26stderr.campsbridge.ui.components.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Saved details sections reuse the corresponding creation fields.
 * @param title Localized menu and child-page title.
 */
enum class CampsiteEditSection(
    @param:StringRes val title: Int,
) {
    BASICS(R.string.campsite_edit_basics),
    LOCATION(R.string.campsite_location),
    CAPACITY_PRICE(R.string.campsite_edit_capacity),
    OPTIONS(R.string.campsite_edit_options),
    RULES(R.string.campsite_edit_rules),
}

/** Groups the saved editor's local details and independent photo/inventory entry points.
 * @param state Required target read and retained details.
 * @param photo Independently saved or selected photo.
 * @param onBack Requests leaving through the owning editor's discard rules.
 * @param onRetry Retries the required listing source.
 * @param onSave Explicitly updates listing details.
 * @param onSection Opens a local details section.
 * @param onPhoto Opens independent attachment controls.
 * @param onAvailability Opens the existing independent inventory editor.
 * @param snackbar Confirmed details-only save feedback.
 */
@Composable
fun CampsiteEditScreen(
    state: CampsiteEditUiState,
    photo: ImageSlotState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onSection: (CampsiteEditSection) -> Unit,
    onPhoto: () -> Unit,
    onAvailability: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val content = state as? CampsiteEditUiState.Content
    AppScreen(
        stringResource(R.string.campsite_edit_title),
        onBack = onBack,
        backEnabled = content?.isSaving != true && photo.canChoose,
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
            val draft = checkNotNull(content).draft
            val enabled = !content.isSaving
            Text(
                draft.name.ifBlank {
                    stringResource(R.string.campsite_edit_details)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (content.saveFailed) FailureContent(stringResource(R.string.campsite_details_save_failure), onSave)
            NavigationGroup(
                buildList<@Composable () -> Unit> {
                    add {
                        NavigationRow(
                            stringResource(CampsiteEditSection.BASICS.title),
                            { onSection(CampsiteEditSection.BASICS) },
                            supportingText =
                                draft.description.lineSequence().firstOrNull()?.takeIf {
                                    it.isNotBlank()
                                },
                            icon = Icons.Default.Edit,
                            enabled = enabled,
                        )
                    }
                    add {
                        NavigationRow(
                            stringResource(R.string.campsite_edit_photo),
                            onPhoto,
                            supportingText =
                                when {
                                    photo.candidate != null -> {
                                        stringResource(R.string.campsite_edit_photo_unsaved)
                                    }

                                    photo.loadState == ImageLoadState.LOADING -> {
                                        stringResource(
                                            R.string.campsite_edit_photo_loading,
                                        )
                                    }

                                    photo.loadState == ImageLoadState.FAILED -> {
                                        stringResource(R.string.photo_load_failure)
                                    }

                                    photo.attachment != null -> {
                                        stringResource(R.string.photo_saved_confirmed)
                                    }

                                    else -> {
                                        stringResource(R.string.photo_empty)
                                    }
                                },
                            icon = Icons.Default.Add,
                            enabled = enabled,
                        )
                    }
                    add {
                        NavigationRow(stringResource(CampsiteEditSection.LOCATION.title), {
                            onSection(CampsiteEditSection.LOCATION)
                        }, supportingText = draft.locationLabel, icon = Icons.Default.Place, enabled = enabled)
                    }
                    add {
                        NavigationRow(
                            stringResource(CampsiteEditSection.CAPACITY_PRICE.title),
                            {
                                onSection(CampsiteEditSection.CAPACITY_PRICE)
                            },
                            supportingText =
                                stringResource(
                                    R.string.campsite_edit_capacity_summary,
                                    draft.maxGuestsText,
                                    draft.priceText,
                                ),
                            icon = Icons.Default.Person,
                            enabled = enabled,
                        )
                    }
                    add {
                        NavigationRow(stringResource(CampsiteEditSection.OPTIONS.title), {
                            onSection(CampsiteEditSection.OPTIONS)
                        }, icon = Icons.Default.Settings, enabled = enabled)
                    }
                    add {
                        NavigationRow(
                            stringResource(CampsiteEditSection.RULES.title),
                            {
                                onSection(CampsiteEditSection.RULES)
                            },
                            supportingText =
                                draft.rules.lineSequence().firstOrNull()?.takeIf {
                                    it.isNotBlank()
                                },
                            icon = Icons.Default.Info,
                            enabled = enabled,
                        )
                    }
                    add {
                        NavigationRow(
                            stringResource(R.string.availability_title),
                            onAvailability,
                            icon = Icons.Default.DateRange,
                            enabled = enabled,
                        )
                    }
                },
            )
        }
    }
}

/** Hosts existing field content without introducing setup steps or save side effects.
 * @param title Localized section title.
 * @param state Saved target and local input.
 * @param onBack Returns to the retained editor summary.
 * @param onRetry Retries a failed required source.
 * @param fields Existing stateless field content.
 */
@Composable
internal fun CampsiteEditFieldsScreen(
    title: String,
    state: CampsiteEditUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    AppScreen(title, onBack = onBack, backEnabled = (state as? CampsiteEditUiState.Content)?.isSaving != true) {
        CampsiteEditBody(state, onRetry, fields)
    }
}

/** Keeps unknown/failed targets separate from populated editable fields.
 * @param state Required target read.
 * @param onRetry Explicit source retry.
 * @param fields Content used only for a confirmed editable target.
 */
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
