package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.platform.images.PhotoContentReader
import io.github.CMPUT301F26stderr.campsbridge.platform.images.rememberPhotoContentReader
import io.github.CMPUT301F26stderr.campsbridge.platform.images.rememberPhotoPicker
import io.github.CMPUT301F26stderr.campsbridge.platform.maps.LocationPicker
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Binds one graph-owned local draft to native selection and explicit navigation results.
 * @param model Parent setup owner, shared with children.
 * @param onExit Removes this parent after explicit exit.
 * @param onSaved Opens the actual saved identity after removing setup.
 * @param onOpenPolicy Opens the packaged refund child.
 * @param onChooseLocation Registered map child action; standalone content tests can omit it.
 * @param photoReader Native content adapter, controllable for interrupted-read tests.
 */
@Composable
fun CampsiteSetupRoute(
    model: CampsiteSetupViewModel,
    onExit: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenPolicy: () -> Unit,
    onChooseLocation: (() -> Unit)? = null,
    photoReader: PhotoContentReader = rememberPhotoContentReader(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    val completed by model.completion.collectAsStateWithLifecycle()
    val exit by model.exitRequested.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val selectionScope = rememberCoroutineScope()
    var selectionJob by remember { mutableStateOf<Job?>(null) }
    var selectionPending by remember { mutableStateOf(false) }

    /** Cancels an explicitly abandoned read without reporting an unexpected interruption. */
    fun cancelSelection() {
        selectionPending = false
        selectionJob?.cancel()
        selectionJob = null
    }
    DisposableEffect(model) {
        onDispose {
            if (selectionPending) model.photoSelectionFailed()
        }
    }
    LaunchedEffect((state as? CampsiteSetupUiState.Content)?.draft?.step) {
        if ((state as? CampsiteSetupUiState.Content)?.draft?.step != CampsiteStep.PHOTO) {
            cancelSelection()
        }
    }
    val choose =
        rememberPhotoPicker { reference ->
            if (reference != null) {
                cancelSelection()
                selectionPending = true
                selectionJob =
                    selectionScope.launch {
                        try {
                            val photo = photoReader.read(reference)
                            currentCoroutineContext().ensureActive()
                            selectionPending = false
                            model.selectPhoto(photo)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            currentCoroutineContext().ensureActive()
                            selectionPending = false
                            model.photoSelectionFailed()
                        }
                    }
            }
        }
    LaunchedEffect(lifecycle, completed, exit) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            if (completed != null) {
                onSaved(checkNotNull(completed))
                model.acknowledgeCompletion()
            } else if (exit) {
                onExit()
                model.acknowledgeExit()
            }
        }
    }
    CampsiteSetupScreen(
        state,
        model::back,
        model::next,
        {
            cancelSelection()
            model.requestExit()
        },
        model::dismissDiscard,
        model::confirmDiscard,
        model::retry,
        model::editStep,
        model::editName,
        model::editDescription,
        model::editLocationLabel,
        model::editTimeZone,
        model::editCapacity,
        model::editPrice,
        model::editRules,
        model::setAmenity,
        model::setTag,
        choose,
        {
            cancelSelection()
            model.discardPhoto()
        },
        {
            cancelSelection()
            model.removePhoto()
        },
        model::retryPhoto,
        model::changeMonth,
        model::toggleDate,
        model::retryCalendar,
        onOpenPolicy,
        onChooseLocation = onChooseLocation,
        onSaveDraft = model::saveDraft,
        onRetrySave = model::retrySave,
    )
}

/** Binds the restored setup parent to its location child without creating another draft owner.
 * @param model Actual graph-owned setup model.
 * @param onBack Returns to the setup steps without abandoning their draft.
 */
@Composable
fun CampsiteLocationRoute(
    model: CampsiteSetupViewModel,
    onBack: () -> Unit,
) {
    val input by model.state.collectAsStateWithLifecycle()
    val content = input as? CampsiteSetupUiState.Content
    if (content != null) {
        LocationPicker(
            content.draft.location,
            onApply = {
                model.selectLocation(it)
                onBack()
            },
            onCancel = onBack,
        )
    } else {
        BackHandler(onBack = onBack)
        AppScreen(stringResource(R.string.location_picker_title), onBack = onBack) {
            when (input) {
                CampsiteSetupUiState.Loading -> {
                    LoadingContent()
                }

                CampsiteSetupUiState.Failure -> {
                    FailureContent(
                        stringResource(R.string.campsite_detail_failure),
                        model::retry,
                    )
                }

                else -> {
                    Text(stringResource(R.string.campsite_unavailable))
                }
            }
        }
    }
}
