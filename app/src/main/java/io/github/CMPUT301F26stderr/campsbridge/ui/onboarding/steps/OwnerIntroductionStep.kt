package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton

/** Introduces hosting without presenting the future campsite editor as implemented. */
@Composable
fun OwnerIntroductionStep() {
    Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.onboarding_host_body))
    AppActionButton(stringResource(R.string.onboarding_add_campsite), {}, enabled = false)
    Text(
        stringResource(R.string.onboarding_campsite_pending),
        style = MaterialTheme.typography.bodySmall,
    )
}
