package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.platform.images.rememberPhotoContentReader
import io.github.CMPUT301F26stderr.campsbridge.platform.images.rememberPhotoPicker
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.ImageSlot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Connects graph-owned state and native picker results to pure profile content.
 * @param factory Explicit feature dependencies.
 * @param modifier Navigation host layout.
 */
@Composable
fun ProfileRoute(
    factory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val model: ProfileViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
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
    ProfileScreen(state, model::retry, modifier) {
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
        )
    }
}
