package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import io.github.CMPUT301F26stderr.campsbridge.platform.images.rememberPhotoContentReader
import io.github.CMPUT301F26stderr.campsbridge.platform.images.rememberPhotoPicker
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageOperation
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.ImageSlot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Connects the shared graph model to its menu without editing account or image fields.
 * @param model Parent profile graph owner.
 * @param onPersonalDetails Opens the personal editor.
 * @param onContactDetails Opens the contact editor.
 * @param modifier Navigation host layout.
 * @param onSettings Opens registered settings, if available.
 * @param workspaceContent Shared capability-limited picker.
 * @param onStartHosting Opens the existing-account hosting introduction.
 */
@Composable
fun ProfileRoute(
    model: ProfileViewModel,
    onPersonalDetails: () -> Unit,
    onContactDetails: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
    workspaceContent: @Composable () -> Unit = {},
    onStartHosting: (() -> Unit)? = null,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val photo by model.photoState.collectAsStateWithLifecycle()
    ProfileScreen(
        state,
        model::retry,
        modifier,
        onPersonalDetails,
        onContactDetails,
        onSettings,
        photo.displayUrl,
        workspaceContent,
        onStartHosting,
    )
}

/** Connects one personal editor to its shared model and the Android Photo Picker.
 * @param model Parent profile graph owner.
 * @param onBack Actual parent return, after discard or confirmed completion.
 */
@Composable
fun ProfileDetailsRoute(
    model: ProfileViewModel,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val photo by model.photoState.collectAsStateWithLifecycle()
    LaunchedEffect(model, state is ProfileUiState.Content) {
        if (state is ProfileUiState.Content) model.beginPersonalEdit()
    }
    ProfileSaveCompletion(model, ProfileEditor.PERSONAL, onBack)
    ProfileDetailsScreen(
        state,
        model::editName,
        model::saveName,
        onBack = { if (model.cancelPersonalEdit()) onBack() },
        onRetry = model::retry,
        photoBusy = photo.operation != ImageOperation.IDLE,
    ) {
        ProfilePhotoContent(
            model,
            (state as? ProfileUiState.Content)?.operation == ProfileOperation.IDLE,
        )
    }
}

/** Connects optional contacts to the same profile graph owner without another account listener.
 * @param model Parent profile graph owner.
 * @param onBack Actual parent return.
 */
@Composable
fun ContactDetailsRoute(
    model: ProfileViewModel,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model, state is ProfileUiState.Content) {
        if (state is ProfileUiState.Content) model.beginContactEdit()
    }
    ProfileSaveCompletion(model, ProfileEditor.CONTACT, onBack)
    ContactDetailsScreen(
        state,
        model::editEmail,
        model::editPhone,
        model::saveContacts,
        onBack = { if (model.cancelContactEdit()) onBack() },
        onRetry = model::retry,
    )
}

/** Handles confirmed navigation only while this editor is the resumed destination.
 * @param model Shared model whose completion is consumed after navigation.
 * @param editor This route's editor identity.
 * @param onSaved Pops to the actual parent after confirmed save.
 */
@Composable
private fun ProfileSaveCompletion(
    model: ProfileViewModel,
    editor: ProfileEditor,
    onSaved: () -> Unit,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsState()
    val completion by model.completion.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState, completion) {
        if (lifecycleState == Lifecycle.State.RESUMED && completion == editor) {
            onSaved()
            model.acknowledgeCompletion(editor)
        }
    }
}

/** Owns native picker IO while forwarding explicit image actions to the existing controller.
 * @param model Parent profile owner.
 * @param enabled Whether profile-field writes allow image actions.
 */
@Composable
private fun ProfilePhotoContent(
    model: ProfileViewModel,
    enabled: Boolean,
) {
    val photo by model.photoState.collectAsStateWithLifecycle()
    val reader = rememberPhotoContentReader()
    val selectionScope = rememberCoroutineScope()
    var selectionJob by remember { mutableStateOf<Job?>(null) }
    val choosePhoto =
        rememberPhotoPicker { reference ->
            if (reference != null) {
                selectionJob?.cancel()
                selectionJob =
                    selectionScope.launch {
                        try {
                            val selected = reader.read(reference)
                            currentCoroutineContext().ensureActive()
                            model.selectPhoto(selected)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            model.photoSelectionFailed()
                        }
                    }
            }
        }
    ImageSlot(
        state = photo,
        onChoosePhoto = choosePhoto,
        onSave = model::savePhoto,
        onDiscard = model::discardPhoto,
        onRemove = model::removePhoto,
        onRetry = model::retryPhoto,
        imageDescription = stringResource(R.string.profile_photo),
        emptyIcon = Icons.Default.AccountCircle,
        imageShape = CircleShape,
        enabled = enabled,
    )
}
