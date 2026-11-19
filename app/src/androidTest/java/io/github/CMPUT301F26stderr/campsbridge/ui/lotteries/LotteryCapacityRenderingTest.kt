package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.YearMonth

/** Keeps large capacity values and correction feedback reachable at supported text scales. */
class LotteryCapacityRenderingTest {
    @get:Rule val compose = createComposeRule()

    /** Winners and review expose the limit, errors and footer at normal and enlarged scales. */
    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun capacityAndErrorsRemainReachableInWinnersAndReview() {
        val base = LotterySetupPreviewProvider().values.first { it.draft.step == LotteryStep.REVIEW }
        val configuration =
            LotteryConfiguration(
                Instant.parse("2027-05-05T15:00:00Z"),
                Instant.parse("2027-05-06T15:00:00Z"),
                Instant.parse("2027-05-07T15:00:00Z"),
                Instant.parse("2027-05-08T15:00:00Z"),
                2,
                Long.MAX_VALUE,
            )
        val scale = mutableStateOf(1f)
        val step = mutableStateOf(LotteryStep.WINNERS)
        val invalid = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale.value)) {
                Box(Modifier.width(360.dp).height(844.dp).testTag("capacity-preview")) {
                    key(scale.value, step.value, invalid.value) {
                        CampsbridgeTheme(AccountSettings(controlScale = scale.value.toDouble())) {
                            val current =
                                base.copy(
                                    draft =
                                        base.draft.copy(
                                            step = step.value,
                                            waitlistCapacityText =
                                                if (invalid.value) "0" else Long.MAX_VALUE.toString(),
                                        ),
                                    errors =
                                        LotterySetupErrors(
                                            waitlistCapacity =
                                                if (invalid.value) {
                                                    PositiveCountInput.Error.INVALID_WHOLE_NUMBER
                                                } else {
                                                    null
                                                },
                                        ),
                                    configuration = if (invalid.value) null else configuration,
                                )
                            LotterySetupScreen(
                                current,
                                YearMonth.of(2027, 5),
                                {},
                                {},
                                {},
                                {},
                                {},
                                {},
                                {},
                                {},
                                {},
                                {},
                                { _, _ -> },
                                { _, _ -> },
                                {},
                                {},
                            )
                        }
                    }
                }
            }
        }
        val directory =
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "lottery153")
        assertTrue(directory.isDirectory || directory.mkdirs())
        for (font in listOf(1f, 2f)) {
            for (page in listOf(LotteryStep.WINNERS, LotteryStep.REVIEW)) {
                for (error in listOf(false, true)) {
                    compose.runOnIdle {
                        scale.value = font
                        step.value = page
                        invalid.value = error
                    }
                    compose
                        .onNodeWithText(
                            if (page ==
                                LotteryStep.WINNERS
                            ) {
                                "Waitlist capacity (optional)"
                            } else {
                                "Waitlist capacity"
                            },
                        ).performScrollTo()
                        .assertIsDisplayed()
                    compose
                        .onNodeWithText(
                            if (error) {
                                "Enter a positive whole number, or leave blank for an unlimited waitlist."
                            } else {
                                Long.MAX_VALUE
                                    .toString()
                            },
                        ).performScrollTo()
                        .assertIsDisplayed()
                    val footer = compose.onNodeWithText(if (page == LotteryStep.WINNERS) "Next" else "Save draft")
                    footer.assertIsDisplayed()
                    if (error && page == LotteryStep.REVIEW) footer.assertIsNotEnabled()
                    val bitmap = compose.onNodeWithTag("capacity-preview").captureToImage().asAndroidBitmap()
                    File(
                        directory,
                        "${page.name.lowercase()}-${font.toInt()}x-${if (error) "error" else "limited"}.png",
                    ).outputStream()
                        .use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
            }
        }
    }
}
