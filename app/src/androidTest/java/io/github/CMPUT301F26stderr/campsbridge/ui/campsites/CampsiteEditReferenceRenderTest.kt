package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps.CampsiteBasicsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Captures the reference screens with the same font and control scaling as their previews. */
class CampsiteEditReferenceRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun referenceSectionsRemainReachableAtNormalAndEnlargedScales() {
        val font = mutableStateOf(1f)
        val page = mutableStateOf("menu")
        val state = mutableStateOf(CampsiteEditUiState.Content(campsiteDraft(previewCampsite()), isDirty = true))
        val photo = ImageSlotState(loadState = ImageLoadState.READY)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, font.value)) {
                Box(Modifier.width(if (font.value == 1f) 390.dp else 360.dp).height(844.dp).testTag("cs1-reference")) {
                    key(font.value, page.value, state.value) {
                        CampsbridgeTheme(AccountSettings(controlScale = font.value.toDouble())) {
                            when (page.value) {
                                "photo" -> {
                                    CampsitePhotoScreen(photo, false, {}, {}, {}, {}, {}, {})
                                }

                                "basics" -> {
                                    CampsiteEditFieldsScreen(
                                        stringResource(R.string.campsite_edit_basics),
                                        state.value,
                                        {},
                                        {},
                                    ) {
                                        CampsiteBasicsStep(
                                            state.value.draft.name,
                                            state.value.draft.description,
                                            false,
                                            {},
                                            {},
                                        )
                                    }
                                }

                                else -> {
                                    CampsiteEditScreen(
                                        state.value,
                                        photo,
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
                        }
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "cs1-reference")
        assertTrue(output.isDirectory || output.mkdirs())
        for (scale in listOf(1f, 2f)) {
            compose.runOnIdle {
                font.value = scale
                page.value = "menu"
            }
            compose.onNodeWithText("Basics").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Save details").assertIsDisplayed()
            capture(File(output, "menu-${scale.toInt()}x.png"))
            compose.onNodeWithText("Availability").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Save details").assertIsDisplayed()
            compose.runOnIdle { page.value = "basics" }
            compose.onNodeWithText("Campsite name").performScrollTo().assertIsDisplayed()
            capture(File(output, "basics-${scale.toInt()}x.png"))
            compose.runOnIdle { page.value = "photo" }
            compose.onNodeWithText("Choose photo").performScrollTo().assertIsDisplayed()
            capture(File(output, "photo-${scale.toInt()}x.png"))
        }
        compose.runOnIdle {
            page.value = "menu"
            state.value = state.value.copy(saveFailed = true)
        }
        compose.onNodeWithText("Retry").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save details").assertIsDisplayed()
        capture(File(output, "failed-save-2x.png"))
    }

    private fun capture(path: File) {
        val bitmap = compose.onNodeWithTag("cs1-reference").captureToImage().asAndroidBitmap()
        path.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
