package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R

/** Shares the short hosting introduction; the containing flow owns its actual completion action. */
@Composable
fun OwnerIntroductionStep() {
    Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.onboarding_host_body))
}
