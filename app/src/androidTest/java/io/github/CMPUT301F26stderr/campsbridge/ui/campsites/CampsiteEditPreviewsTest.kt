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
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Verifies reachable editor actions on bounded normal and enlarged layouts, and exports evidence. */
class CampsiteEditPreviewsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun menuAndSaveRemainReachableWithLargeTextAndFailureFeedback() {
        val scale = mutableStateOf(1f)
        val failure = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale.value)) {
                Box(Modifier.width(if (scale.value > 1f) 360.dp else 390.dp).height(800.dp).testTag("edit-preview")) {
                    key(scale.value, failure.value) {
                        CampsbridgeTheme {
                            CampsiteEditScreen(
                                CampsiteEditUiState.Content(
                                    sampleCampsite().toDraft(),
                                    isDirty = true,
                                    saveFailed = failure.value,
                                ),
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "cs1-previews")
        assertTrue(directory.isDirectory || directory.mkdirs())
        for (font in listOf(1f, 2f)) {
            compose.runOnIdle { scale.value = font }
            compose.onNodeWithText("Name and description").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Save details").assertIsDisplayed()
            capture(File(directory, "editor-font-${font.toInt()}.png"))
            compose.onNodeWithText("Availability").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Save details").assertIsDisplayed()
        }
        compose.runOnIdle { failure.value = true }
        compose
            .onNodeWithText(
                "Could not save details. Your edits are kept for retry.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Save details").assertIsDisplayed()
        capture(File(directory, "editor-failure-large.png"))
    }

    private fun capture(file: File) {
        val bitmap = compose.onNodeWithTag("edit-preview").captureToImage().asAndroidBitmap()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
