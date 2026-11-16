/*
 * Connects the published campsite's QR generator to the saved graph owner.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Connects the published campsite's QR generator to the saved graph owner.
 * @param model Management graph owner.
 * @param onBack Returns to the campsite management summary.
 */
@Composable
fun CampsiteQrRoute(
    model: CampsiteDetailViewModel,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val image by model.qrImageState.collectAsStateWithLifecycle()
    LaunchedEffect(model) {
        model.startQr()
    }
    CampsiteQrScreen(
        state,
        image,
        onBack = onBack,
        onRetry = {
            model.retry()
            model.retryQr()
        },
    )
}
