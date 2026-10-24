package io.github.CMPUT301F26stderr.campsbridge.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Presents one focused wizard step with scrolling content and a reachable bottom action.
 * @param title Localized step title.
 * @param progress Localized position, such as Step 1 of 3.
 * @param onBack Return action.
 * @param onNext Explicit continuation action.
 * @param modifier Caller layout.
 * @param nextEnabled Whether input permits continuation.
 * @param isSubmitting Whether continuation is pending.
 * @param content Step fields/content without application wiring.
 */
@Composable
fun StepScaffold(
    title: String,
    progress: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    nextEnabled: Boolean = true,
    isSubmitting: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppScreen(
        title = title,
        modifier = modifier,
        onBack = onBack,
        bottomContent = {
            AppActionButton(
                stringResource(R.string.next),
                onNext,
                Modifier.fillMaxWidth().padding(Spacing.screen),
                nextEnabled,
                isSubmitting,
            )
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            Text(progress)
            content()
            Spacer(Modifier.height(Spacing.large))
        }
    }
}
