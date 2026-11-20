package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Offers an explicit recovery pass while keeping failure separate from completion.
 * @param state Current pass outcome.
 * @param onResume Starts or retries the existing administrative operation.
 */
@Composable
fun AdminMaintenanceAction(
    state: AdminMaintenanceState,
    onResume: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        TextButton(onResume, enabled = state != AdminMaintenanceState.RUNNING) {
            Text(
                stringResource(
                    when (state) {
                        AdminMaintenanceState.RUNNING -> R.string.admin_maintenance_running
                        AdminMaintenanceState.FAILED -> R.string.admin_maintenance_retry
                        else -> R.string.admin_maintenance_resume
                    },
                ),
            )
        }
        Text(
            stringResource(
                when (state) {
                    AdminMaintenanceState.COMPLETE -> R.string.admin_maintenance_complete
                    AdminMaintenanceState.FAILED -> R.string.admin_maintenance_failed
                    else -> R.string.admin_maintenance_description
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Passive examples of ready, running, failed and completed recovery. */
private class MaintenancePreviews : PreviewParameterProvider<AdminMaintenanceState> {
    override val values = AdminMaintenanceState.entries.asSequence()
}

/** Shows each recovery outcome with the shared theme.
 * @param state Passive recovery outcome.
 */
@Preview(showBackground = true, widthDp = 390)
@Composable
private fun AdminMaintenancePreview(
    @PreviewParameter(MaintenancePreviews::class) state: AdminMaintenanceState,
) {
    CampsbridgeTheme { AdminMaintenanceAction(state, {}) }
}
