/*
 * Owns the shared app bar, Back input, system insets, gutters, and optional bottom actions.
 */

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Measured clearance that lets lists scroll their final row above the floating action. */
internal val LocalFloatingActionPadding = staticCompositionLocalOf { 0.dp }

/**
 * Owns the shared app bar, Back input, system insets, gutters, and optional bottom actions.
 * @param title Localized page title, or null to omit the app bar.
 * @param modifier Caller layout.
 * @param onBack Optional return action; absent on root destinations.
 * @param snackbarHostState Optional caller-owned snackbar state.
 * @param bottomContent Optional actions kept above navigation and keyboard insets.
 * @param actions Page-level app-bar actions.
 * @param content Page content; the caller chooses whether it scrolls.
 * @param backEnabled Whether the current operation allows leaving through any Back input.
 * @param floatingActionButton Primary page action placed by the Material scaffold.
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
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionEnabled = LocalScreenInteractionEnabled.current
    val appBarScroll = if (title != null) TopAppBarDefaults.pinnedScrollBehavior() else null
    var floatingActionHeight by remember { mutableIntStateOf(0) }
    val floatingActionPadding =
        if (floatingActionHeight == 0) {
            0.dp
        } else {
            with(LocalDensity.current) { floatingActionHeight.toDp() } + Spacing.medium * 2
        }
    Scaffold(
        modifier =
            modifier.fillMaxSize().then(screenBackGesture(onBack, backEnabled)).then(
                if (appBarScroll != null) Modifier.nestedScroll(appBarScroll.nestedScrollConnection) else Modifier,
            ),
        topBar = {
            if (title != null) {
                val colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                val heading: @Composable () -> Unit = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onBack != null) {
                    TopAppBar(
                        title = heading,
                        actions = actions,
                        navigationIcon = {
                            IconButton(onClick = onBack, enabled = backEnabled && interactionEnabled) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.back),
                                )
                            }
                        },
                        colors = colors,
                        scrollBehavior = appBarScroll,
                    )
                } else {
                    TopAppBar(title = heading, actions = actions, colors = colors, scrollBehavior = appBarScroll)
                }
            }
        },
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        bottomBar = { Box(Modifier.navigationBarsPadding().imePadding()) { bottomContent() } },
        floatingActionButton = {
            Box(Modifier.onSizeChanged { floatingActionHeight = it.height }) {
                floatingActionButton()
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalFloatingActionPadding provides floatingActionPadding) {
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
}
