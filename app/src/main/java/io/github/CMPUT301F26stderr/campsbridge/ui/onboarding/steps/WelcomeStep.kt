package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
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
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(vertical = Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.large, Alignment.CenterVertically),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Image(
                painterResource(R.drawable.onboarding_campsite),
                contentDescription = null,
                modifier = Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp),
            )
            Text(
                stringResource(R.string.onboarding_purpose),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
