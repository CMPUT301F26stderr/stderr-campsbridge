/*
 * Connects submitted criteria and visible-row read lifetime to the graph-owned model.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.platform.qr.CampsiteQrPayload
import io.github.CMPUT301F26stderr.campsbridge.platform.qr.QrScanResult
import io.github.CMPUT301F26stderr.campsbridge.platform.qr.rememberQrScanner
import kotlinx.coroutines.flow.distinctUntilChanged

/** Connects submitted criteria and visible-row read lifetime to the graph-owned model.
 * @param model Primary discovery owner, shared with its filter child.
 * @param onOpenFilters Opens the registered working filter child.
 * @param onOpenCampsite Opens a separate saved-ID detail parent.
 */
@Composable
fun ExploreRoute(
    model: ExploreViewModel,
    onOpenFilters: () -> Unit,
    onOpenCampsite: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var feedback by rememberSaveable { mutableStateOf<QrScanFeedback?>(null) }
    var decodedId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val scan =
        rememberQrScanner { result ->
            when (result) {
                QrScanResult.Cancelled -> {
                    Unit
                }

                QrScanResult.PermissionDenied -> {
                    feedback = QrScanFeedback.PERMISSION_DENIED
                }

                QrScanResult.Failed -> {
                    feedback = QrScanFeedback.STARTUP_FAILED
                }

                is QrScanResult.Decoded -> {
                    val id = CampsiteQrPayload.decode(result.text)
                    if (id == null) {
                        feedback = QrScanFeedback.FOREIGN_CODE
                    } else {
                        feedback = null
                        decodedId = id
                    }
                }
            }
        }
    LaunchedEffect(decodedId, lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            decodedId?.let {
                onOpenCampsite(it)
                decodedId = null
            }
        }
    }
    LaunchedEffect(model, list) {
        snapshotFlow {
            list.layoutInfo.visibleItemsInfo
                .mapNotNull { it.key as? String }
                .toSet()
        }.distinctUntilChanged()
            .collect(model::setVisibleCampsites)
    }
    DisposableEffect(model) { onDispose { model.setVisibleCampsites(emptySet()) } }
    ExploreScreen(
        state,
        model::editQuery,
        model::submitSearch,
        {
            model.beginFilters()
            onOpenFilters()
        },
        onOpenCampsite,
        model::retry,
        model::retryRow,
        list,
        onScan = scan,
        scanFeedback = feedback,
        onOpenSettings = {
            try {
                settings.launch(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            } catch (_: ActivityNotFoundException) {
                feedback = QrScanFeedback.STARTUP_FAILED
            }
        },
    )
}
