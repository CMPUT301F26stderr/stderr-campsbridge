package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.platform.assets.SceneryPhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.RemoteImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Presents the approved welcome artwork and one entry action without account operations.
 * @param onStart Starts the short setup flow.
 * @param onGuidelines Opens the read-only introduction.
 * @param modifier Caller layout.
 */
@Composable
fun WelcomeStep(
    onStart: () -> Unit,
    onGuidelines: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(title = null, modifier = modifier, bottomContent = {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppActionButton(
                stringResource(R.string.onboarding_get_started),
                onStart,
                Modifier.fillMaxWidth(),
            )
            TextButton(onGuidelines) { Text(stringResource(R.string.onboarding_guidelines)) }
        }
    }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(vertical = Spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.large),
                ) {
                    RemoteImage(
                        SceneryPhoto.JASPER.reference,
                        stringResource(R.string.welcome_scenery),
                        modifier = Modifier.size(200.dp).clip(CircleShape),
                    )
                    Text(
                        stringResource(R.string.onboarding_purpose),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(Spacing.large))
            }
        }
    }
}
