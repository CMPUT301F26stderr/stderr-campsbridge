/*
 * Registers discovery/filter lifetime and a separate saved public listing reader.
 */

package io.github.CMPUT301F26stderr.campsbridge.navigation

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.platform.qr.CampsiteQrPayload
import io.github.CMPUT301F26stderr.campsbridge.platform.qr.QrScanResult
import io.github.CMPUT301F26stderr.campsbridge.platform.qr.rememberQrScanner
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.WorkspaceScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.SearchFiltersRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

private enum class ScanError {
    PermissionDenied,
    Failed,
    Foreign,
}

/** Registers discovery/filter lifetime and a separate saved public listing reader.
 * @param controller Account-scoped navigation host.
 * @param state Confirmed root session.
 * @param onSelectWorkspace Root-owned permitted workspace selection.
 * @param exploreFactory Dependencies for one parent discovery model.
 * @param detailFactory Dependencies for an ID/context-qualified saved reader.
 */
internal fun NavGraphBuilder.discoveryGraph(
    controller: NavHostController,
    state: SessionState.Ready,
    onSelectWorkspace: (Role) -> Unit,
    exploreFactory: ViewModelProvider.Factory,
    detailFactory: (String, CampsiteDetailContext) -> ViewModelProvider.Factory,
) {
    navigation(
        startDestination = AppDestination.EXPLORE + "/results",
        route = AppDestination.EXPLORE,
    ) {
        composable(AppDestination.EXPLORE + "/results") { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(AppDestination.EXPLORE) }
            val model: ExploreViewModel =
                viewModel(viewModelStoreOwner = parent, factory = exploreFactory)
            var scanError by remember { mutableStateOf<ScanError?>(null) }
            val context = LocalContext.current
            val launchSettings = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { }
            val launchScanner = rememberQrScanner { result ->
                when (result) {
                    QrScanResult.Cancelled -> {
                        scanError = null
                    }
                    QrScanResult.PermissionDenied -> {
                        scanError = ScanError.PermissionDenied
                    }
                    QrScanResult.Failed -> {
                        scanError = ScanError.Failed
                    }
                    is QrScanResult.Decoded -> {
                        val campsiteId = CampsiteQrPayload.decode(result.text)
                        if (campsiteId != null) {
                            scanError = null
                            controller.navigate(
                                AppDestination.campsiteGraph(Role.ENTRANT, campsiteId, CampsiteDetailContext.VIEW),
                            ) {
                                launchSingleTop = true
                            }
                        } else {
                            scanError = ScanError.Foreign
                        }
                    }
                }
            }
            WorkspaceScaffold(
                state,
                onSelectWorkspace,
                title = stringResource(R.string.discovery_title),
            ) {
                ExploreRoute(
                    model,
                    onOpenFilters = {
                        controller.navigate(AppDestination.EXPLORE + "/filters") {
                            launchSingleTop = true
                        }
                    },
                    onOpenCampsite = { id ->
                        controller.navigate(
                            AppDestination.campsiteGraph(Role.ENTRANT, id, CampsiteDetailContext.VIEW),
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onScan = launchScanner,
                )
                if (scanError != null) {
                    val currentError = scanError!!
                    val (titleRes, bodyRes) = when (currentError) {
                        ScanError.PermissionDenied -> R.string.scan_qr_permission_title to R.string.scan_qr_permission_body
                        ScanError.Failed -> R.string.scan_qr_failed_title to R.string.scan_qr_failed_body
                        ScanError.Foreign -> R.string.scan_qr_foreign_title to R.string.scan_qr_foreign_body
                    }
                    AlertDialog(
                        onDismissRequest = { scanError = null },
                        title = { Text(stringResource(titleRes)) },
                        text = { Text(stringResource(bodyRes)) },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                if (currentError == ScanError.PermissionDenied) {
                                    OutlinedButton(
                                        onClick = {
                                            val intent =
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", context.packageName, null)
                                                }
                                            launchSettings.launch(intent)
                                        },
                                    ) {
                                        Text(stringResource(R.string.scan_qr_settings))
                                    }
                                }
                                Button(onClick = launchScanner) {
                                    Text(stringResource(R.string.scan_qr_retry))
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { scanError = null }) {
                                Text(stringResource(R.string.cancel))
                            }
                        },
                    )
                }
            }
        }
        composable(AppDestination.EXPLORE + "/filters") { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(AppDestination.EXPLORE) }
            val model: ExploreViewModel =
                viewModel(viewModelStoreOwner = parent, factory = exploreFactory)
            SearchFiltersRoute(model) { controller.popBackStack() }
        }
    }
    campsiteReadGraph(controller, Role.ENTRANT, CampsiteDetailContext.VIEW, detailFactory)
}
