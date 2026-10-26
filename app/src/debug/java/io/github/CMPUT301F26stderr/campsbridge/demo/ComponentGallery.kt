package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Two offline gallery pages invoking production components with explicitly local interactions.
 * @param settings Initial accessibility settings.
 * @param initialPage Controls or content; used by debug launch and visual review.
 * @param initialSection Zero-based section to reveal on initial launch.
 */
@Composable
fun ComponentGallery(
    settings: AccountSettings = AccountSettings(),
    initialPage: Int = 0,
    initialSection: Int = 0,
) {
    var page by rememberSaveable { mutableStateOf(initialPage.coerceIn(0, 1)) }
    var large by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedLabel = stringResource(R.string.gallery_action_selected)
    val effective = if (large) settings.copy(fontScale = 1.25, controlScale = 1.5) else settings
    CampsbridgeTheme(effective) {
        AppScreen(stringResource(R.string.gallery_title), snackbarHostState = snackbar, actions = {
            TextButton(
                onClick = { large = !large },
            ) { Text(stringResource(R.string.gallery_large_text)) }
        }) {
            Text(
                stringResource(R.string.gallery_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryTabRow(selectedTabIndex = page) {
                Tab(
                    page == 0,
                    onClick = { page = 0 },
                    text = { Text(stringResource(R.string.gallery_controls)) },
                )
                Tab(
                    page == 1,
                    onClick = { page = 1 },
                    text = { Text(stringResource(R.string.gallery_content)) },
                )
            }
            val controlsScroll = rememberLazyListState()
            val contentScroll = rememberLazyListState()
            LaunchedEffect(Unit) {
                if (initialSection > 0) {
                    if (page == 0) {
                        controlsScroll.scrollToItem(initialSection.coerceAtMost(6))
                    } else {
                        contentScroll.scrollToItem(initialSection.coerceAtMost(8))
                    }
                }
            }
            if (page == 0) {
                ControlsGallery(controlsScroll)
            } else {
                ContentGallery(contentScroll) { value ->
                    scope.launch { snackbar.showSnackbar("$selectedLabel $value") }
                }
            }
        }
    }
}

/** Shared gallery section with restrained typography and no decorative container.
 * @param title Localized section label.
 * @param content Real production component examples.
 */
@Composable
internal fun GallerySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.padding(vertical = Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** Shows the full production component family without SDK/network dependencies. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ControlsPreview() {
    ComponentGallery()
}

/** Shows realistic app content assembled from shared components. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ContentPreview() {
    ComponentGallery(initialPage = 1)
}

/** Enlarged text preview retains the same production theme and components. */
@Preview(showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun EnlargedGalleryPreview() {
    ComponentGallery(AccountSettings(fontScale = 1.25, controlScale = 1.5))
}
