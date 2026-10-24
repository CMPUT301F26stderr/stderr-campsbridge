package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.components.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Interactive local examples, including loading, empty, failure, and pending states.
 * @param settings Accessibility settings used only by this demo.
 */
@Composable
fun ComponentGallery(settings: AccountSettings = AccountSettings()) {
    var name by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    CampsbridgeTheme(settings) {
        AppScreen(stringResource(R.string.gallery_title)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                Text(stringResource(R.string.gallery_intro))
                AppTextField(
                    name,
                    {
                        name = it
                    },
                    stringResource(
                        R.string.gallery_name,
                    ),
                    errorMessage =
                        if (name.isBlank()) {
                            stringResource(
                                R.string.gallery_name_error,
                            )
                        } else {
                            null
                        },
                )
                AppActionButton(stringResource(R.string.gallery_confirm), { showDialog = true })
                AppActionButton(stringResource(R.string.gallery_pending), {}, isSubmitting = true)
                AppActionButton(stringResource(R.string.gallery_disabled), {}, enabled = false)
                LoadingContent()
                EmptyContent(stringResource(R.string.gallery_empty))
                FailureContent(stringResource(R.string.gallery_failure), { showDialog = true })
                TextButton(
                    onClick = { name = "Alex Chen" },
                ) { Text(stringResource(R.string.gallery_fill)) }
            }
        }
        if (showDialog) {
            ConfirmationDialog(
                stringResource(
                    R.string.gallery_dialog,
                ),
                stringResource(R.string.gallery_dialog_body),
                {
                    showDialog =
                        false
                },
                { showDialog = false },
                { showDialog = false },
            )
        }
    }
}

/** Shows the shared component family without SDK/network dependencies. */
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun GalleryPreview() {
    ComponentGallery()
}

/** Checks narrow screens with large system and application text. */
@Preview(showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun EnlargedGalleryPreview() {
    ComponentGallery(AccountSettings(fontScale = 1.25, controlScale = 1.5))
}
