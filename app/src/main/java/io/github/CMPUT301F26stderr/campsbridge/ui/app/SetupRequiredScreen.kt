package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen

/** Explicit pending onboarding boundary; never invents a profile or pretends setup succeeded. */
@Composable
fun SetupRequiredScreen() {
    AppScreen(stringResource(R.string.setup_title)) { Text(stringResource(R.string.setup_pending)) }
}
