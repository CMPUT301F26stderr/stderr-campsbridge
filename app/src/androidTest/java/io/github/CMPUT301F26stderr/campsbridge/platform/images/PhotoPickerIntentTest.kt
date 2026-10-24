package io.github.CMPUT301F26stderr.campsbridge.platform.images

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.CMPUT301F26stderr.campsbridge.testsupport.ControlledPhotoPicker
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixturePhotoUri
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Verifies the production native picker launcher, request restriction, and result callbacks. */
class PhotoPickerIntentTest {
    @get:Rule val compose = createComposeRule()

    /** The actual launcher requests images and reports the selected content reference. */
    @Test fun imageOnlyPickerDeliversSelection() {
        val selected = fixturePhotoUri()
        val picker = ControlledPhotoPicker(selected)
        var received: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                CampsbridgeTheme {
                    val launch = rememberPhotoPicker { received = it }
                    Button(onClick = launch) { Text("Select image") }
                }
            }
        }
        compose.onNodeWithText("Select image").performClick()
        compose.runOnIdle {
            assertEquals(1, picker.launches)
            assertNotNull(picker.lastIntent?.action)
            assertEquals("image/*", picker.lastIntent?.type)
            assertEquals(selected.toString(), received)
        }
    }

    /** Cancellation is delivered as null rather than a fabricated selection. */
    @Test fun cancelledPickerReportsNoSelection() {
        val picker = ControlledPhotoPicker(null)
        var received: String? = "not called"
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                val launch = rememberPhotoPicker { received = it }
                Button(onClick = launch) { Text("Select image") }
            }
        }
        compose.onNodeWithText("Select image").performClick()
        compose.runOnIdle {
            assertEquals(1, picker.launches)
            assertNull(received)
        }
    }
}
