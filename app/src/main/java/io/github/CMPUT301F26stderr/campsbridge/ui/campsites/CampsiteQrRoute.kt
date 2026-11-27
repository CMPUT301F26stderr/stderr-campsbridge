/*
 * Generates display pixels off the main thread for the current saved published identity.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Generates display pixels off the main thread for the current saved published identity.
 * @param model Retained campsite manager's saved source, shared with its QR child.
 * @param createBitmap Existing native encoder; output remains local to this composition.
 * @param onBack Pops this child through the same arrow/gesture callback.
 */
@Composable
fun CampsiteQrRoute(
    model: CampsiteDetailViewModel,
    createBitmap: (String) -> Bitmap,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val id = (state as? CampsiteDetailUiState.Content)?.campsite?.takeIf { it.isPublished }?.id
    var attempt by remember { mutableIntStateOf(0) }
    val image by rememberCampsiteQrImage(id, attempt, createBitmap)
    CampsiteQrScreen(state, image, onBack) { if (id == null) model.retry() else attempt++ }
}

/** Keeps native pixels within one composition and propagates cancellation without a false failure.
 * @param id Current published identity, absent while its required source is unavailable.
 * @param attempt Explicit generation retry number.
 * @param createBitmap Native encoder called on the worker dispatcher.
 */
@Composable
internal fun rememberCampsiteQrImage(
    id: String?,
    attempt: Int,
    createBitmap: (String) -> Bitmap,
): State<CampsiteQrImage> =
    produceState<CampsiteQrImage>(CampsiteQrImage.Loading, id, attempt, createBitmap) {
        value = CampsiteQrImage.Loading
        if (id != null) {
            value =
                try {
                    withContext(Dispatchers.Default) { CampsiteQrImage.Ready(createBitmap(id).asImageBitmap()) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    CampsiteQrImage.Failure
                }
        }
    }
