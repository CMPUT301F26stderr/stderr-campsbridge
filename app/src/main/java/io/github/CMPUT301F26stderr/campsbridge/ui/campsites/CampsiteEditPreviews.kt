/*
 * Displays the grouped saved editor at normal and enlarged type.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteBasicsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Displays the grouped saved editor at normal and enlarged type.
 * @param state Confirmed input or a required-source outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large saved editor", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun CampsiteEditPreview(
    @PreviewParameter(CampsiteEditPreviewProvider::class) state: CampsiteEditUiState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        CampsiteEditScreen(
            state,
            previewCampsitePhoto(),
            {},
            {},
            {},
            {},
            {},
            {},
            remember { SnackbarHostState() },
        )
    }
}

/** Keeps a long saved name and multiline input readable at enlarged scales. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large saved basics", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun CampsiteEditBasicsPreview() {
    val draft =
        campsiteDraft(previewCampsite()).copy(
            name = "Riverbend family campsite and riverside pitches",
            description = "Quiet nights beside the river.\nRoom for the whole family.",
        )
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        CampsiteEditFieldsScreen(
            stringResource(
                R.string.campsite_edit_basics,
            ),
            CampsiteEditUiState.Content(draft),
            {
            },
            {},
        ) {
            CampsiteBasicsStep(draft.name, draft.description, false, {}, {})
        }
    }
}

/** Shows confirmed absence and independent photo controls without faking a saved image. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large empty photo", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun CampsiteEditPhotoPreview() {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        CampsitePhotoScreen(ImageSlotState(loadState = ImageLoadState.READY), false, {}, {}, {}, {}, {}, {})
    }
}
