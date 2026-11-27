/*
 * Connects native photo selection to the existing independent image controller.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.platform.images.rememberPhotoContentReader
import io.github.CMPUT301F26stderr.campsbridge.platform.images.rememberPhotoPicker
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.ImageSlot
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Connects native photo selection to the existing independent image controller.
 * @param model Saved edit graph owner; returning keeps its details and photo choice.
 * @param onBack Returns to the edit summary without implicitly saving or discarding a choice.
 */
@Composable
fun CampsitePhotoRoute(
    model: CampsiteEditViewModel,
    onBack: () -> Unit,
) {
    val target by model.state.collectAsStateWithLifecycle()
    val photo by model.photo.state.collectAsStateWithLifecycle()
    val reader = rememberPhotoContentReader()
    val scope = rememberCoroutineScope()
    var selecting by remember { mutableStateOf(false) }
    DisposableEffect(model) {
        onDispose { if (selecting) model.photo.selectionFailed() }
    }
    val choose =
        rememberPhotoPicker { uri ->
            if (uri != null) {
                selecting = true
                scope.launch {
                    try {
                        val selected = reader.read(uri)
                        currentCoroutineContext().ensureActive()
                        model.photo.select(selected)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        model.photo.selectionFailed()
                    } finally {
                        selecting = false
                    }
                }
            }
        }
    if (target is CampsiteEditUiState.Content) {
        CampsitePhotoScreen(
            photo,
            selecting,
            onBack,
            choose,
            model.photo::save,
            model.photo::discard,
            model.photo::remove,
            model.photo::retry,
        )
    } else {
        CampsiteEditFieldsScreen(stringResource(R.string.campsite_edit_photo), target, onBack, model::retry) {}
    }
}

/** Shows explicit attachment actions independently of the parent details save.
 * @param state Existing image controller state.
 * @param selecting Whether native content is being read.
 * @param onBack Returns with any unsaved choice retained by the parent graph.
 * @param onChoose Opens the native photo picker.
 * @param onSave Saves this image only.
 * @param onDiscard Discards the local selection only.
 * @param onRemove Removes the current attachment after the shared confirmation.
 * @param onRetry Retries the existing controller's failed stage.
 */
@Composable
internal fun CampsitePhotoScreen(
    state: ImageSlotState,
    selecting: Boolean,
    onBack: () -> Unit,
    onChoose: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    AppScreen(
        stringResource(R.string.campsite_edit_photo),
        onBack = onBack,
        backEnabled = state.canChoose && !selecting,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            Text(stringResource(R.string.campsite_edit_photo_help))
            ImageSlot(
                state,
                onChoose,
                onSave,
                onDiscard,
                onRemove,
                onRetry,
                stringResource(R.string.campsite_edit_photo_description),
                Icons.Default.Add,
                imageModifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                enabled = !selecting,
            )
        }
    }
}
