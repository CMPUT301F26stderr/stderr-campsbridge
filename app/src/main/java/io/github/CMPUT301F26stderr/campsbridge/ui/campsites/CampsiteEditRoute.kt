package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.platform.images.PhotoContentReader
import io.github.CMPUT301F26stderr.campsbridge.platform.images.rememberPhotoContentReader
import io.github.CMPUT301F26stderr.campsbridge.platform.images.rememberPhotoPicker
import io.github.CMPUT301F26stderr.campsbridge.platform.maps.LocationPicker
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteBasicsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteCapacityPriceStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteLocationStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteOptionsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteRulesStep
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.ImageSlot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Binds the graph-owned published editor to retained sections and explicit exit results. */
@Composable
fun CampsiteEditRoute(
    model: CampsiteEditViewModel,
    onExit: () -> Unit,
    onSection: (CampsiteEditSection) -> Unit,
    onPhoto: () -> Unit,
    onAvailability: () -> Unit,
    onChooseLocation: () -> Unit,
    onOpenPolicy: () -> Unit,
    onBack: () -> Unit,
    section: CampsiteEditSection? = null,
    photoPage: Boolean = false,
    locationPage: Boolean = false,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val photo by model.photoState.collectAsStateWithLifecycle()
    val discard by model.discardPrompt.collectAsStateWithLifecycle()
    val exit by model.exitRequested.collectAsStateWithLifecycle()
    val saved by model.saveConfirmed.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.campsite_edit_saved)
    val isSummary = section == null && !photoPage && !locationPage
    val back = if (isSummary) model::requestExit else onBack
    BackHandler(onBack = back)
    LaunchedEffect(exit, lifecycle) {
        if (exit && lifecycle == Lifecycle.State.RESUMED) {
            onExit()
            model.acknowledgeExit()
        }
    }
    LaunchedEffect(saved, lifecycle) {
        if (saved && lifecycle == Lifecycle.State.RESUMED && isSummary) {
            model.acknowledgeSave()
            snackbar.showSnackbar(savedText)
        }
    }
    val content = state as? CampsiteEditUiState.Content
    when {
        locationPage && content != null -> {
            LocationPicker(
                content.draft.location,
                onApply = {
                    model.selectLocation(it)
                    onBack()
                },
                onCancel = onBack,
            )
        }

        photoPage -> {
            CampsiteEditFieldsScreen(
                stringResource(R.string.campsite_edit_photo),
                state,
                onBack,
                model::retry,
            ) {
                Text(stringResource(R.string.campsite_edit_photo_hint))
                CampsiteEditPhoto(model, enabled = content?.isSaving == false)
            }
        }

        section != null -> {
            CampsiteEditFieldsScreen(stringResource(section.title), state, onBack, model::retry) {
                val draft = checkNotNull(content).draft
                when (section) {
                    CampsiteEditSection.BASICS -> {
                        CampsiteBasicsStep(
                            draft.name,
                            draft.description,
                            content.errors.nameRequired,
                            model::editName,
                            model::editDescription,
                        )
                    }

                    CampsiteEditSection.LOCATION -> {
                        CampsiteLocationStep(
                            draft,
                            content.errors,
                            model::editLocationLabel,
                            model::editTimeZone,
                            onChooseLocation,
                        )
                    }

                    CampsiteEditSection.CAPACITY_PRICE -> {
                        CampsiteCapacityPriceStep(
                            draft,
                            content.errors,
                            model::editCapacity,
                            model::editPrice,
                        )
                    }

                    CampsiteEditSection.OPTIONS -> {
                        CampsiteOptionsStep(
                            draft.amenities,
                            draft.tags,
                            model::setAmenity,
                            model::setTag,
                        )
                    }

                    CampsiteEditSection.RULES -> {
                        CampsiteRulesStep(draft.rules, model::editRules, onOpenPolicy)
                    }
                }
            }
        }

        else -> {
            CampsiteEditScreen(
                state,
                model::requestExit,
                model::retry,
                model::save,
                onSection,
                onPhoto,
                onAvailability,
                snackbar,
                backEnabled = photo.canChoose,
            )
        }
    }
    if (discard) {
        ConfirmationDialog(
            stringResource(R.string.campsite_edit_discard_title),
            stringResource(R.string.campsite_edit_discard_body),
            model::confirmDiscard,
            model::dismissDiscard,
            model::dismissDiscard,
            confirmLabel = stringResource(R.string.campsite_discard_confirm),
            destructive = true,
        )
    }
}

/** Native selection does not upload; explicit image actions use the shared slot controller. */
@Composable
private fun CampsiteEditPhoto(
    model: CampsiteEditViewModel,
    enabled: Boolean,
    reader: PhotoContentReader = rememberPhotoContentReader(),
) {
    val photo by model.photoState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selecting by remember { mutableStateOf(false) }
    DisposableEffect(model) { onDispose { if (selecting) model.photoSelectionFailed() } }
    val choose =
        rememberPhotoPicker { reference ->
            if (reference != null) {
                selecting = true
                scope.launch {
                    try {
                        val selected = reader.read(reference)
                        currentCoroutineContext().ensureActive()
                        selecting = false
                        model.selectPhoto(selected)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        selecting = false
                        model.photoSelectionFailed()
                    }
                }
            }
        }
    ImageSlot(
        photo,
        choose,
        model::savePhoto,
        model::discardPhoto,
        model::removePhoto,
        model::retryPhoto,
        stringResource(R.string.campsite_edit_photo),
        Icons.Default.Place,
        imageModifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        enabled = enabled && !selecting,
    )
}
