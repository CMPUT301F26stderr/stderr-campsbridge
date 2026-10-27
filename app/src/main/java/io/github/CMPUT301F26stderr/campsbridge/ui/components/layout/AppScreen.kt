package io.github.CMPUT301F26stderr.campsbridge.ui.components.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Owns the shared white app bar, system insets, gutters, and optional bottom actions.
 * @param title Localized page title, or null to omit the app bar.
 * @param modifier Caller layout.
 * @param onBack Optional return action; absent on root destinations.
 * @param snackbarHostState Optional caller-owned snackbar state.
 * @param bottomContent Optional actions kept above navigation and keyboard insets.
 * @param actions Page-level app-bar actions.
 * @param content Page content; the caller chooses whether it scrolls.
 * @param backEnabled Whether the visible Back action can be invoked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    bottomContent: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    backEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (title != null) {
                val colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    )
                val heading: @Composable () -> Unit = {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onBack != null) {
                    CenterAlignedTopAppBar(
                        title = heading,
                        actions = actions,
                        navigationIcon = {
                            IconButton(onClick = onBack, enabled = backEnabled) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.back),
                                )
                            }
                        },
                        colors = colors,
                    )
                } else {
                    TopAppBar(title = heading, actions = actions, colors = colors)
                }
            }
        },
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        bottomBar = { Box(Modifier.navigationBarsPadding().imePadding()) { bottomContent() } },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            content = content,
        )
    }
}
