package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Verifies the same themed preview content on a device, without production navigation/writes. */
class BookingPreviewsTest {
    @get:Rule val compose = createComposeRule()

    /** Captures normal/narrow enlarged states and submitting feedback for visual inspection. */
    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun themedStatesRenderAtNormalAndLargeFontSizes() {
        val current = mutableStateOf<BookingUiState>(previewBooking())
        val fontScale = mutableStateOf(1f)
        val submitting = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale.value),
            ) {
                val large = fontScale.value > 1f
                Box(
                    Modifier
                        .width(if (large) 360.dp else 390.dp)
                        .height(if (large) 800.dp else 844.dp)
                        .testTag("booking-preview-root"),
                ) {
                    key(current.value, fontScale.value, submitting.value) {
                        BookingPreviewContent(current.value, submitting.value)
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "bk3-previews")
        assertTrue(directory.isDirectory || directory.mkdirs())
        for (scale in listOf(1f, 2f)) {
            for ((index, state) in BookingPreviewProvider().values.withIndex()) {
                compose.runOnIdle {
                    fontScale.value = scale
                    current.value = state
                    submitting.value = false
                }
                compose.waitForIdle()
                if (state is BookingUiState.Content) {
                    val conflict =
                        state.error ==
                            BookingStepError.Dates(
                                StaySelectionInput.UNAVAILABLE_NIGHTS,
                            )
                    compose
                        .onNodeWithText(
                            if (state.draft.step != BookingStep.REVIEW) {
                                "Next"
                            } else if (conflict) {
                                "Choose dates"
                            } else {
                                "Confirm reservation"
                            },
                        ).assertIsDisplayed()
                }
                capture(File(directory, "state-$index-font-${scale.toInt()}.png"))
                if (state is BookingUiState.Content && state.draft.step == BookingStep.REVIEW) {
                    compose
                        .onNodeWithText(
                            context.getString(R.string.reservation_simulated_payment),
                        ).performScrollTo()
                        .assertIsDisplayed()
                    capture(File(directory, "state-$index-bottom-font-${scale.toInt()}.png"))
                }
                if (state is BookingUiState.Content &&
                    state.draft.step == BookingStep.TERMS &&
                    '\n' in state.campsite.details.rules
                ) {
                    compose
                        .onNodeWithText(
                            context.getString(R.string.booking_agreement),
                        ).performScrollTo()
                        .assertIsDisplayed()
                    capture(File(directory, "long-rules-bottom-font-${scale.toInt()}.png"))
                }
            }
            compose.runOnIdle {
                current.value = previewBooking(BookingStep.REVIEW).copy(canConfirm = true)
                submitting.value = true
            }
            compose.waitForIdle()
            capture(File(directory, "submitting-font-${scale.toInt()}.png"))
        }
    }

    /** Saves the bounded preview surface, keeping system UI outside the test artifact.
     * @param file Local device test-output target.
     */
    private fun capture(file: File) {
        val bitmap =
            compose
                .onNodeWithTag(
                    "booking-preview-root",
                ).captureToImage()
                .asAndroidBitmap()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
