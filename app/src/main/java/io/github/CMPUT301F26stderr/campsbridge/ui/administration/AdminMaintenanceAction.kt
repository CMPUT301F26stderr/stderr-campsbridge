/*
 * Offers an explicit recovery pass while keeping failure separate from completion.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Offers an explicit recovery pass while keeping failure separate from completion. It sits in a compact tonal
 * card above the browse tabs, with the outcome above its action.
 * @param state Current pass outcome.
 * @param onResume Starts or retries the existing administrative operation.
 */
@Composable
fun AdminMaintenanceAction(
    state: AdminMaintenanceState,
    onResume: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(bottom = Spacing.small),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(start = Spacing.medium, end = Spacing.small, top = Spacing.medium, bottom = Spacing.small),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(
                stringResource(
                    when (state) {
                        AdminMaintenanceState.COMPLETE -> R.string.admin_maintenance_complete
                        AdminMaintenanceState.FAILED -> R.string.admin_maintenance_failed
                        else -> R.string.admin_maintenance_description
                    },
                ),
                Modifier.padding(end = Spacing.small).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (state == AdminMaintenanceState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            FilledTonalButton(
                onResume,
                Modifier.align(Alignment.End),
                enabled = state != AdminMaintenanceState.RUNNING,
            ) {
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
        }
    }
}

/** Passive examples of ready, running, failed and completed recovery. */
private class MaintenancePreviews : PreviewParameterProvider<AdminMaintenanceState> {
    override val values = AdminMaintenanceState.entries.asSequence()
}

/** Shows every outcome with the shared theme and enlarged text.
 * @param state Passive recovery outcome.
 */
@Preview(showBackground = true, widthDp = 360, fontScale = 2f)
@Preview(showBackground = true, widthDp = 390)
@Composable
private fun AdminMaintenancePreview(
    @PreviewParameter(MaintenancePreviews::class) state: AdminMaintenanceState,
) {
    CampsbridgeTheme { AdminMaintenanceAction(state, {}) }
}
